package com.fastmq.domain.service;

import com.fastmq.domain.consumer.FastMQListener;
import com.fastmq.domain.consumer.FastMQMessageListener;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;

import java.util.Map;
import java.util.Set;

/**
 * The interface Fast mq service.
 *
 * @author disaster
 * @version 1.0
 */
public interface FastMQService {
    /**
     * 异步消费空闲超时信息进行重传
     *
     * @param idleIds        超时列表
     * @param fastMQListener the fast mq listener
     */
    void consumeIdleMessagesAsync(Set<StreamMessageId> idleIds, FastMQListener<?> fastMQListener);

    /**
     * 异步检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     *
     * @param deadLetterIds  死信ID列表
     * @param fastMQListener the fast mq listener
     */
    void consumeDeadLetterMessagesAsync(Set<StreamMessageId> deadLetterIds, FastMQListener<?> fastMQListener);

    /**
     * 异步认领空闲过久的消息
     *
     * @param fastMQListener the fast mq listener
     */
    void claimIdleConsumerAsync(FastMQListener<?> fastMQListener);

    /**
     * 异步消费消息
     *
     * @param res
     * @param data
     * @param stream
     * @param fastMQMessageListener
     */
    void consumeMessagesAsync(Map<StreamMessageId, Map<Object, Object>> res, FastMQListener<?> data, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener);
}
