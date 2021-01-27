package top.javatool.canal.client.client;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import top.javatool.canal.client.handler.MessageHandler;
import top.javatool.canal.client.util.IpUtil;

/**
 * @author yang peng
 * @date 2019/3/2619:11
 */

public abstract class AbstractCanalClient implements CanalClient {


  protected volatile boolean flag;


  private Logger log = LoggerFactory.getLogger(AbstractCanalClient.class);


  private Thread workThread;


  private CanalConnector connector;


  protected String filter = StringUtils.EMPTY;

  private static final String LOCK_NAME = "spark:api:canal:start";


  protected Integer batchSize = 100;


  protected Long timeout = 1L;


  protected TimeUnit unit = TimeUnit.SECONDS;


  private MessageHandler messageHandler;

  @Autowired
  RedissonClient redissonClient;

  private static final String CURRENT_APP_ID = IpUtil.getLinuxLocalIp().replaceAll("\\.", "-");

  @Override
  public void start() {
    log.info("start canal client");
    RBucket<String> bucket = redissonClient.getBucket(LOCK_NAME);
    if (bucket.isExists() && !bucket.get().equals(CURRENT_APP_ID)) {
      log.info("\n\n\n\ncanal client is runing anohter app now\n\n\n\n");
      return;
    }
    bucket.set(CURRENT_APP_ID, 50 * 10, TimeUnit.SECONDS);
    workThread = new Thread(this::process);
    workThread.setName("canal-client-thread");
    flag = true;
    workThread.start();
    log.info("canal client started...........");
  }

  @Override
  public void stop() {
    log.info("stop canal client");
    RBucket<String> bucket = redissonClient.getBucket(LOCK_NAME);
    if (bucket.isExists() && bucket.get().equals(CURRENT_APP_ID)) {
      log.info("releasing canal client bucket ...........");
      bucket.deleteAsync();
    }
    flag = false;
    if (null != workThread) {
      workThread.interrupt();
    }

  }

  @Override
  public void process() {
    while (flag) {
      try {
        //续期
        connector.connect();
        connector.subscribe(filter);
        connector.rollback();
        while (flag) {
          Message message = connector.getWithoutAck(batchSize, timeout, unit);
          long batchId = message.getId();
          try {
            if (batchId != -1) {
              log.debug("scheduled_batchId=" + batchId);
            }
            //提前ACK
            connector.ack(batchId);
            if (message.getId() != -1 && message.getEntries().size() != 0) {
              messageHandler.handleMessage(message);
            }
          } catch (Exception e) {
            log.error("canal client 异常", e);
          }
        }
      } catch (Exception e) {
        log.error("canal client 异常", e);
      } finally {
        connector.disconnect();
      }
    }
  }


  public void setConnector(CanalConnector connector) {
    this.connector = connector;
  }


  public void setMessageHandler(MessageHandler messageHandler) {
    this.messageHandler = messageHandler;
  }


  public CanalConnector getConnector() {
    return connector;
  }


  public MessageHandler getMessageHandler() {
    return messageHandler;
  }
}
