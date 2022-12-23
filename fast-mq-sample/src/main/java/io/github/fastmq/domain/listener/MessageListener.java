package io.github.fastmq.domain.listener;

import io.github.fastmq.domain.SearchEntity;
import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class MessageListener implements FastMQListener<SearchEntity> {
    @Override
    public void onMessage(SearchEntity t) throws Throwable {
        log.info("result = {}",t);
    }
}
