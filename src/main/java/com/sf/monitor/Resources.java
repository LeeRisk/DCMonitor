package com.sf.monitor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sf.influxdb.InfluxDB;
import com.sf.influxdb.InfluxDBFactory;
import com.sf.log.Logger;
import com.sf.monitor.druid.DruidInfos;
import com.sf.monitor.kafka.KafkaInfos;
import com.sf.monitor.zk.ZookeeperHosts;
import com.sf.notify.Notify;
import kafka.utils.ZKStringSerializer;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.commons.io.IOUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class Resources {
  private static final Logger log = new Logger(Resources.class);
  public static ThreadLocal<Boolean> isZKDirectBytes = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }
  };

  private static final ZkSerializer zkSerializer = new ZkSerializer() {
    @Override
    public byte[] serialize(Object data) throws ZkMarshallingError {
      return ZKStringSerializer.serialize(data);
    }

    @Override
    public Object deserialize(byte[] bytes) throws ZkMarshallingError {
      if (isZKDirectBytes.get()) {
        return bytes;
      } else {
        return ZKStringSerializer.deserialize(bytes);
      }
    }
  };

  public static CuratorFramework curator;
  public static KafkaInfos kafkaInfos;
  public static DruidInfos druidInfos;
  public static ZookeeperHosts zkHosts;
  public static InfluxDB influxDB;
  public static Notify notify;
  public static Config.Fetchers fetchers;

  public static final ObjectMapper jsonMapper = new ObjectMapper();

  static {
    jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
  }

  public static void init() {
    try {
      Config.CuratorConfig zkConfig = Config.config.zookeeper;
      curator = CuratorFrameworkFactory
        .builder()
        .connectString(zkConfig.addrs)
        .retryPolicy(new ExponentialBackoffRetry(zkConfig.baseSleepTimeMs, zkConfig.maxRetries, zkConfig.maxSleepMs))
        .build();
      curator.start();
      kafkaInfos = new KafkaInfos(new ZkClient(zkConfig.addrs, 30000, zkConfig.connectionTimeout, zkSerializer));
      druidInfos = Config.config.druidInfos;
      zkHosts = new ZookeeperHosts(Config.config.zookeeper.addrs);
      druidInfos.init();
      influxDB = InfluxDBFactory.connect(
        Config.config.influxdb.influxdbUrl,
        Config.config.influxdb.influxdbUser,
        Config.config.influxdb.influxdbPassword
      );
      notify = new Notify(Config.config.notify.url);
      fetchers = Config.config.fetchers;
    } catch (RuntimeException e) {
      log.error(e, "error while initialyzing resourses");
      throw e;
    }
  }

  public static void close() {
    IOUtils.closeQuietly(kafkaInfos);
    IOUtils.closeQuietly(curator);
  }
}
