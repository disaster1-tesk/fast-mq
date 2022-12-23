package io.github.fastmq.domain.consumer.instantaneous;

import lombok.SneakyThrows;

/**
 * The interface Fast mq listener.
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQListener {

    void onMessage(Object t) throws Throwable;
}
