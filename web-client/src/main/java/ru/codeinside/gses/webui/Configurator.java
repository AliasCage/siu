/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2013, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.gses.webui;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.bpmn.parser.BpmnParseListener;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.form.FormTypes;
import org.activiti.engine.impl.jobexecutor.JobExecutor;
import org.glassfish.embeddable.Deployer;
import org.glassfish.osgicdi.OSGiService;
import ru.codeinside.gses.activiti.DelegateFormType;
import ru.codeinside.gses.activiti.ReceiptEnsurance;
import ru.codeinside.gses.activiti.forms.CustomFormTypes;
import ru.codeinside.gses.activiti.forms.FieldConstructor;
import ru.codeinside.gses.activiti.ftarchive.AttachmentFFT;
import ru.codeinside.gses.activiti.ftarchive.BooleanFFT;
import ru.codeinside.gses.activiti.ftarchive.DateFFT;
import ru.codeinside.gses.activiti.ftarchive.DirectoryFFT;
import ru.codeinside.gses.activiti.ftarchive.EnclosureItemFFT;
import ru.codeinside.gses.activiti.ftarchive.EnumFFT;
import ru.codeinside.gses.activiti.ftarchive.FormSignatureFFT;
import ru.codeinside.gses.activiti.ftarchive.JsonFFT;
import ru.codeinside.gses.activiti.ftarchive.LongFFT;
import ru.codeinside.gses.activiti.ftarchive.MaskedFFT;
import ru.codeinside.gses.activiti.ftarchive.MultilineFFT;
import ru.codeinside.gses.activiti.ftarchive.SmevRequestEnclosuresFFT;
import ru.codeinside.gses.activiti.ftarchive.SmevRequestFFT;
import ru.codeinside.gses.activiti.ftarchive.SmevResponseEnclosuresFFT;
import ru.codeinside.gses.activiti.ftarchive.StringFFT;
import ru.codeinside.gses.activiti.jta.JtaProcessEngineConfiguration;
import ru.codeinside.gses.activiti.listeners.GsesBpmnParseListener;
import ru.codeinside.gses.activiti.listeners.MailBpmnParseListener;
import ru.codeinside.gses.activiti.mail.SmtpConfig;
import ru.codeinside.gses.activiti.mail.SmtpConfigReader;
import ru.codeinside.gses.webui.utils.RunProfile;
import ru.codeinside.gws.api.CryptoProvider;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import java.util.Map;
import java.util.logging.Logger;

import static org.activiti.engine.ProcessEngineConfiguration.HISTORY_FULL;

@Singleton
@DependsOn("BaseBean")
public class Configurator {

  final static Logger logger = Logger.getLogger(Configurator.class.getName());
  private static ProcessEngine processEngine;

  @Resource(mappedName = "jdbc/adminka")
  private DataSource dataSource;

  @Resource
  TransactionManager transactionManager;

  @PersistenceUnit(unitName = "myPU")
  EntityManagerFactory emf;

  @PersistenceContext(unitName = "logPU")
  EntityManager emLog;


  @Inject
  BeanManager beanManager;

  @Inject
  @OSGiService(dynamic = true)
  CryptoProvider cryptoProvider;

  @Inject
  ActivitiJobProvider activitiJobProvider;

  @Inject
  Instance<ReceiptEnsurance> receiptEnsuranceHolder;

  @Resource
  Deployer deployer;

  @Named("doDbUpdate")
  @Inject
  Instance<Boolean> doDbUpdate;


  private static Deployer embeddableDeployer;

  private void initTypes(ProcessEngineConfigurationImpl engineConfiguration) {
    ImmutableMap<String, FieldConstructor> internalFormTypes = ImmutableMap.<String, FieldConstructor>builder()
      .put("string", new StringFFT())
      .put("multiline", new MultilineFFT())
      .put("long", new LongFFT())
      .put("date", new DateFFT())
      .put("masked", new MaskedFFT())
      .put("boolean", new BooleanFFT())
      .put("enum", new EnumFFT())
      .put("directory", new DirectoryFFT())
      .put("attachment", new AttachmentFFT())
      .put("signature", new FormSignatureFFT())
      .put("smevRequest", new SmevRequestFFT())
      .put("enclosure", new EnclosureItemFFT())
      .put("smevRequestEnclosure", new SmevRequestEnclosuresFFT())
      .put("smevResponseEnclosure", new SmevResponseEnclosuresFFT())
      .put("json", new JsonFFT())
      .build();

    FormTypes formTypes = new CustomFormTypes();
    for (Map.Entry<String, FieldConstructor> e : internalFormTypes.entrySet()) {
      formTypes.addFormType(new DelegateFormType(e.getKey(), e.getValue()));
    }
    engineConfiguration.setFormTypes(formTypes);
  }

  @Produces
  public ProcessEngine getProcessEngine() {
    synchronized (Configurator.class) {
      if (processEngine == null) {
        final JtaProcessEngineConfiguration cfg =
          new JtaProcessEngineConfiguration(transactionManager, cryptoProvider, beanManager);

        // асинхронное исполнение
        final JobExecutor jobExecutor = activitiJobProvider.createJobExecutor();
        if (jobExecutor != null) {
          cfg.setJobExecutor(jobExecutor);
          cfg.setJobExecutorActivate(true);
        }

        // Включить историю изменения переменных
        cfg.setHistory(HISTORY_FULL);
        Boolean update = doDbUpdate.isUnsatisfied() ? (!RunProfile.isProduction()) : doDbUpdate.get();
        cfg.setDatabaseSchemaUpdate(Boolean.TRUE == update ? "true" : "false");
        cfg.setDataSource(dataSource);
        cfg.setJpaEntityManagerFactory(emf);
        cfg.setJpaHandleTransaction(false);
        cfg.setJpaCloseEntityManager(true);
        //Подменить реализацию TaskQuery c TaskQueryImpl на TaskQueryImpl2
        cfg.setTaskService(new TaskServiceImpl2());
        cfg.setCustomPostBPMNParseListeners(
          Lists.<BpmnParseListener>newArrayList(
            new GsesBpmnParseListener(receiptEnsuranceHolder.get()),
            new MailBpmnParseListener()
          )
        );
        cfg.setProcessEngineName("СИУ");
        initTypes(cfg);
        // форсируем тут создание EM
        emf.createEntityManager().close();
        // читаем настройки подключения к smtp серверу
        fillSmtpConfig(cfg);
        processEngine = cfg.buildProcessEngine();
      }
      return processEngine;
    }
  }

  /**
   * Заполняет свойства конфига необходимые для подключения к SMTP и отправке сообщения из Activiti.
   * Свойства выставляются только в том случае если значение, прочтеное из настроек не null, в противном случае
   * будет сохранено значение по умолчанию.
   * Значения по умолчанию можно посмотреть здесь http://activiti.org/userguide/index.html#bpmnEmailTaskServerConfiguration
   *
   * @param cfg activiti engine config
   */
  private void fillSmtpConfig(ProcessEngineConfiguration cfg) {
    SmtpConfig smtpConfig = SmtpConfigReader.readSmtpConnectionParams();
    if (smtpConfig.getPort() != null) {
      cfg.setMailServerPort(smtpConfig.getPort());
    }
    if (smtpConfig.getHost() != null) {
      cfg.setMailServerHost(smtpConfig.getHost());
    }
    if (smtpConfig.getUserName() != null) {
      cfg.setMailServerUsername(smtpConfig.getUserName());
    }
    if (smtpConfig.getPassword() != null) {
      cfg.setMailServerPassword(smtpConfig.getPassword());
    }
    if (smtpConfig.getUseTLS() != null) {
      cfg.setMailServerUseTLS(smtpConfig.getUseTLS());
    }
    if (smtpConfig.getDefaultFrom() != null) {
      cfg.setMailServerDefaultFrom(smtpConfig.getDefaultFrom());
    }
  }

  @PostConstruct
  public void postConstruct() {
    logger.info("Запуск исполнителя процессов");
    getProcessEngine();
    if (deployer != null) {
      embeddableDeployer = deployer;
    }
  }


  @PreDestroy
  public void close() {
    logger.info("Выключение исполнителя процессов");
    closeEngine();
  }

  private void closeEngine() {
    if (processEngine != null) {
      processEngine.close();
      processEngine = null;
    }
  }

  public static ProcessEngine get() {
    return processEngine;
  }

  public static Deployer getDeployer() {
    return embeddableDeployer;
  }

}
