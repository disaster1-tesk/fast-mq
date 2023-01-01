package io.github.fastmq.domain.share;

import io.github.fastmq.domain.consumer.delay.FastMQDelayListener;
import io.github.fastmq.domain.consumer.delay.FastMQDelayMessageListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQMessageListener;
import io.github.fastmq.domain.service.FastMQAsyncService;
import io.github.fastmq.domain.service.FastMQService;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.prop.FastMQProperties;
import io.github.fastmq.infrastructure.utils.BeanMapUtils;
import io.github.fastmq.infrastructure.utils.ThreadPoolUtil;
import jodd.util.concurrent.ThreadFactoryBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.RedisBusyException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.*;

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
public class MQCenter implements ApplicationRunner, ApplicationContextAware, DisposableBean {
    /**
     * 存放没有注解修饰的监听器
     */
    private Set<FastMQListener> fastMQListeners0;

    /**
     * 存放有注解修饰的监听器
     */
    private Set<FastMQListener> fastMQListeners1;

    /**
     * 存放延时注解修饰的监听器
     */
    private Set<FastMQDelayListener> fastMQListeners2;

    /**
     * redissonClient对象
     */
    private final RedissonClient client;

    /**
     * fastmq配置属性
     */
    private final FastMQProperties fastMQProperties;

    /**
     * 领域服务对象-异步
     */
    private final FastMQAsyncService fastMQAsyncService;

    /**
     * 领域服务对象-同步
     */
    private final FastMQService fastMQService;


    @Autowired
    public MQCenter(RedissonClient client, FastMQProperties fastMQProperties, FastMQAsyncService fastMQAsyncService, FastMQService fastMQService) {
        this.client = client;
        this.fastMQProperties = fastMQProperties;
        this.fastMQAsyncService = fastMQAsyncService;
        this.fastMQService = fastMQService;
        fastMQListeners0 = new HashSet<>();
        fastMQListeners1 = new HashSet<>();
        fastMQListeners2 = new HashSet<>();
    }

    /**
     * spring上下文对象，用于获取fastmq的bean实例
     */
    private ApplicationContext applicationContext;

    private ScheduledExecutorService service = null;

    private static String luaStr;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("local zkey = KEYS[1]\n")
                .append("local maxsco = ARGV[1]\n")
                .append("local zrange = redis.call('zrangebyscore',zkey,0,maxsco,'LIMIT',0,1)\n")
                .append("if next(zrange) ~= nil and #zrange > 0\n")
                .append("then\n")
                .append("\tlocal rmnum = redis.call('zrem',zkey,unpack(zrange))\n")
                .append("\tif(rmnum > 0)\n")
                .append("\tthen\n")
                .append("\t\treturn zrange\n")
                .append("\tend\n")
                .append("else\n")
                .append("\treturn {}\n")
                .append("end\n")
        ;

        luaStr = sb.toString();
    }


    private void initThreadPool() {
        service = Executors.newScheduledThreadPool(
                fastMQProperties.getExecutor().getExecutorCoreSize(),
                ThreadFactoryBuilder.create()
                        .setDaemon(false)
                        .setNameFormat("fast-mq-schedule-")
                        .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                        .setPriority(6)
                        .get());
        ThreadPoolUtil.QUEUE.init(service);
        ExecutorService delayExecutor = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder.create()
                .setNameFormat("fast-mq-delay")
                .setDaemon(false)
                .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                .get());
        ThreadPoolUtil.DELAY.init(delayExecutor);
        log.info("fast-mq线程池初始化完成！！");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Objects.nonNull(fastMQProperties.getEnable()) && fastMQProperties.getEnable()) {
            initThreadPool();
            Map<String, FastMQListener> fastMQListenerMap = applicationContext.getBeansOfType(FastMQListener.class);
            Map<String, FastMQDelayListener> fastMQDelayListenerMap = applicationContext.getBeansOfType(FastMQDelayListener.class);
            for (Map.Entry<String, FastMQDelayListener> delayListenerEntry : fastMQDelayListenerMap.entrySet()) {
                fastMQListeners2.add(delayListenerEntry.getValue());
            }
            for (Map.Entry<String, FastMQListener> listenerEntry : fastMQListenerMap.entrySet()) {
                FastMQListener value = listenerEntry.getValue();
                Class aClass = value.getClass();
                if (aClass.isAnnotationPresent(FastMQMessageListener.class)) {
                    fastMQListeners1.add(value);
                } else {
                    fastMQListeners0.add(value);
                }
                createConsumerGroup(value);
            }
            start();
        } else {
            log.info("您当前未启动fast-mq" +
                    "\nfastmq:\n" +
                    "  config:\n" +
                    "    enable=" + false);
        }
    }

    private void createConsumerGroup(FastMQListener fastMQListener) {
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
            stream.createGroupAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                    : fastMQMessageListener.groupName(), id);
            log.info("主题 = {} ,消费组 = {} 创建成功", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                    : fastMQMessageListener.topic(), Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                    : fastMQMessageListener.groupName());
        } catch (RedisBusyException e) {
            log.info(e.getMessage());
        }
    }


    /**
     * 处理默认stream中的相关死信、超时信息重传、异步认领空闲过久的消息
     */
    public void checkPendingList1() {
        for (FastMQListener fastMQListener :
                this.fastMQListeners1) {
            _checkPendingList(fastMQListener);
        }
    }

    /**
     * 处理指定stream中的相关死信、超时信息重传、异步认领空闲过久的消息
     */
    public void checkPendingList0() {
        for (FastMQListener fastMQListener :
                this.fastMQListeners0) {
            _checkPendingList(fastMQListener);
        }
    }

    private void _checkPendingList(FastMQListener fastMQListener) {
        if (fastMQProperties.getIsAsync()) {
            fastMQAsyncService.checkPendingListAsync(fastMQListener);
        } else {
            fastMQService.checkPendingList(fastMQListener);
        }
    }


    /**
     * 消费默认stream
     */
    public void consumeFastMQListeners0() {
        for (FastMQListener fastMQListener :
                this.fastMQListeners0) {
            _consumeFastMQListener(fastMQListener);
        }
    }

    private void _consumeFastMQListener(FastMQListener fastMQListener) {
        if (fastMQProperties.getIsAsync()) {
            fastMQAsyncService.consumeFastMQListenersAsync(fastMQListener);
        } else {
            fastMQService.consumeFastMQListeners(fastMQListener);
        }
    }

    /**
     * 消费带注解的stream
     */
    public void consumeFastMQListeners1() {
        for (FastMQListener fastMQListener :
                this.fastMQListeners1) {
            _consumeFastMQListener(fastMQListener);
        }
    }

    /**
     * 消费延时队列
     */
    public void consumeFastMQListeners2() {
        for (FastMQDelayListener fastMQDelayListener : fastMQListeners2) {
            Class<? extends FastMQDelayListener> aClass = fastMQDelayListener.getClass();
            FastMQDelayMessageListener annotation = aClass.getAnnotation(FastMQDelayMessageListener.class);
            String key = Objects.nonNull(annotation) && StringUtils.hasText(annotation.queueName()) ? annotation.queueName() : FastMQConstant.DEFAULT_DElAY_QUEUE;
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            client.getDelayedQueue(blockingDeque);
            if (Objects.nonNull(annotation) && StringUtils.hasText(annotation.executorName())) {
                ThreadPoolTaskExecutor bean = applicationContext.getBean(annotation.queueName(), ThreadPoolTaskExecutor.class);
                bean.submit(() -> {
                    doDelayMsgConsumer(fastMQDelayListener, key, blockingDeque);
                });
            } else {
                ThreadPoolUtil.DELAY.run(() -> {
                    doDelayMsgConsumer(fastMQDelayListener, key, blockingDeque);
                });
            }
        }

    }

    private void doDelayMsgConsumer(FastMQDelayListener fastMQDelayListener, String key, RBlockingDeque<Object> blockingDeque) {
        while (!Thread.interrupted()) {
            String take = null;
            try {
                take = (String) blockingDeque.take();
            } catch (InterruptedException e) {
                log.info("延时线程中断!");
            }
            if (Objects.nonNull(take)) {
                try {
                    Object o = BeanMapUtils.toBean(fastMQDelayListener.getClass(), take);
                    if (Objects.nonNull(o)) {
                        log.info("开始消费延迟消息,队列键{}:队列值{}", key, take);
                        fastMQDelayListener.onMessage(BeanMapUtils.toBean(fastMQDelayListener.getClass(), take));
                        TimeUnit.MILLISECONDS.sleep(500);
                        log.info("队列键{}:队列值{}，消费成功", key, take);
                    }
                } catch (Throwable e) {
                    log.info("延时队列逻辑执行失败！！");
                }
            }
        }
    }


    private void start() {
        /**
         * 延时队列后台线程,TODO 需要优化成打印日志
         */
        service.schedule(() -> {
            consumeFastMQListeners2();
        }, 5, TimeUnit.MILLISECONDS);

        /**
         * 指定消费组的消费者后台线程
         */
        service.scheduleAtFixedRate(
                () -> {
                    //必须放在同一个线程里面执行，不然会出现重复消费的问题
                    consumeFastMQListeners1();
                    checkPendingList1();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getPullTopicMessagesPeriod(),
                fastMQProperties.getExecutor().getTimeUnit());

        /**
         * 默认消费组的消费者后台线程
         */
        service.scheduleAtFixedRate(
                () -> {
                    consumeFastMQListeners0();
                    checkPendingList0();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getPullDefaultTopicMessagesPeriod(),
                fastMQProperties.getExecutor().getTimeUnit());
    }


    @Override
    public void destroy() throws Exception {
        ExecutorService threadPool = ThreadPoolUtil.QUEUE.getThreadPool();
        threadPool.shutdown();
        if (threadPool.isShutdown()) {
            log.info("fast-mq线程池销毁完成");
        }
    }
}
