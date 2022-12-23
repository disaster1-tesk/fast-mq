package io.github.fastmq.domain.service;

import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQMessageListener;
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
public interface FastMQAsyncService extends MQService {
    /**
     * 异步消费空闲超时信息进行重传
     */
    void consumeIdleMessagesAsync(Set<StreamMessageId> idleIds, FastMQListener fastMQListener);

    /**
     * 异步检查消费一直消费失败的信息（达到最大重试次数后会加入死信队列、通知管理员）
     */
    void consumeDeadLetterMessagesAsync(Set<StreamMessageId> deadLetterIds, FastMQListener fastMQListener);

    /**
     * 异步认领空闲过久的消息
     */
    void claimIdleConsumerAsync(FastMQListener fastMQListener);

    /**
     * 异步消费消息
     */
    void consumeMessagesAsync(Map<StreamMessageId, Map<Object, Object>> res, FastMQListener data, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener);


    /**
     * 处理异常消息方法
     */
    void checkPendingListAsync(FastMQListener fastMQListener);


    /**
     * 消费者消费
     */
    void consumeFastMQListenersAsync(FastMQListener fastMQListener);
}
