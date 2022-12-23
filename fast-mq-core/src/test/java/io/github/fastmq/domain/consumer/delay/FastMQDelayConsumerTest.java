package io.github.fastmq.domain.consumer.delay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 不使用注解则使用框架默认队列名和线程池
 */
@Service
@Slf4j
public class FastMQDelayConsumerTest implements FastMQDelayListener {
    @Override
    public void onMessage(Object t) throws Throwable {
        log.info("result = {}", t);
    }
}
