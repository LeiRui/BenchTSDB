package cn.edu.thu.database.fileformat;

import cn.edu.thu.common.Config;
import cn.edu.thu.common.Record;
import cn.edu.thu.database.IDataBaseManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.orc.Writer;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.predicate.Operators;
import org.apache.parquet.hadoop.ParquetInputFormat;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.parquet.filter2.predicate.FilterApi.*;


/**
 * time, seriesid, value
 *
 * time, deviceId, s1, s2, s3...
 *
 * time, series1, series2...
 */
public class ParquetManager implements IDataBaseManager {

  private static Logger logger = LoggerFactory.getLogger(ParquetManager.class);
  private MessageType schema;
  private ParquetWriter[] writers;
  private SimpleGroupFactory simpleGroupFactory;
  private Config config;
  private String filePath;
  private String schemaName = "defaultSchema";

  public ParquetManager(Config config) {
    this.config = config;
    this.filePath = config.FILE_PATH;
  }

  public ParquetManager(Config config, int threadNum) {
    this.config = config;
    this.filePath = config.FILE_PATH + "_" + threadNum;
  }

  @Override
  public void initServer() {

  }

  @Override
  public void initClient() {
    if (Config.FOR_QUERY) {
      return;
    }

    Types.MessageTypeBuilder builder = Types.buildMessage();
    builder.addField(new PrimitiveType(Type.Repetition.REQUIRED, PrimitiveType.PrimitiveTypeName.INT64, config.TIME_NAME));
    if (!config.splitFileByDevice) {
      builder.addField(new PrimitiveType(Type.Repetition.REQUIRED, PrimitiveType.PrimitiveTypeName.BINARY, Config.TAG_NAME));
    }
    for (int i = 0; i < config.FIELDS.length; i++) {
      builder.addField(new PrimitiveType(Type.Repetition.OPTIONAL, PrimitiveType.PrimitiveTypeName.DOUBLE, config.FIELDS[i]));
    }

    schema = builder.named(schemaName);
    simpleGroupFactory = new SimpleGroupFactory(schema);
    Configuration configuration = new Configuration();

    createWriters(configuration);
  }

  private void createWriters(Configuration configuration) {
    int fileNum = 1;
    if (config.useSynthetic && config.splitFileByDevice) {
      fileNum = config.syntheticDeviceNum;
    }
    writers = new ParquetWriter[fileNum];

    for (int i = 0; i < fileNum; i++) {
      GroupWriteSupport.setSchema(schema, configuration);
      GroupWriteSupport groupWriteSupport = new GroupWriteSupport();
      groupWriteSupport.init(configuration);
      new File(i + "_" + filePath).delete();
      try {
        writers[i] = new ParquetWriter(new Path(i + "_" + filePath), groupWriteSupport,
            CompressionCodecName.SNAPPY,
            ParquetWriter.DEFAULT_BLOCK_SIZE, ParquetWriter.DEFAULT_PAGE_SIZE, ParquetWriter.DEFAULT_PAGE_SIZE,
            true, true, ParquetProperties.WriterVersion.PARQUET_2_0);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private ParquetWriter getWriter(String tag) {
    if (config.splitFileByDevice) {
      return writers[getFileIndex(tag)];
    } else {
      return writers[0];
    }
  }

  private int getFileIndex(String tag) {
    // root.device_i
    return Integer.parseInt(tag.split("_")[1]);
  }

  @Override
  public long insertBatch(List<Record> records) {
    long start = System.nanoTime();

    List<Group> groups = convertRecords(records);
    for(Group group: groups) {
      try {
        getWriter(records.get(0).tag).write(group);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return System.nanoTime() - start;
  }


  private List<Group> convertRecords(List<Record> records) {
    List<Group> groups = new ArrayList<>();
    for(Record record: records) {
      Group group = simpleGroupFactory.newGroup();
      group.add(Config.TIME_NAME, record.timestamp);
      if (!config.splitFileByDevice) {
        group.add(Config.TAG_NAME, record.tag);
      }
      for(int i = 0; i < config.FIELDS.length; i++) {
        if (record.fields.get(i) != null) {
          double floatV = (double) record.fields.get(i);
          group.add(config.FIELDS[i], floatV);
        }
      }
      groups.add(group);
    }
    return groups;
  }

  @Override
  public long count(String tagValue, String field, long startTime, long endTime) {

    Configuration conf = new Configuration();
    if (!config.splitFileByDevice) {
      ParquetInputFormat.setFilterPredicate(conf, and(and(gtEq(longColumn(Config.TIME_NAME), startTime),
          ltEq(longColumn(Config.TIME_NAME), endTime)),
          eq(binaryColumn(Config.TAG_NAME), Binary.fromString(tagValue))));
    } else {
      ParquetInputFormat.setFilterPredicate(conf, and(gtEq(longColumn(Config.TIME_NAME), startTime),
          ltEq(longColumn(Config.TIME_NAME), endTime)));
    }

    FilterCompat.Filter filter = ParquetInputFormat.getFilter(conf);

    Types.MessageTypeBuilder builder = Types.buildMessage();
    builder.addField(new PrimitiveType(Type.Repetition.REQUIRED, PrimitiveType.PrimitiveTypeName.INT64, Config.TIME_NAME));
    if (!config.splitFileByDevice) {
      builder.addField(new PrimitiveType(Type.Repetition.REQUIRED, PrimitiveType.PrimitiveTypeName.BINARY, Config.TAG_NAME));
    }
    builder.addField(new PrimitiveType(Type.Repetition.OPTIONAL, PrimitiveType.PrimitiveTypeName.DOUBLE, field));

    MessageType querySchema = builder.named(schemaName);
    conf.set(ReadSupport.PARQUET_READ_SCHEMA, querySchema.toString());

    // set reader
    ParquetReader.Builder<Group> reader= ParquetReader
            .builder(new GroupReadSupport(), new Path(getFileIndex(tagValue) + "_" + filePath))
            .withConf(conf)
            .withFilter(filter);

    long start = System.nanoTime();

    ParquetReader<Group> build;
    int result = 0;
    try {
      build = reader.build();
      Group line;
      while((line=build.read())!=null) {
        result++;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    logger.info("Parquet result: {}", result);

    return System.nanoTime() - start;
  }

  @Override
  public long flush() {
    return 0;
  }

  @Override
  public long close() {
    long start = System.nanoTime();
    long fileSize = 0;
    for (int i = 0, writersLength = writers.length; i < writersLength; i++) {
      ParquetWriter writer = writers[i];
      try {
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      fileSize += new File(i + "_" + filePath).length();
    }
    logger.info("Total file size: {}", fileSize / (1024*1024.0));
    return System.nanoTime() - start;
  }
}
