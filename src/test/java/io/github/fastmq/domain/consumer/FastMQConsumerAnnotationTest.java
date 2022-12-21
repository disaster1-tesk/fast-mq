package io.github.fastmq.domain.consumer;

import lombok.SneakyThrows;
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
    @SneakyThrows
    public void onMessage(Object t){
        Thread.sleep(21000);
        log.info("result = {}", t);
    }
}
