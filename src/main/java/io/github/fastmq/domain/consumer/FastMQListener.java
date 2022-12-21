package io.github.fastmq.domain.consumer;

import lombok.SneakyThrows;

/**
 * The interface Fast mq listener.
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQListener {
    /**
     * On message.
     *
     * @param t the t
     */

    void onMessage(Object t);
}
