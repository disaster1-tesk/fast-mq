package io.github.fastmq.domain.consumer.instantaneous;



/**
 * The interface Fast mq listener.
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQListener<T> {

    void onMessage(T t) throws Throwable;
}
