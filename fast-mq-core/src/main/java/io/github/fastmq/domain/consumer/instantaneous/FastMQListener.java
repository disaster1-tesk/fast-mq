package io.github.fastmq.domain.consumer.instantaneous;


/**
 * The interface Fast mq listener.
 *
 * @param <T> the type parameter
 * @author disaster
 * @version 1.0
 */
public interface FastMQListener<T> {

    /**
     * On message.
     *
     * @param t the t
     * @throws Throwable the throwable
     */
    void onMessage(T t) throws Throwable;
}
