package io.github.fastmq.domain.consumer.delay;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@FastMQDelayMessageListener
@Service
@Slf4j
public class FastMQDelayConsumerAnnotationTest implements FastMQDelayListener {
    @Override
    public void onMessage(Object t) throws Throwable {
        log.info("result = {}", t);
    }
}
