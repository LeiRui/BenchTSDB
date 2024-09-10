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

package cn.edu.thu.reader;

import cn.edu.thu.common.Config;
import cn.edu.thu.common.IndexedSchema;
import cn.edu.thu.common.IndexedSchema.MapIndexedSchema;
import cn.edu.thu.common.Record;
import cn.edu.thu.common.Schema;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSVReader extends BasicReader {

  private static final Logger logger = LoggerFactory.getLogger(CSVReader.class);

  private final int defaultPrecision = 8;
  private IndexedSchema overallSchema;
  private Schema currentFileSchema;
  private boolean useDateFormat = true;
  private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
  private Map<String, Class<?>> typeMap;

  public CSVReader(Config config, List<String> files) throws IOException {
    super(config, files);
    if (!config.TYPE_INFO_EXIST) {
      if (!config.splitFileByDevice) {
        logger.info("Collecting the overall schema");
        overallSchema = collectSchemaFromFiles(files);
        logger.info("The overall schema is collected");
        logger.debug("The overall schema is: {}", overallSchema);
      }
    }
  }

  private IndexedSchema collectSchemaFromFiles(List<String> files) {
    SchemaSet schemaSet = new SchemaSet();
    logger.info("Collecting schema from {} files", files.size());
    for (int i = 0; i < files.size(); i++) {
      String file = files.get(i);
      logger.info("Collecting schema from {} ({}/{})", file, (i + 1), files.size());
      schemaSet.union(collectSchemaFromFile(file));
    }
    return schemaSet.toSchema();
  }

  private Schema collectSchemaFromFile(String file) {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String headerLine = reader.readLine();
      if (headerLine != null) {
        return convertHeaderToSchema(headerLine, reader, file, false);
      }
    } catch (IOException e) {
      logger.warn("Cannot read schema from file {}, file skipped", file);
    }

    return null;
  }

  private void inferTypeWithData(int fieldNum, Schema schema, BufferedReader reader,
      boolean fillCache) throws IOException {
    for (int i = 0; i < fieldNum; i++) {
      schema.getTypes()[i] = null;
    }

    String line;
    int numRead = 0;
    while (++numRead <= config.INFER_TYPE_MAX_RECORD_NUM
        && (line = reader.readLine())
        != null) { // note the sequence, make this command the last to avoid miss the line
      String[] lineSplit = line.split(config.CSV_SEPARATOR);

      for (int i = 0; i < fieldNum; i++) {
        if (i + 1 >= lineSplit.length) {
          continue;
        }
//        String field = removeOuterQuote(lineSplit[unknownTypeIndex + 1]);
        //TODO: do not remove quote, otherwise string may be wrongly inferred as long
        String field = lineSplit[i + 1];

        Class<?> aClass = inferType(field);

        schema.getTypes()[i] = SchemaSet.mergeType(schema.getTypes()[i], aClass);
        // this is to mitigate the tianyuan dataset problem:
        // some fields at first have int values while have string values afterwards...
      }
      if (fillCache) {
        cachedLines.add(line);
      }
    }

    for (int i = 0; i < fieldNum; i++) {
      if (schema.getTypes()[i] == null) {
        schema.getTypes()[i] = String.class;
      }
    }

  }

  private void inferTypeWithData_deprecated(int fieldNum, Schema schema, BufferedReader reader,
      boolean fillCache) throws IOException {
    Set<Integer> unknownTypeIndices = new HashSet<>();
    for (int i = 0; i < fieldNum; i++) {
      unknownTypeIndices.add(i);
    }

    String line;
    List<Integer> indexToRemove = new ArrayList<>();
    int numRead = 0;
    while (++numRead <= config.INFER_TYPE_MAX_RECORD_NUM
        && !unknownTypeIndices.isEmpty()
        && (line = reader.readLine())
        != null) { // note the sequence, make this command the last to avoid miss the line
      String[] lineSplit = line.split(config.CSV_SEPARATOR);
      indexToRemove.clear();

      for (Integer unknownTypeIndex : unknownTypeIndices) {
        if (unknownTypeIndex + 1 >= lineSplit.length) {
          continue;
        }

//        String field = removeOuterQuote(lineSplit[unknownTypeIndex + 1]);
        //TODO: do not remove quote, otherwise string may be wrongly inferred as long
        String field = lineSplit[unknownTypeIndex + 1];

        Class<?> aClass = inferType(field);
        if (aClass != null) {
          schema.getTypes()[unknownTypeIndex] = aClass;
          indexToRemove.add(unknownTypeIndex);
        }
      }

      unknownTypeIndices.removeAll(indexToRemove);
      if (fillCache) {
        cachedLines.add(line);
      }
    }

    // if some fields cannot be inferred within a batch, assume them as text
    for (Integer unknownTypeIndex : unknownTypeIndices) {
      schema.getTypes()[unknownTypeIndex] = String.class;
    }
  }

  private Schema convertHeaderToSchemaWithTypeInfo(String headerLine, String fileName)
      throws IOException {
    String[] split = headerLine.split(config.CSV_SEPARATOR);
    Schema schema = new Schema();

    // the first field is fixed to time
    int fieldNum = split.length - 1;
    schema.setFields(new String[fieldNum]);
    schema.setPrecision(new int[fieldNum]);

    int devicePos = split[1].lastIndexOf('.');
    String tag;
    if (devicePos == -1) {
      tag = fileNameToTag(new File(fileName).getName());
    } else {
      tag = split[1].substring(0, devicePos);
    }

    schema.setTag(formatString(tag));

    for (int i = 1; i < split.length; i++) {
      String columnName = split[i];
      String measurement = devicePos != -1 ? columnName.substring(devicePos + 1) : columnName;
      schema.getFields()[i - 1] = formatString(measurement);
      schema.getPrecision()[i - 1] = defaultPrecision;
      // get type info
      if (typeMap == null) {
        extractTypeMap();
      }
      String seriesName = devicePos != -1 ? columnName : tag + "." + columnName;
      if (!typeMap.containsKey(seriesName)) {
        throw new IOException(
            "something went wrong: TYPE_INFO_EXIST=true while typeMap does not contain series "
                + seriesName);
      }
      schema.getTypes()[i - 1] = typeMap.get(seriesName);
    }
    return schema;
  }

  private void extractTypeMap() throws IOException {
    typeMap = new HashMap<>();
    try (
        BufferedReader reader = new BufferedReader(new FileReader(config.TYPE_INFO_FILE))) {
      if (config.TYPE_INFO_HEADER) {
        reader.readLine();
      }
      String line;
      while ((line = reader.readLine()) != null) {
        String[] items = line.split(config.CSV_SEPARATOR);
        String seriesName = items[config.TYPE_INFO_SERIES_COL];
        String seriesType = items[config.TYPE_INFO_TYPE_COL];
        Class<?> type = String.class;
        switch (seriesType) {
          case "FLOAT":
          case "DOUBLE":
            type = Double.class;
            break;
          case "BOOLEAN":
          case "TEXT":
            type = String.class;
            break;
          case "INT32":
          case "INT64":
            type = Long.class;
            break;
          default:
            break;
        }
        typeMap.put(seriesName, type);
      }
    }
  }

  public String formatString(String input) {
//    - Tsbs: root.diskio.host_9.writes(INT64)
//    - Zhongche: root.group_69.1701.ZT12029
//    - Tianyuan: root.cty.trans.07.1001202307.1001202307.TY_0001_00_6
//    - Zhongyan: root.T000100010002.90401.struct@waste%105006, root.T000100010002.90003.TotalWastePct-123

    // 1. 用正则表达式替换特殊字符 ( ) % @ - 为字母a
    String modifiedString = input.replaceAll("[()@%\\-]", "a");

    // 2. 用点分割字符串
    String[] parts = modifiedString.split("\\.");

    // 3. 创建StringBuilder存储最终结果
    StringBuilder result = new StringBuilder();

    // 4. 处理每个部分，root保持不变，其余部分添加前缀a
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].equals("root")) {
        // root保持不变
        result.append(parts[i]);
      } else if (i > 0) { // 排除第一个
        // 其他部分前面加字母a
        result.append(".a").append(parts[i]);
      } else { // 第一个且不是root
        result.append("a").append(parts[i]);
      }
    }

    return result.toString();
  }

  private Schema convertHeaderToSchema(String headerLine, BufferedReader reader, String fileName,
      boolean fillCache)
      throws IOException {
    String[] split = headerLine.split(config.CSV_SEPARATOR);
    Schema schema = new Schema();

    // the first field is fixed to time
    int fieldNum = split.length - 1;
    schema.setFields(new String[fieldNum]);
    schema.setPrecision(new int[fieldNum]);

    int devicePos = split[1].lastIndexOf('.');
    String tag;
    if (devicePos == -1) {
      tag = fileNameToTag(new File(fileName).getName());
    } else {
      tag = split[1].substring(0, devicePos);
    }

    schema.setTag(formatString(tag));

    for (int i = 1; i < split.length; i++) {
      String columnName = split[i];
      String measurement = devicePos != -1 ? columnName.substring(devicePos + 1) : columnName;
      schema.getFields()[i - 1] = formatString(measurement);
      schema.getPrecision()[i - 1] = defaultPrecision;
    }

    // infer datatype using at most a batch of lines
    if (overallSchema == null) {
      inferTypeWithData(fieldNum, schema, reader, fillCache);
    } else {
      inferTypeWithOverallSchema(schema);
    }

    return schema;
  }

  private String fileNameToTag(String fileName) {
    int suffixPos = fileName.lastIndexOf('.');
    return fileName.substring(0, suffixPos);
  }

  private void inferTypeWithOverallSchema(Schema schema) {
    for (int i = 0; i < schema.getFields().length; i++) {
      String field = schema.getFields()[i];
      schema.getTypes()[i] = overallSchema.getTypes()[overallSchema.getIndex(field)];
    }
  }

  private Class<?> inferType(String field) {
    if (field.equalsIgnoreCase("null") || field.isEmpty()) {
      return null;
    }

    try {
      Long.parseLong(field);
      return Long.class;
    } catch (NumberFormatException ignore) {
      // ignored
    }

    try {
      Double.parseDouble(field);
      return Double.class;
    } catch (NumberFormatException ignore) {
      // ignored
    }

    return String.class;
  }

  @Override
  public List<Record> convertCachedLinesToRecords() {
    List<Record> records = new ArrayList<>();
    Iterator<String> it = cachedLines.iterator();
    int n = 0;
    while (n < config.BATCH_SIZE && it.hasNext()) {
      String cachedLine = it.next();
      it.remove();
      records.add(convertToRecord(cachedLine));
      n++;
    }
//    for (String cachedLine : cachedLines) {
//      records.add(convertToRecord(cachedLine));
//    }
    return records;
  }

  private String removeOuterQuote(String s) {
    if (s.length() >= 2 &&
        s.startsWith("'") && s.endsWith("'") ||
        s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private Object parseField(String field, Schema schema, int index) {
    if (field.isEmpty() || field.equalsIgnoreCase("null")) {
      return null;
    }

    Class<?> type = schema.getTypes()[index];
    try {
      if (type == Long.class) {
        return Long.parseLong(field);
      }
      if (type == Double.class) {
        return Double.parseDouble(field);
      }
      return field;
    } catch (NumberFormatException ignore) {
      logger.info("field {}: parseField type conflict: return null object.", field);
      return null;
    }
  }

  private List<Object> fieldsWithCurrentFileSchema(String[] split) {
    List<Object> fields = new ArrayList<>(currentFileSchema.getFields().length);
    for (int i = 1; i < split.length; i++) {
      split[i] = removeOuterQuote(split[i]);
      // IMPORTANT NOTE: here need to remove quote, otherwise for example 'null' can not be identified as null during writes.
      // This is different from the case when doing type inference, where we do not remove quotes!

      fields.add(parseField(split[i], currentFileSchema, i - 1));
    }
    for (int i = split.length; i <= currentFileSchema.getFields().length; i++) {
      fields.add(null); // this is for tianyuan dataset, where data may be less than schema fields
    }

    return fields;
  }

  private List<Object> fieldsWithOverallSchema(String[] split) {
    List<Object> fields = new ArrayList<>(overallSchema.getFields().length);
    for (int i = 0; i < overallSchema.getFields().length; i++) {
      fields.add(null);
    }

    for (int i = 1; i < split.length; i++) {
      int overallIndex = overallSchema.getIndex(currentFileSchema.getFields()[i - 1]);
      split[i] = removeOuterQuote(split[i]);

      fields.set(overallIndex, parseField(split[i], overallSchema, overallIndex));
    }
    return fields;
  }

  private long parseTime(String timeStr) {
    if (useDateFormat) {
      try {
        return dateFormat.parse(timeStr).getTime();
      } catch (ParseException e) {
        useDateFormat = false;
        return Long.parseLong(timeStr);
      }
    } else {
      return Long.parseLong(timeStr);
    }
  }

  private Record convertToRecord(String line) {
    Record record;
    String[] split = line.split(config.CSV_SEPARATOR);
    long time = parseTime(split[0]);
    String tag = currentFileSchema.getTag();
    List<Object> fields;

    if (config.TYPE_INFO_EXIST || config.splitFileByDevice) {
      fields = fieldsWithCurrentFileSchema(split);
    } else {
      fields = fieldsWithOverallSchema(split);
    }
    record = new Record(time, tag, fields);

    return record;
  }

  @Override
  public void onFileOpened() {
    Schema fileSchema = null;
    try {
      if (!config.TYPE_INFO_EXIST) {
        fileSchema = convertHeaderToSchema(reader.readLine(), reader, currentFile, true);
        logger.info("File {} schema collected automatically", currentFile);
      } else {
        fileSchema = convertHeaderToSchemaWithTypeInfo(reader.readLine(), currentFile);
        logger.info("File {} schema collected with existent type information", currentFile);
      }
      logger.debug("Current file schema: {}", fileSchema);
    } catch (IOException e) {
      logger.error("Cannot read schema from {}, skipping. {}", currentFile, e);
      return;
    }
    currentFileSchema = fileSchema;
  }

  @Override
  public Schema getCurrentSchema() {
    if (config.TYPE_INFO_EXIST) {
      return currentFileSchema;
    } else {
      return config.splitFileByDevice ? currentFileSchema : overallSchema;
    }
  }

  private static class SchemaSet {

    private final Map<String, Integer> fieldPrecisionMap = new HashMap<>();
    private final Map<String, Class<?>> fieldTypeMap = new HashMap<>();

    public void union(Schema schema) {
      if (schema == null) {
        return;
      }

      for (int i = 0; i < schema.getFields().length; i++) {
        int finalI = i;
        fieldPrecisionMap.compute(schema.getFields()[i], (s, p) -> Math.max(p == null ? 0 : p,
            schema.getPrecision()[finalI]));
        fieldTypeMap.compute(schema.getFields()[i], (s, t) -> {
          Class<?> newType = schema.getTypes()[schema.getIndex(s)];
          return mergeType(t, newType);
        });
      }
    }

    public static Class<?> mergeType(Class<?> t1, Class<?> t2) {
      if (t1 == null && t2 == null) {
        return null;
      }
      if (t1 == null) {
        return t2;
      }
      if (t2 == null) {
        return t1;
      }

      if (t1 == Double.class && t2 == Long.class || t1 == Long.class && t2 == Double.class) {
        return Double.class;
      }

      if (t1 != t2) {
        return String.class;
      }
      return t1;
    }

    public IndexedSchema toSchema() {
      MapIndexedSchema schema = new MapIndexedSchema();
      schema.setFields(new String[fieldPrecisionMap.size()]);
      schema.setPrecision(new int[fieldPrecisionMap.size()]);

      int index = 0;
      for (Entry<String, Integer> entry : fieldPrecisionMap.entrySet()) {
        schema.getFields()[index] = entry.getKey();
        schema.getTypes()[index] = fieldTypeMap.getOrDefault(entry.getKey(), String.class);
        schema.getPrecision()[index++] = entry.getValue();
      }

      return schema.rebuildIndex();
    }
  }
}
