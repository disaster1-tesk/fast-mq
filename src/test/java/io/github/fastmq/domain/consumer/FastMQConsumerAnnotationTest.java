package io.github.fastmq.domain.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 *
 *
 */
@Service
@FastMQMessageListener(idempotent = true,groupName = "disaster",consumeName = "disaster1",topic = "disaster_topic", readSize = 0)
@Slf4j
public class FastMQConsumerAnnotationTest implements FastMQListener{
    @Override
    public void onMessage(Object t) {
        log.info("result = {}", t);
    }
}
