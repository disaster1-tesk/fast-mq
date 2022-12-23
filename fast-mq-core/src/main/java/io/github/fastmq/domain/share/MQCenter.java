package io.github.fastmq.domain.share;

import io.github.fastmq.domain.consumer.delay.FastMQDelayListener;
import io.github.fastmq.domain.consumer.delay.FastMQDelayMessageListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQMessageListener;
import io.github.fastmq.domain.service.FastMQAsyncService;
import io.github.fastmq.domain.service.FastMQService;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.prop.FastMQProperties;
import io.github.fastmq.infrastructure.utils.ThreadPoolUtil;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executor;
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
    @Autowired
    private RedissonClient client;

    /**
     * fastmq配置属性
     */
    @Autowired
    private FastMQProperties fastMQProperties;

    /**
     * 领域服务对象-异步
     */
    @Autowired
    private FastMQAsyncService fastMQAsyncService;

    /**
     * 领域服务对象-同步
     */
    @Autowired
    private FastMQService fastMQService;

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

    /**
     * Instantiates a new Mq center.
     */
    public MQCenter() {
        fastMQListeners0 = new HashSet<>();
        fastMQListeners1 = new HashSet<>();
        fastMQListeners2 = new HashSet<>();
    }

    @PostConstruct
    public void init() {
        service = Executors.newScheduledThreadPool(
                fastMQProperties.getExecutor().getExecutorCoreSize(),
                ThreadFactoryBuilder.create()
                        .setDaemon(true)
                        .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                        .setPriority(6)
                        .get());
        ThreadPoolUtil.init(service);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (fastMQProperties.getEnable()) {
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
            log.info(
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
            String executorName = annotation.executorName();
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            client.getDelayedQueue(blockingDeque);
            Executor bean = applicationContext.getBean(executorName, Executor.class);
            bean.execute(() -> {
                while (true) {
                    try {
                        Object take = blockingDeque.take();
                        log.info("开始消费延迟队列{}:消息内容{}", key, take);
                        fastMQDelayListener.onMessage(take);
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (Throwable throwable) {
                        log.error("fast-mq 延迟队列监测异常中断 {}", throwable.getMessage());
                    }
                }
            });
        }
    }


    private void start() {

        ScheduledExecutorService service = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                ThreadFactoryBuilder.create()
                        .setNameFormat("fast-mq-schedule-pool-%d")
                        .setDaemon(true)
                        .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                        .setPriority(6)
                        .get());
        log.info("stream队列处理线程池fast-mq-schedule-pool初始化完成！！");
        /**
         * 延时队列后台线程
         */
        service.schedule(() -> {
            consumeFastMQListeners2();
        }, 0, TimeUnit.MILLISECONDS);

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
                fastMQProperties.getExecutor().getPullHealthyMessagesPeriod(),
                TimeUnit.SECONDS);

        /**
         * 默认消费组的消费者后台线程
         */
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
