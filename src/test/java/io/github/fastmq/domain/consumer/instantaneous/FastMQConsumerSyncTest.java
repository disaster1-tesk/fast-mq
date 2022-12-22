package io.github.fastmq.domain.consumer.instantaneous;

import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@FastMQMessageListener(idempotent = true,groupName = "disaster",consumeName = "disaster2",topic = "disaster_topic", readSize = 1)
@Slf4j
public class FastMQConsumerSyncTest implements FastMQListener {
    @Override
    public void onMessage(Object t) {
        log.info("result = {}", t);
    }
}
