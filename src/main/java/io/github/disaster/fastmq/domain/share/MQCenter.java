package io.github.disaster.fastmq.domain.share;

import io.github.disaster.fastmq.domain.consumer.FastMQListener;
import io.github.disaster.fastmq.domain.consumer.FastMQMessageListener;
import io.github.disaster.fastmq.domain.service.FastMQService;
import io.github.disaster.fastmq.infrastructure.constant.FastMQConstant;
import io.github.disaster.fastmq.infrastructure.prop.FastMQProperties;
import jodd.util.concurrent.ThreadFactoryBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.RedisBusyException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * mq的事件处理中心
 * <p>
 * 参考：
 * https://juejin.cn/post/7112825943231561741#heading-13
 *
 * @author disaster
 * @version 1.0
 */
@Component
@Slf4j
@Data
public class MQCenter implements ApplicationRunner, ApplicationContextAware {
    /**
     * 存放没有注解修饰的监听器
     */
    private Set<FastMQListener<?>> fastMQListeners0;

    /**
     * 存放有注解修饰的监听器
     */
    private Set<FastMQListener<?>> fastMQListeners1;

    /**
     * redissonClient对象
     */
    @Autowired
    private RedissonClient client;

    /**
     * fastmq配置属性
     */
    @Autowired
    private FastMQProperties fastMQProperties;

    /**
     * 领域服务对象
     */
    @Autowired
    private FastMQService fastMQService;

    /**
     * spring上下文对象，用于获取fastmq的bean实例
     */
    private ApplicationContext applicationContext;

    /**
     * Instantiates a new Mq center.
     */
    public MQCenter() {
        fastMQListeners0 = new HashSet<>();
        fastMQListeners1 = new HashSet<>();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, FastMQListener> beansOfType = applicationContext.getBeansOfType(FastMQListener.class);
        for (Map.Entry<String, FastMQListener> entry : beansOfType.entrySet()) {
            FastMQListener value = entry.getValue();
            Class aClass = value.getClass();
            if (aClass.isAnnotationPresent(FastMQMessageListener.class)) {
                fastMQListeners1.add(value);
            } else {
                fastMQListeners0.add(value);
            }
            createConsumerGroup(value);
        }
        start();
    }

    private void createConsumerGroup(FastMQListener<?> fastMQListener) {
        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());
        //从尾开始读消息
        StreamMessageId id = StreamMessageId.NEWEST;
        if (fastMQProperties.getIsStartFromHead()) {
            //从头开始读消息
            id = StreamMessageId.ALL;
        }
        try {
            stream.createGroup(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                    : fastMQMessageListener.groupName(), id);
        } catch (RedisBusyException e) {
            log.info(e.getMessage());
        }
    }


    /**
     * 处理默认stream中的相关死信、超时信息重传、异步认领空闲过久的消息
     */
    public void checkPendingList1() {
        for (FastMQListener<?> fastMQListener :
                this.fastMQListeners1) {
            _checkPendingList(fastMQListener);
        }
    }

    /**
     * 处理指定stream中的相关死信、超时信息重传、异步认领空闲过久的消息
     */
    public void checkPendingList0() {
        for (FastMQListener<?> fastMQListener :
                this.fastMQListeners0) {
            _checkPendingList(fastMQListener);
        }
    }

    private void _checkPendingList(FastMQListener<?> fastMQListener) {
        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);

        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //异步执行XPENDING key group [[IDLE min-idle-time] start end count  [consumer]] 获取某个消费者组中的未处理消息的相关信息
        RFuture<List<PendingEntry>> future = stream.listPendingAsync(
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                        : fastMQMessageListener.groupName(),
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                        : fastMQMessageListener.consumeName(),
                StreamMessageId.MIN,
                StreamMessageId.MAX,
                fastMQProperties.getIdle().getPendingListIdleThreshold(),
                fastMQProperties.getIdle().getTimeUnit(),
                fastMQProperties.getCheckPendingListSize());

        future.thenAccept(pendingEntryList -> {
                    Set<StreamMessageId> deadLetterIds = new HashSet<>();
                    Set<StreamMessageId> idleIds = new HashSet<>();
                    //获取死信消息
                    for (PendingEntry entry :
                            pendingEntryList) {
                        long cnt = entry.getLastTimeDelivered();
                        //判断是否超过fastMQProperties中指定的时间
                        if (cnt >= this.fastMQProperties.getDeadLetterThreshold()) {
                            //加入死信队列中
                            deadLetterIds.add(entry.getId());
                        } else {
                            //否则加入超时队列中
                            idleIds.add(entry.getId());
                        }
                    }
                    //处理超时队列逻辑：目前仅支持随机消费者重新消费
                    fastMQService.consumeIdleMessagesAsync(idleIds, fastMQListener);
                    fastMQService.consumeDeadLetterMessagesAsync(deadLetterIds, fastMQListener);
                    fastMQService.claimIdleConsumerAsync(fastMQListener);
                }
        ).exceptionally(exception -> {
            exception.printStackTrace();
            return null;
        });
    }

    /**
     * 消费默认stream
     */
    public void consumeFastMQListeners0() {
        for (FastMQListener<?> fastMQListener :
                this.fastMQListeners0) {
            //没有注解修饰的，统一采用默认的主题去消费
            RStream<Object, Object> stream = client.getStream(FastMQConstant.DEFAULT_TOPIC);

            //执行XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds]  [NOACK] STREAMS key [key ...] ID [ID ...]
            RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                    stream.readGroupAsync(FastMQConstant.DEFAULT_CONSUMERGROUP,
                            FastMQConstant.DEFAULT_CONSUMER,
                            fastMQProperties.getFetchMessageSize(),
                            StreamMessageId.NEVER_DELIVERED);

            future.thenAccept(res ->

                    //异步执行的后续操作
                    fastMQService.consumeMessagesAsync(res, fastMQListener, stream, null)

            ).exceptionally(exception -> {
                //异常处理
                log.info("consumeHealthMessages Exception:{}", exception.getMessage());
                exception.printStackTrace();
                return null;
            });

        }
    }

    /**
     * 消费带注解的stream
     */
    public void consumeFastMQListeners1() {

        for (FastMQListener<?> fastMQListener :
                this.fastMQListeners1) {
            FastMQMessageListener fastMQMessageListener = AnnotationUtils.findAnnotation(fastMQListener.getClass(), FastMQMessageListener.class);
            RStream<Object, Object> stream = client.getStream(fastMQMessageListener.topic());

            //执行XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds]  [NOACK] STREAMS key [key ...] ID [ID ...]
            RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                    stream.readGroupAsync(fastMQMessageListener.groupName(),
                            fastMQMessageListener.consumeName(),
                            fastMQMessageListener.readSize() == -1 ? fastMQProperties.getFetchMessageSize() : fastMQMessageListener.readSize(),
                            StreamMessageId.NEVER_DELIVERED);

            //异步执行的后续操作
            future.thenAccept(res ->

                    fastMQService.consumeMessagesAsync(res, fastMQListener, stream, fastMQMessageListener)

            ).exceptionally(exception -> {

                //异常处理
                log.info("consumeHealthMessages Exception:{}", exception.getMessage());
                exception.printStackTrace();
                return null;

            });
        }
    }


    private void start() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(
                fastMQProperties.getExecutor().getExecutorCoreSize(),
                ThreadFactoryBuilder.create()
                        .setDaemon(true)
                        .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                        .setPriority(6)
                        .get());

        service.scheduleAtFixedRate(
                () -> {
                    consumeFastMQListeners1();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getPullHealthyMessagesPeriod(),
                TimeUnit.SECONDS);
        /**
         * 指定消费组单独采用一个线程去执行
         */
        service.scheduleAtFixedRate(
                () -> {
                    checkPendingList1();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getCheckPendingListsPeriod(),
                TimeUnit.SECONDS);

        service.scheduleAtFixedRate(
                () -> {
                    consumeFastMQListeners0();
                    checkPendingList0();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getPullHealthyMessagesPeriod(),
                TimeUnit.SECONDS);


    }


}
