/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Copyright (c) 2013, MPL CodeInside http://codeinside.ru
 */

package ru.codeinside.gses.webui.executor;

import ru.codeinside.gses.lazyquerycontainer.Query;
import ru.codeinside.gses.lazyquerycontainer.QueryDefinition;
import ru.codeinside.gses.lazyquerycontainer.QueryFactory;

import java.io.Serializable;

final public class ProcessTaskQueryFactory implements QueryFactory, Serializable {
  private ProcessTaskQueryDefinition def;
  @Override
  public void setQueryDefinition(QueryDefinition queryDefinition) {
    def = (ProcessTaskQueryDefinition) queryDefinition;
  }

  @Override
  public Query constructQuery(Object[] sortPropertyIds, boolean[] sortStates) {
    return new ProcessTaskQuery(def.procedureId);
  }
}
