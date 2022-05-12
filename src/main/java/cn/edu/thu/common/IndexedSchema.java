/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package cn.edu.thu.common;

import java.util.HashMap;
import java.util.Map;

public abstract class IndexedSchema extends Schema {


  public abstract Schema rebuildIndex();
  public abstract int getIndex(String fieldName);

  public static class MapIndexedSchema extends IndexedSchema {
    private Map<String, Integer> fieldPosMap;

    @Override
    public MapIndexedSchema rebuildIndex() {
      fieldPosMap = new HashMap<>(fields.length);
      for (int i = 0; i < fields.length; i++) {
        fieldPosMap.put(fields[i], i);
      }

      return this;
    }

    @Override
    public int getIndex(String fieldName) {
      if (fieldPosMap == null) {
        rebuildIndex();
      }
      return fieldPosMap.getOrDefault(fieldName, -1);
    }
  }
}
