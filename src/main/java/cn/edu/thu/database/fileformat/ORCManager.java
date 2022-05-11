package cn.edu.thu.database.fileformat;

import cn.edu.thu.common.Config;
import cn.edu.thu.common.Record;
import cn.edu.thu.common.Schema;
import cn.edu.thu.database.IDataBaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Map.Entry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.orc.*;
import org.apache.orc.OrcFile.Version;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.DoubleColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * time, seriesid, value
 * <p>
 * time, deviceId, s1, s2, s3...
 * <p>
 * time, series1, series2...
 */
public class ORCManager implements IDataBaseManager {

  private static Logger logger = LoggerFactory.getLogger(ORCManager.class);

  private Map<String, Writer> writerMap = new HashMap<>();
  private Config config;
  private String filePath;

  private long totalFileSize = 0;

  public ORCManager(Config config) {
    this.config = config;
    this.filePath = config.FILE_PATH;
  }

  public ORCManager(Config config, int threadNum) {
    this.config = config;
    this.filePath = config.FILE_PATH + "_" + threadNum;
  }

  @Override
  public void initServer() {

  }

  @Override
  public void initClient() {

  }

  private Writer createWriter(String tag, Schema schema) {
    TypeDescription orcSchema = TypeDescription.fromString(genWriteSchema(schema));

    String fullFilePath = tagToFilePath(tag);
    new File(fullFilePath).delete();
    Writer writer = null;
    try {
      writer = OrcFile.createWriter(new Path(fullFilePath),
          OrcFile.writerOptions(new Configuration())
              .setSchema(orcSchema)
              .compress(CompressionKind.SNAPPY)
              .version(Version.V_0_12));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return writer;
  }

  @Override
  public long insertBatch(List<Record> records, Schema schema) {

    long start = System.nanoTime();

    String tag = records.get(0).tag;
    Writer writer = getWriter(tag, schema);

    VectorizedRowBatch batch = writer.getSchema().createRowBatch(records.size());

    for (int i = 0; i < records.size(); i++) {
      Record record = records.get(i);
      LongColumnVector time = (LongColumnVector) batch.cols[0];
      time.vector[i] = record.timestamp;

      if (!config.splitFileByDevice) {
        BytesColumnVector device = (BytesColumnVector) batch.cols[1];
        device.setVal(i, record.tag.getBytes(StandardCharsets.UTF_8));
      }

      for (int j = 0; j < schema.fields.length; j++) {
        DoubleColumnVector v;
        if (!config.splitFileByDevice) {
          v = (DoubleColumnVector) batch.cols[j + 2];
        } else {
          v = (DoubleColumnVector) batch.cols[j + 1];
        }
        if (record.fields.get(j) != null) {
          v.vector[i] = (double) record.fields.get(j);
          v.isNull[i] = false;
        } else {
          v.isNull[i] = true;
          v.noNulls = false;
        }
      }

      batch.size++;

      // If the batch is full, write it out and start over. actually not needed here
      if (batch.size == batch.getMaxSize()) {
        try {
          writer.addRowBatch(batch);
        } catch (IOException e) {
          e.printStackTrace();
        }
        batch.reset();
      }
    }

    return System.nanoTime() - start;
  }

  private Writer getWriter(String tag, Schema schema) {
    if (!config.splitFileByDevice) {
      return writerMap.computeIfAbsent(Config.DEFAULT_TAG, t -> createWriter(t, schema));
    } else {
      return writerMap.computeIfAbsent(tag, t -> createWriter(t, schema));
    }
  }

  private String tagToFilePath(String tag) {
    if (config.splitFileByDevice) {
      return tag + "_" + filePath;
    } else {
      return Config.DEFAULT_TAG + "_" + filePath;
    }
  }

  private String genWriteSchema(Schema schema) {
    String s;
    if (config.splitFileByDevice) {
      s = "struct<timestamp:bigint";
    } else {
      s = "struct<timestamp:bigint,deviceId:string";
    }

    for (int i = 0; i < schema.fields.length; i++) {
      s += ("," + schema.fields[i] + ":" + "DOUBLE");
    }
    s += ">";
    return s;
  }

  private String getReadSchema(String field) {
    if (!config.splitFileByDevice) {
      return "struct<timestamp:bigint,deviceId:string," + field + ":DOUBLE>";
    } else {
      return "struct<timestamp:bigint," + field + ":DOUBLE>";
    }
  }

  @Override
  public long count(String tagValue, String field, long startTime, long endTime) {

    long start = System.nanoTime();

    String schema = getReadSchema(field);
    try {
      Reader reader = OrcFile.createReader(new Path(tagToFilePath(tagValue)),
          OrcFile.readerOptions(new Configuration()));
      TypeDescription readSchema = TypeDescription.fromString(schema);

      VectorizedRowBatch batch = readSchema.createRowBatch();
      RecordReader rowIterator = reader.rows(reader.options().schema(readSchema));

      int result = 0;
      while (rowIterator.nextBatch(batch)) {
        for (int r = 0; r < batch.size; ++r) {

          // time, deviceId, field
          long t = ((LongColumnVector) batch.cols[0]).vector[r];
          if (t < startTime || t > endTime) {
            continue;
          }

          if (!config.splitFileByDevice) {
            String deviceId = ((BytesColumnVector) batch.cols[1]).toString(r);
            if (deviceId.endsWith(tagValue)) {
              result++;
            }
            double fieldValue = ((DoubleColumnVector) batch.cols[2]).vector[r];
          } else {
            result++;
            double fieldValue = ((DoubleColumnVector) batch.cols[1]).vector[r];
          }
        }
      }
      rowIterator.close();

      logger.info("ORC result: {}", result);

    } catch (IOException e) {
      e.printStackTrace();
    }

    return System.nanoTime() - start;
  }

  @Override
  public long flush() {
    return 0;
  }

  @Override
  public long close() {
    long start = System.nanoTime();
    for (Entry<String, Writer> entry : writerMap.entrySet()) {
      try {
        entry.getValue().close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      totalFileSize += new File(tagToFilePath(entry.getKey())).length();
    }
    logger.info("Total file size: {}", totalFileSize / (1024 * 1024.0));
    return System.nanoTime() - start;
  }
}
