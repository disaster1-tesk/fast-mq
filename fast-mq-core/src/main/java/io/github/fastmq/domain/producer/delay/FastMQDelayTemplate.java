package io.github.fastmq.domain.producer.delay;

import java.util.concurrent.TimeUnit;

/**
 * The interface Fast mq delay template.
 */
public interface FastMQDelayTemplate {
    /**
     * Send msg.
     *
     * @param data       the data
     * @param delayTime  the delay time
     * @param delayQueue the delay queue
     * @param timeUnit   the time unit
     */
    void sendMsg(Object data, long delayTime, String delayQueue, TimeUnit timeUnit);

    /**
     * Send msg.
     *
     * @param data      the data
     * @param delayTime the delay time
     * @param timeUnit  the time unit
     */
    void sendMsg(Object data, long delayTime, TimeUnit timeUnit);

    /**
     * Send msg.
     *
     * @param data      the data
     * @param delayTime the delay time
     */
    void sendMsg(Object data, long delayTime);
}
