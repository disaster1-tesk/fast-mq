package io.github.fastmq.domain.consumer.delay;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 使用注解可自定义队列名称与线程池
 */
@FastMQDelayMessageListener(queueName = "test",executorName = "test_executor")
@Service
@Slf4j
public class FastMQDelayConsumerAnnotationTest implements FastMQDelayListener {
    @Override
    public void onMessage(Object t) throws Throwable {
        log.info("result = {}", t);
    }
}
