package io.github.fastmq.domain.share;

import io.github.fastmq.domain.consumer.FastMQListener;
import io.github.fastmq.domain.consumer.FastMQMessageListener;
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

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

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

    /**
     * Instantiates a new Mq center.
     */
    public MQCenter() {
        fastMQListeners0 = new HashSet<>();
        fastMQListeners1 = new HashSet<>();
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
            stream.createGroupAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                    : fastMQMessageListener.groupName(), id);
            log.info("消费组{}创建成功", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
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


    private void start() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                ThreadFactoryBuilder.create()
                        .setDaemon(true)
                        .setUncaughtExceptionHandler((t, e) -> log.debug("线程:{},异常{}", t.getName(), e.getMessage()))
                        .setPriority(6)
                        .get());

        service.scheduleAtFixedRate(
                () -> {
                    //必须放在同一个线程里面执行，不然会出现重复消费的问题
                    consumeFastMQListeners1();
                    checkPendingList1();
                },
                fastMQProperties.getExecutor().getInitialDelay(),
                fastMQProperties.getExecutor().getPullHealthyMessagesPeriod(),
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
