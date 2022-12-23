package io.github.fastmq.domain.consumer.delay;

/**
 * The interface Fast mq delay listener.
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQDelayListener<T> {
    /**
     * On message.
     *
     * @param t the t
     * @throws Throwable the throwable
     */
    void onMessage(T t) throws Throwable;
}
