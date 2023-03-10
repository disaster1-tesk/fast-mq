package io.github.fastmq.domain.consumer.instantaneous;

import io.github.fastmq.infrastructure.constant.FastMQConstant;

import java.lang.annotation.*;

/**
 * The interface Fast mq message listener.
 *
 * @author disaster
 * @version 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FastMQMessageListener {
    /**
     * 消费组名称
     *
     * @return string
     */
    String groupName() default FastMQConstant.DEFAULT_CONSUMER_GROUP;

    /**
     * 消费组中的消费名称，需配合消费组使用
     *
     * @return string
     */
    String consumeName() default FastMQConstant.DEFAULT_CONSUMER;

    /**
     * 操作是否幂等
     *
     * @return boolean
     */
    boolean idempotent() default false;

    /**
     * 消费主题
     *
     * @return string
     */
    String topic() default FastMQConstant.DEFAULT_TOPIC;

    /**
     * 每次读取的数据量,默认与全局的数量一致
     *
     * @return int
     */
    int readSize() default -1;

}
