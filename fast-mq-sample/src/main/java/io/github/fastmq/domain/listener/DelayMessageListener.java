package io.github.fastmq.domain.listener;

import io.github.fastmq.domain.SearchEntity;
import io.github.fastmq.domain.consumer.delay.FastMQDelayListener;
import io.github.fastmq.domain.consumer.delay.FastMQDelayMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@FastMQDelayMessageListener
@Slf4j
public class DelayMessageListener implements FastMQDelayListener<SearchEntity> {
    @Override
    public void onMessage(SearchEntity searchEntity) throws Throwable {
        log.info("result = {}",searchEntity);
    }
}
