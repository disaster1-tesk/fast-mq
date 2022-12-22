package io.github.fastmq.domain.consumer.instantaneous;

import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 *
 */
@Service
@Slf4j
public class FastMQConsumerTest implements FastMQListener {
    @Override
    public void onMessage(Object o) {
        log.info("result = {}", o);
    }
}
