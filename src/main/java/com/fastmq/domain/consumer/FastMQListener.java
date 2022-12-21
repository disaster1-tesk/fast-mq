package com.fastmq.domain.consumer;

/**
 * The interface Fast mq listener.
 *
 * @param <T> the type parameter
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQListener<T> {
    /**
     * On message.
     *
     * @param t the t
     */
    void onMessage(Object t);
}
