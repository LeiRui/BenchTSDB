package cn.edu.thu.common;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

  public static final String DEFAULT_TAG = "DEFAULT";
  private static Logger logger = LoggerFactory.getLogger(Config.class);

  // INFLUXDB, OPENTSDB, SUMMARYSTORE, WATERWHEEL, KAIROSDB, TSFILE, PARQUET, ORC
  public String DATABASE = "TSFILE";

  // NOAA, GEOLIFE, MLAB_UTILIZATION, MLAB_IP, TDRIVE, REDD, SYNTHETIC
  public String DATA_SET = "REDD";
  public String DATA_DIR = "data/redd_low";

  public boolean useSynthetic = false;
  public double syntheticNullRatio = 0.0;
  public int syntheticDeviceNum = 100;
  public int syntheticMeasurementNum = 100;
  public int syntheticPointNum = 10000;

  // out file path
  public String FILE_PATH = "redd.tsfile";


  public int BEGIN_FILE = 0;
  public int END_FILE = 100000;

  public static final String TAG_NAME = "deviceId";
  public static final String TIME_NAME = "time";
  public static boolean FOR_QUERY = false;
  public boolean splitFileByDevice = true;

  public int THREAD_NUM = 1;
  public int BATCH_SIZE = 500;
  public int INFER_TYPE_MAX_RECORD_NUM = 10;

  public String CSV_SEPARATOR = ",";

  public boolean TYPE_INFO_EXIST = true;
  public String TYPE_INFO_FILE = "show_timeseries.csv";
  public int TYPE_INFO_SERIES_COL = 1;
  public int TYPE_INFO_TYPE_COL = 4;
  public boolean TYPE_INFO_HEADER = true;

  public String IOTDB_HOST = "127.0.0.1";
  public int IOTDB_PORT = 6667;
  public String IOTDB_USERNAME = "root";
  public String IOTDB_PASSWORD = "root";
  public boolean IOTDB_ENABLE_THRIFT_COMPRESSION = false;
  public String IOTDB_STORAGE_GROUP = "mysg1";

  public String INFLUXDB_URL = "http://127.0.0.1:8086";
  public String INFLUXDB_DATABASE = "mydb";

  public String OPENTSDB_URL = "http://127.0.0.1:4242";
//    public String OPENTSDB_URL = "http://192.168.10.64:4242";

  public String KAIROSDB_URL = "http://127.0.0.1:8080";
  public String CASSANDRA_IP = "127.0.0.1";
  public int CASSANDRA_PORT = 9042;

  public String SUMMARYSTORE_PATH = "sstore";

  public String WATERWHEEL_IP = "127.0.0.1";
  public String HDFS_IP = "hdfs://127.0.0.1:9000/"; // must end with '/'
  public boolean LOCAL = false;

  public int WATERWHEEL_INGEST_PORT = 10000;
  public int WATERWHEEL_QUERY_PORT = 10001;

  // TsFile configs
  public boolean useAlignedTablet = false;
  public boolean useAlignedSeries = false;

  // for query

  // geolife
//    public String QUERY_TAG = "000";
//    public String FIELD = "Latitude";
//    public long START_TIME = 0;
//    public long END_TIME = 1946816515000L;

  // redd
  public String QUERY_TAG = "house_1_channel_1";
  public String FIELD = "value";
  public long START_TIME = 0;
  public long END_TIME = 1946816515000L;

  // noaa
//    public String QUERY_TAG = "010230_99999";
//    public String FIELD = "TEMP";
//    public long START_TIME = 0L;
//    public long END_TIME = 1946816515000L;

  public Config() {
    Properties properties = new Properties();
    properties.putAll(System.getenv());
    load(properties);
    init();
    logger.debug("construct config without config file");
  }

  public Config(InputStream stream) throws IOException {
    Properties properties = new Properties();
    properties.putAll(System.getenv());
    properties.load(stream);
    load(properties);
    init();
    logger.debug("construct config with config file");
  }

  private void init() {
    if (!DATA_DIR.endsWith("/")) {
      DATA_DIR += "/";
    }
    logger.info("use dataset: {}", DATA_SET);
  }


  private void load(Properties properties) {

    DATABASE = properties.getOrDefault("DATABASE", DATABASE).toString();
    DATA_SET = properties.getOrDefault("DATA_SET", DATA_SET).toString();
    useSynthetic = "SYNTHETIC".equals(DATA_SET);
    THREAD_NUM = Integer.parseInt(properties.getOrDefault("THREAD_NUM", THREAD_NUM).toString());
    DATA_DIR = properties.getOrDefault("DATA_DIR", DATA_DIR).toString();

    TYPE_INFO_EXIST = Boolean
        .parseBoolean(properties.getOrDefault("TYPE_INFO_EXIST", TYPE_INFO_EXIST).toString());
    TYPE_INFO_FILE = properties.getOrDefault("TYPE_INFO_FILE", TYPE_INFO_FILE).toString();
    TYPE_INFO_SERIES_COL = Integer
        .parseInt(properties.getOrDefault("TYPE_INFO_SERIES_COL", TYPE_INFO_SERIES_COL).toString());
    TYPE_INFO_TYPE_COL = Integer
        .parseInt(properties.getOrDefault("TYPE_INFO_TYPE_COL", TYPE_INFO_TYPE_COL).toString());
    TYPE_INFO_HEADER = Boolean
        .parseBoolean(properties.getOrDefault("TYPE_INFO_HEADER", TYPE_INFO_HEADER).toString());

    IOTDB_HOST = properties.getOrDefault("IOTDB_HOST", IOTDB_HOST).toString();
    IOTDB_PORT = Integer.parseInt(properties.getOrDefault("IOTDB_PORT", IOTDB_PORT).toString());
    IOTDB_USERNAME = properties.getOrDefault("IOTDB_USERNAME", IOTDB_USERNAME).toString();
    IOTDB_PASSWORD = properties.getOrDefault("IOTDB_PASSWORD", IOTDB_PASSWORD).toString();
    IOTDB_ENABLE_THRIFT_COMPRESSION = Boolean.parseBoolean(
        properties.getOrDefault("IOTDB_ENABLE_THRIFT_COMPRESSION", IOTDB_ENABLE_THRIFT_COMPRESSION)
            .toString());
    IOTDB_STORAGE_GROUP = properties.getOrDefault("IOTDB_STORAGE_GROUP", IOTDB_STORAGE_GROUP)
        .toString();

    INFLUXDB_URL = properties.getOrDefault("INFLUXDB_URL", INFLUXDB_URL).toString();
    INFLUXDB_DATABASE = properties.getOrDefault("INFLUXDB_DATABASE", INFLUXDB_DATABASE).toString();

    KAIROSDB_URL = properties.getOrDefault("KAIROSDB_URL", KAIROSDB_URL).toString();
    CASSANDRA_IP = properties.getOrDefault("CASSANDRA_IP", CASSANDRA_IP).toString();
    CASSANDRA_PORT = Integer
        .parseInt(properties.getOrDefault("CASSANDRA_PORT", CASSANDRA_PORT).toString());

    OPENTSDB_URL = properties.getOrDefault("OPENTSDB_URL", OPENTSDB_URL).toString();
    WATERWHEEL_IP = properties.getOrDefault("WATERWHEEL_IP", WATERWHEEL_IP).toString();
    HDFS_IP = properties.getOrDefault("HDFS_IP", HDFS_IP).toString();
    SUMMARYSTORE_PATH = properties.getOrDefault("SUMMARYSTORE_PATH", SUMMARYSTORE_PATH).toString();
    FILE_PATH = properties.getOrDefault("FILE_PATH", FILE_PATH).toString();

    CSV_SEPARATOR = properties.getOrDefault("csv_separator", CSV_SEPARATOR).toString();

    splitFileByDevice = Boolean.parseBoolean(
        properties.getOrDefault("split_file_by_device", syntheticNullRatio).toString());

    syntheticNullRatio =
        Double.parseDouble(
            properties.getOrDefault("synthetic_null_ratio", syntheticNullRatio).toString());
    syntheticDeviceNum =
        Integer.parseInt(
            properties.getOrDefault("synthetic_device_num", syntheticDeviceNum).toString());
    syntheticMeasurementNum =
        Integer.parseInt(
            properties.getOrDefault("synthetic_measurement_num", syntheticMeasurementNum)
                .toString());
    syntheticPointNum =
        Integer
            .parseInt(properties.getOrDefault("synthetic_point_num", syntheticPointNum).toString());

    INFER_TYPE_MAX_RECORD_NUM = Integer
        .parseInt(properties.getOrDefault("INFER_TYPE_MAX_RECORD_NUM", INFER_TYPE_MAX_RECORD_NUM)
            .toString());

    useAlignedTablet = Boolean.parseBoolean(properties.getOrDefault("use_aligned_tablet",
        useAlignedTablet).toString());
    useAlignedSeries = Boolean.parseBoolean(properties.getOrDefault("use_aligned_series",
        useAlignedSeries).toString());

    BEGIN_FILE = Integer.parseInt(properties.getOrDefault("BEGIN_FILE", BEGIN_FILE).toString());
    END_FILE = Integer.parseInt(properties.getOrDefault("END_FILE", END_FILE).toString());
    BATCH_SIZE = Integer.parseInt(properties.getOrDefault("BATCH_SIZE", BATCH_SIZE).toString());
    WATERWHEEL_INGEST_PORT = Integer.parseInt(
        properties.getOrDefault("WATERWHEEL_INGEST_PORT", WATERWHEEL_INGEST_PORT).toString());
    WATERWHEEL_QUERY_PORT = Integer.parseInt(
        properties.getOrDefault("WATERWHEEL_QUERY_PORT", WATERWHEEL_QUERY_PORT).toString());
    LOCAL = Boolean.parseBoolean(properties.getOrDefault("LOCAL", LOCAL).toString());

    QUERY_TAG = properties.getOrDefault("QUERY_TAG", QUERY_TAG).toString();

    FIELD = properties.getOrDefault("FIELD", FIELD).toString();

    String startTime = properties.getOrDefault("START_TIME", START_TIME).toString();
    if (startTime.toLowerCase().contains("min")) {
//      START_TIME = Long.MIN_VALUE;
      // do nothing, just leave START_TIME as 0
    } else {
      START_TIME = Long.parseLong(startTime);
    }

    String endTime = properties.getOrDefault("END_TIME", END_TIME).toString();
    if (endTime.toLowerCase().contains("max")) {
//      END_TIME = Long.MAX_VALUE;
      // do nothing, just leave END_TIME as 1946816515000 which means Wednesday, September 10, 2031 14:21:55 in milliseconds
    } else {
      END_TIME = Long.parseLong(endTime);
    }

  }
}