package io.github.fastmq.domain.producer.delay;

import java.util.concurrent.TimeUnit;

public interface FastMQDelayTemplate {
    void sendMsg(Object data, long delayTime, String delayQueue, TimeUnit timeUnit);

    void sendMsg(Object data, long delayTime, TimeUnit timeUnit);

    void sendMsg(Object data, long delayTime);
}
