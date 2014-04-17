/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2014, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.adm;


import commons.Streams;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import ru.codeinside.adm.database.ExternalGlue;
import ru.codeinside.adm.database.HttpLog;
import ru.codeinside.adm.database.SmevLog;
import ru.codeinside.adm.database.SoapPacket;
import ru.codeinside.gses.API;
import ru.codeinside.gses.webui.osgi.LogCustomizer;
import ru.codeinside.gws.log.format.Metadata;
import ru.codeinside.gws.log.format.Pack;

import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionManagement;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static javax.ejb.TransactionAttributeType.REQUIRES_NEW;

@TransactionManagement
@TransactionAttribute
@Singleton
@Stateless
@DependsOn("BaseBean")
public class LogConverter {

  @PersistenceContext(unitName = "myPU")
  EntityManager em;

  final Logger logger = Logger.getLogger(getClass().getName());
  String dirPath;
  Storage storage;
  boolean legacyMode = true;


  // test support
  public interface Storage {
    String getLazyDirPath();

    SmevLog findLogEntry(String marker);

    void store(SmevLog smevLog);
  }


  public void setStorage(Storage storage) {
    this.storage = storage;
  }

  @TransactionAttribute(REQUIRES_NEW)
  public boolean logToBd() {
    if (storage == null) {
      storage = new Storage() {
        @Override
        public String getLazyDirPath() {
          return LogCustomizer.getStoragePath();
        }

        @Override
        public SmevLog findLogEntry(String marker) {
          List<SmevLog> rs = em.
            createQuery("select l from SmevLog l where l.marker = :marker", SmevLog.class)
            .setMaxResults(1)
            .setParameter("marker", marker)
            .getResultList();
          return rs.isEmpty() ? null : rs.get(0);
        }

        @Override
        public void store(SmevLog smevLog) {
          em.persist(smevLog);
        }
      };
    }

    String pathInfo = getDirPath();
    if (isEmpty(pathInfo)) {
      return false;
    }
    if (legacyMode) {
      return legacyMode(pathInfo);
    }
    Set<File> logPackagesSet = new HashSet<File>();
    listLowDirs(new File(pathInfo), logPackagesSet);
    if (logPackagesSet.isEmpty()) {
      return false;
    }
    try {
      List<File> logPackages = new ArrayList<File>(logPackagesSet);
      Collections.sort(logPackages, new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
          long delta = f1.lastModified() - f2.lastModified();
          if (delta > 0L) {
            return 1;
          }
          if (delta < 0L) {
            return -1;
          }
          return 0;
        }
      });
      final File logPackage = logPackages.get(0);
      SmevLog log = getOepLog(logPackage.getName());
      File[] logFiles = logPackage.listFiles();
      if (logFiles != null) {
        for (File logFile : logFiles) {
          String name = logFile.getName();

          if (Metadata.HTTP_RECEIVE.equals(name)) {
            log.setReceiveHttp(new HttpLog(readFileAsString(logFile)));

          } else if (Metadata.HTTP_SEND.equals(name)) {
            log.setSendHttp(new HttpLog(readFileAsString(logFile)));

          } else if (Metadata.METADATA.equals(name)) {
            ObjectMapper objectMapper = new ObjectMapper();
            Metadata metadata = objectMapper.readValue(logFile, Metadata.class);
            log.setClient(metadata.client);
            log.setError(metadata.error);
            log.setLogDate(metadata.date);
            log.setComponent(limitLength(metadata.componentName, 255));
            if (metadata.bid != null) {
              log.setBidId(metadata.bid);
            }
            if (metadata.send != null) {
              log.setSendPacket(getSoapPacket(metadata.send));
            }
            if (metadata.receive != null) {
              log.setReceivePacket(getSoapPacket(metadata.receive));
            }
            // в сущности нет processInstanceId
          }
          deleteFile(logFile);
        }
        if (log.getBidId() == null && !log.isClient()) {
          ExternalGlue glue = getExternalGlue(log);
          if (glue != null) {
            log.setBidId(Long.parseLong(glue.getBidId()));
          }
        }
        if (isEmpty(log.getInfoSystem())) {
          String infoSystem = null;
          if (log.isClient()) {
            SoapPacket sendPacket = log.getSendPacket();
            if (sendPacket != null) {
              infoSystem = sendPacket.getRecipient();
            }
          } else {
            SoapPacket receivePacket = log.getReceivePacket();
            if (receivePacket != null) {
              infoSystem = receivePacket.getSender();
            }
          }
          log.setInfoSystem(infoSystem);
        }
        storage.store(log);
      }
      deleteFile(logPackage);
    } catch (Exception e) {
      logger.log(Level.WARNING, "failure", e);
      return false;
    }
    return true;
  }

  //TODO синхронизировать запуск по расписанию и из UI
  @TransactionAttribute(REQUIRES_NEW)
  public boolean logToZip() {
    Date edgeDate = calcEdge();

    Number count = em.createQuery("select count(o) from SmevLog o where o.logDate is null or o.logDate < :date", Number.class)
      .setParameter("date", edgeDate)
      .getSingleResult();

    if (count == null || count.intValue() == 0) {
      return false;
    }

    ZipOutputStream zip = null;
    try {
      zip = new ZipOutputStream(new FileOutputStream(createZipFileName(edgeDate)));
      for (int i = 0; i < 100; i++) {
        List<SmevLog> logs = em.createQuery("select o from SmevLog o where o.logDate is null or o.logDate < :date", SmevLog.class)
          .setParameter("date", edgeDate)
          .setMaxResults(1)
          .getResultList();
        if (logs.isEmpty()) {
          return false;
        }
        for (SmevLog log : logs) {
          final long packageTime = log.getLogDate() == null ? log.getDate().getTime() : log.getLogDate().getTime();
          final String packagePath;
          {
            final String logId;
            if (log.getMarker() == null || log.getMarker().length() < 2) {
              logId = String.valueOf(Math.round(Math.random() * 100000.0));
            } else {
              logId = log.getMarker();
            }
            int mlen = logId.length();
            String d1 = logId.substring(mlen - 2, mlen - 1);
            String d2 = logId.substring(mlen - 1);
            packagePath = d1 + "/" + d2 + "/" + logId + "/";
            ZipEntry logPackage = new ZipEntry(packagePath);
            logPackage.setTime(packageTime);
            zip.putNextEntry(logPackage);
          }

          if (log.getSendHttp() != null && log.getSendHttp().getData() != null) {
            ZipEntry httpSend = new ZipEntry(packagePath + Metadata.HTTP_SEND);
            httpSend.setTime(packageTime);
            zip.putNextEntry(httpSend);
            zip.write(log.getSendHttp().getData());
            zip.closeEntry();
          }

          if (log.getReceiveHttp() != null && log.getReceiveHttp().getData() != null) {
            ZipEntry httpReceive = new ZipEntry(packagePath + Metadata.HTTP_RECEIVE);
            httpReceive.setTime(packageTime);
            zip.putNextEntry(httpReceive);
            zip.write(log.getReceiveHttp().getData());
            zip.closeEntry();
          }

          ObjectMapper objectMapper = new ObjectMapper();
          Metadata metadata = new Metadata();
          metadata.client = log.isClient();
          metadata.error = log.getError();
          metadata.date = log.getLogDate();
          metadata.componentName = log.getComponent();
          Long bidId = log.getBidId();
          if (bidId != null) {
            metadata.bid = bidId;
            List<String> ids = em.createQuery("select b.processInstanceId from Bid b where b.id = :id", String.class)
              .setParameter("id", bidId).getResultList();
            if (!ids.isEmpty()) {
              metadata.processInstanceId = ids.get(0);
            }
          }
          metadata.send = getPack(log.getSendPacket());
          metadata.receive = getPack(log.getReceivePacket());
          ZipEntry logMetadata = new ZipEntry(packagePath + Metadata.METADATA);
          logMetadata.setTime(packageTime);
          zip.putNextEntry(logMetadata);
          zip.write(objectMapper.writeValueAsBytes(metadata));
          zip.closeEntry();

          em.remove(log);
          em.flush();
        }
      }
      zip.flush();
    } catch (IOException e) {
      logger.log(Level.WARNING, "io error", e);
    } finally {
      Streams.close(zip);
    }
    return true;
  }

  // ---- internals ----

  private SmevLog getOepLog(String marker) {
    SmevLog entry = storage.findLogEntry(marker);
    if (entry == null) {
      entry = new SmevLog();
      entry.setDate(new Date());
      entry.setMarker(marker);
    }
    return entry;
  }

  private String getDirPath() {
    if (dirPath == null) {
      dirPath = storage.getLazyDirPath();
    }
    return dirPath;
  }

  private ExternalGlue getExternalGlue(SmevLog log) {
    ExternalGlue externalGlue = getExternalGlue(log.getReceivePacket());
    if (externalGlue != null) {
      return externalGlue;
    }
    return getExternalGlue(log.getSendPacket());
  }

  private ExternalGlue getExternalGlue(SoapPacket soapPacket) {
    if (em == null || soapPacket == null) {
      return null;
    }
    String[] idRefs = {soapPacket.getOriginRequestIdRef(), soapPacket.getRequestIdRef()};
    for (String idRef : idRefs) {
      if (!isEmpty(idRef)) {
        List resultList = em.createQuery("select g from ExternalGlue g where g.requestIdRef = :requestIdRef")
          .setParameter("requestIdRef", idRef).getResultList();
        if (!resultList.isEmpty()) {
          return (ExternalGlue) resultList.get(0);
        }
      }
    }
    return null;
  }

  private SoapPacket getSoapPacket(Pack packet) {
    SoapPacket result = new SoapPacket();
    result.setSender(packet.sender);
    result.setRecipient(packet.recipient);
    result.setOriginator(packet.originator);
    result.setService(packet.serviceName);
    result.setTypeCode(packet.typeCode);
    result.setStatus(packet.status);
    result.setDate(packet.date);
    result.setRequestIdRef(packet.requestIdRef);
    result.setOriginRequestIdRef(packet.originRequestIdRef);
    result.setServiceCode(packet.serviceCode);
    result.setCaseNumber(packet.caseNumber);
    result.setExchangeType(packet.exchangeType);
    return result;
  }

  private Pack getPack(SoapPacket packet) {
    if (packet == null) {
      return null;
    }
    Pack result = new Pack();
    result.sender = packet.getSender();
    result.recipient = packet.getRecipient();
    result.originator = packet.getOriginator();
    result.serviceName = packet.getService();
    result.typeCode = packet.getTypeCode();
    result.status = packet.getStatus();
    result.date = packet.getDate();
    result.requestIdRef = packet.getRequestIdRef();
    result.originRequestIdRef = packet.getOriginRequestIdRef();
    result.serviceCode = packet.getServiceCode();
    result.caseNumber = packet.getCaseNumber();
    result.exchangeType = packet.getExchangeType();
    return result;
  }

  private String getZipPath() {
    String instanceRoot = System.getProperty("com.sun.aas.instanceRoot");
    File file;
    if (instanceRoot != null) {
      file = new File(new File(new File(instanceRoot), "logs"), "zip");
    } else {
      file = new File("/var/", "zip");
    }
    if (!file.exists()) {
      file.mkdirs();
    }
    return file.getAbsolutePath();
  }

  private boolean isEmpty(String str) {
    return str == null || str.length() == 0;
  }

  private void listLowDirs(File root, Set<File> lowDirs) {
    if (lowDirs.isEmpty()) {
      File[] files = root.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            listLowDirs(file, lowDirs);
          } else {
            lowDirs.add(root);
            break;
          }
        }
      }
    }
  }

  private void setInfoSystem(SmevLog log, String sender) {
    if (isEmpty(log.getInfoSystem()) && !isEmpty(sender)) {
      log.setInfoSystem(sender);
    }
  }

  private void deleteFile(File file) {
    if (!file.delete()) {
      logger.info("can't delete " + file);
    }
  }


  private String limitLength(String src, int maxLen) {
    if (src == null || src.length() <= maxLen) {
      return src;
    }
    return src.substring(0, maxLen);
  }


  // ---- legacy ----
  private SoapPacket getPacket(File logFile) {
    String splitter = "!!;";

    String packetString = readFileAsString(logFile);
    String[] values = packetString.split(splitter);

    int index = 0;
    SoapPacket result = new SoapPacket();
    result.setSender(getValue(values, index++));
    result.setRecipient(getValue(values, index++));
    result.setOriginator(getValue(values, index++));
    result.setService(getValue(values, index++));
    result.setTypeCode(getValue(values, index++));
    result.setStatus(getValue(values, index++));
    result.setDate(getAsDate(getValue(values, index++), logFile));
    result.setRequestIdRef(getValue(values, index++));
    result.setOriginRequestIdRef(getValue(values, index++));
    result.setServiceCode(getValue(values, index++));
    result.setCaseNumber(getValue(values, index++));
    result.setExchangeType(getValue(values, index));

    return result;
  }

  private String getValue(String[] values, int index) {
    if (values.length <= index) {
      return "";
    }
    return values[index];
  }

  private Date getAsDate(String value, File file) {
    value = StringUtils.trimToNull(value);
    if (value != null) {
      try {
        SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
        return format.parse(value);
      } catch (ParseException e) {
        logger.log(Level.INFO, "parse", e);
        return new Date(file.lastModified());
      }
    }
    return null;
  }

  private String readFileAsString(File file) {
    BufferedReader reader = null;
    try {
      StringBuilder fileData = new StringBuilder();
      reader = new BufferedReader(new FileReader(file));
      char[] buf = new char[1024];
      int numRead;
      while ((numRead = reader.read(buf)) != -1) {
        String readData = String.valueOf(buf, 0, numRead);
        fileData.append(readData);
      }
      return fileData.toString();
    } catch (IOException e) {
      logger.log(Level.WARNING, "io error", e);
      return null;
    } finally {
      Streams.close(reader);
    }
  }

  boolean legacyMode(String pathInfo) {
    final File[] files = new File(pathInfo).listFiles();
    if (files == null || files.length == 0) {
      legacyMode = false;
      return false;
    }
    try {
      for (final File logPackage : files) {
        String packageName = logPackage.getName();
        if (packageName.length() < 4) {
          continue;
        }
        final File[] logFiles = logPackage.listFiles();
        if (logFiles == null || logFiles.length == 0) {
          continue;
        }
        SmevLog log = getOepLog(packageName);
        long lastModified = 0;
        for (File logFile : logFiles) {
          String name = logFile.getName();
          if (name.startsWith("log-http")) {
            int lastIndex = name.lastIndexOf("-");
            int index = name.indexOf("-", "log-http".length());
            log.setClient(Boolean.valueOf(name.substring(lastIndex + 1)));
            String httpString = readFileAsString(logFile);
            if (Boolean.valueOf(name.substring(index + 1, lastIndex))) {
              log.setSendHttp(new HttpLog(httpString));
            } else {
              log.setReceiveHttp(new HttpLog(httpString));
            }
          }
          if (name.startsWith("log-Error")) {
            log.setError(readFileAsString(logFile));
          }
          if (name.startsWith("log-Date")) {
            log.setLogDate(getAsDate(readFileAsString(logFile), logFile));
          }
          if (name.startsWith("log-ProcessInstanceId")) {
            String processInstanceId = readFileAsString(logFile);
            List<Long> l = em.createQuery(
              "select b.id from Bid b where b.processInstanceId=:processInstanceId", Long.class)
              .setParameter("processInstanceId", processInstanceId)
              .getResultList();
            if (!l.isEmpty()) {
              log.setBidId(l.get(0));
            }
          }
          if (name.startsWith("log-ClientRequest")) {
            lastModified = logFile.lastModified();
            log.setSendPacket(getPacket(logFile));
            setInfoSystem(log, log.getSendPacket().getSender());
          }
          if (name.startsWith("log-ClientResponse")) {
            lastModified = logFile.lastModified();
            log.setReceivePacket(getPacket(logFile));
            setInfoSystem(log, log.getReceivePacket().getSender());
          }
          if (name.startsWith("log-ServerResponse")) {
            lastModified = logFile.lastModified();
            log.setSendPacket(getPacket(logFile));
            setInfoSystem(log, log.getSendPacket().getRecipient());
          }
          if (name.startsWith("log-ServerRequest")) {
            lastModified = logFile.lastModified();
            log.setReceivePacket(getPacket(logFile));
            setInfoSystem(log, log.getReceivePacket().getRecipient());
          }
          if (log.getBidId() == null) {
            ExternalGlue glue = getExternalGlue(log);
            if (glue != null) {
              log.setBidId(Long.parseLong(glue.getBidId()));
            }
          }
          deleteFile(logFile);
        }
        if (log.getLogDate() == null) {
          if (lastModified == 0) {
            logger.info("missed logDate for " + packageName);
          }
          log.setLogDate(new Date(lastModified));
        }
        storage.store(log);
        deleteFile(logPackage);
        return true;
      }
      legacyMode = false;
    } catch (Exception e) {
      logger.log(Level.WARNING, "failure", e);
    }
    return false;
  }


  private Date calcEdge() {
    int depth = -1;
    String depthConfig = AdminServiceProvider.get().getSystemProperty(API.LOG_DEPTH);
    if (depthConfig != null) {
      try {
        depth = Integer.parseInt(depthConfig);
      } catch (NumberFormatException e) {
      }
    }
    if (depth <= 0) {
      depth = API.DEFAULT_LOG_DEPTH;
    }
    Calendar cal = Calendar.getInstance(getTimeZone());
    cal.add(Calendar.DATE, -depth);
    return cal.getTime();
  }

  private TimeZone getTimeZone() {
    return TimeZone.getTimeZone("Europe/Moscow");
  }

  private File createZipFileName(Date edge) {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMdd-HHmmssSSS");
    dateTimeFormat.setTimeZone(getTimeZone());
    return new File(getZipPath(), dateTimeFormat.format(edge) + ".zip");
  }

}
