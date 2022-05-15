package cn.edu.thu.database;

import cn.edu.thu.common.Config;
import cn.edu.thu.database.fileformat.ORCManager;
import cn.edu.thu.database.fileformat.ParquetManager;
//import cn.edu.thu.database.fileformat.TsFileManager;
import cn.edu.thu.database.iotdb.IoTDBManager;
import cn.edu.thu.database.kairosdb.KairosDBManager;
import cn.edu.thu.database.opentsdb.OpenTSDBManager;
//import cn.edu.thu.database.waterwheel.WaterWheelManager;

public class DatabaseFactory {

  public static IDataBaseManager getDbManager(Config config) {
    switch (config.DATABASE) {
      case "NULL":
        return new NullManager();
      case "IOTDB": // 0.13.0
        return new IoTDBManager(config);
//      case "INFLUXDB":
//        return new InfluxDBManager(config);
      case "OPENTSDB":
        return new OpenTSDBManager(config);
      case "KAIROSDB":
        return new KairosDBManager(config);
//      case "SUMMARYSTORE":
//        return new SummaryStoreManager(config);
//      case "WATERWHEEL":
//        return new WaterWheelManager(config);
//      case "TSFILE":
//        return new TsFileManager(config);
      case "PARQUET":
        return new ParquetManager(config);
      case "ORC":
        return new ORCManager(config);
      default:
        throw new RuntimeException(config.DATABASE + " not supported");
    }
  }

}
