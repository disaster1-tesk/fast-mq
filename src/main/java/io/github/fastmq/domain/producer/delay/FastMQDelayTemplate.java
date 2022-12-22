package io.github.fastmq.domain.producer.delay;

import java.util.concurrent.TimeUnit;

public interface FastMQDelayTemplate {
    void msgEnQueue(Object data, long delayTime, String delayQueue, TimeUnit timeUnit);
}
