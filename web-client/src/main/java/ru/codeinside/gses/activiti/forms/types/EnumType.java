/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2014, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.gses.activiti.forms.types;

import ru.codeinside.adm.database.FieldBuffer;
import ru.codeinside.gses.activiti.forms.api.definitions.VariableType;

import java.util.Map;

final class EnumType implements VariableType<String> {

  @Override
  public String convertFormValueToModelValue(Object propertyValue, String pattern, Map<String, String> values) {
    if (propertyValue == null) {
      return null;
    }
    String key = propertyValue.toString();
    if (values.containsKey(key)) {
      return key;
    }
    return null;
  }

  @Override
  public void validateParams(String pattern, Map<String, String> values, boolean sandbox) {
    if (pattern != null) {
      throw VariableTypes.badPattern(GsesTypes.ENUM);
    }
    if (values == null || values.isEmpty()) {
      throw new IllegalStateException("Элемент values обязателен для типа " + GsesTypes.ENUM.name);
    }
  }

  @Override
  public Class<String> getJavaType() {
    return String.class;
  }

  @Override
  public String convertBufferToModelValue(FieldBuffer fieldBuffer) {
    return fieldBuffer.getTextValue();
  }

  @Override
  public String getName() {
    return "enum";
  }
}
