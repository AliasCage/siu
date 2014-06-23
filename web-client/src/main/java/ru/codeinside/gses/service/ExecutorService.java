/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2013, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.gses.service;

import org.activiti.engine.impl.task.TaskDefinition;
import ru.codeinside.gses.activiti.FileValue;

import javax.annotation.security.RolesAllowed;
import java.io.File;
import java.util.Map;
import java.util.Set;

@RolesAllowed("Executor")
public interface ExecutorService {
  public String getProcedureNameByDefinitionId(String processDefinitionId);

  public Map<String, TaskDefinition> selectTasksByProcedureId(long procedureId);

  int countTasksByProcedureId(long procedureId);

  void saveBuffer(String taskId, String fieldId, String value);

  void saveBuffer(String taskId, String fieldId, Long value);

  Some<String> getTextBuffer(String taskId, String fieldId);

  Some<Long> getLongBuffer(String taskId, String fieldId);

  Some<FileValue> getFileBuffer(String taskId, String fieldId);

  byte[] getBytes(int contentId);

  FileValue saveBytesBuffer(String taskId, String fieldId, String fileName, long fileSize, String mimeType, File tmpFile);

  Set<String> getActiveFields(String taskId);
}
