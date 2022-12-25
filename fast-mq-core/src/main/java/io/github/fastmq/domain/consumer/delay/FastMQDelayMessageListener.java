package io.github.fastmq.domain.consumer.delay;

import io.github.fastmq.infrastructure.constant.FastMQConstant;

import java.lang.annotation.*;

/**
 * The interface R queue listener.
 *
 * @author disaster
 * @version 1.0
 */
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FastMQDelayMessageListener {
    /**
     * Queue name string.
     *
     * @return the string
     */
    String queueName() default FastMQConstant.DEFAULT_DElAY_QUEUE;


    /**
     * Executor name string.
     *
     * @return the string
     */
    String executorName() default "";

}
