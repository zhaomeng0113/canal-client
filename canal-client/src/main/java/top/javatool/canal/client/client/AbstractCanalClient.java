package top.javatool.canal.client.client;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import top.javatool.canal.client.handler.MessageHandler;

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


  protected Integer batchSize = 1;


  protected Long timeout = 1L;


  protected TimeUnit unit = TimeUnit.SECONDS;


  private MessageHandler messageHandler;

  @Autowired
  RedissonClient redissonClient;

  private static final String CURRENT_APP_ID = UUID.randomUUID().toString();
  RBucket<String> bucket;

  @Override
  public void start() {
    log.info("start canal client");
    for (; ; ) {
      bucket = redissonClient.getBucket(LOCK_NAME);
      if (bucket.isExists() && !bucket.get().equals(CURRENT_APP_ID)) {
        log.info("\n\n\n\ncanal client is runing anohter app now\n\n\n\n");
        try {
          Thread.sleep(60 * 1000 * 10);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } else {
        bucket.set(CURRENT_APP_ID, 50 * 10, TimeUnit.SECONDS);
        break;
      }
    }
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
        bucket.set(CURRENT_APP_ID, 50 * 10, TimeUnit.SECONDS);
        connector.connect();
        connector.subscribe(filter);
        connector.rollback();
        while (flag) {
          Message message = connector.getWithoutAck(batchSize, timeout, unit);
          long batchId = message.getId();
          //提前ACK
          connector.ack(batchId);
          if (message.getId() != -1 && message.getEntries().size() != 0) {
            messageHandler.handleMessage(message);
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
