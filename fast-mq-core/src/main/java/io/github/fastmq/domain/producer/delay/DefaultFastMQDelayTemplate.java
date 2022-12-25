package io.github.fastmq.domain.producer.delay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
@Primary
@Slf4j
public class DefaultFastMQDelayTemplate implements FastMQDelayTemplate {
    private final RedissonClient client;


    @Autowired
    public DefaultFastMQDelayTemplate(RedissonClient client) {
        this.client = client;
    }

    @Override
    public void sendMsg(Object data, long delayTime, String delayQueue, TimeUnit timeUnit) {
        String key = StringUtils.isEmpty(delayQueue) ? FastMQConstant.DEFAULT_DElAY_QUEUE : FastMQConstant.FAST_MQ_DELAY_PREFIX + delayQueue;
        String value = (data instanceof String) ? (String) data : JSON.toJSONString(data, SerializerFeature.DisableCircularReferenceDetect);
        try {
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            RDelayedQueue<Object> delayedQueue = client.getDelayedQueue(blockingDeque);
            delayedQueue.remove(value);
            delayedQueue.offerAsync(value, delayTime, timeUnit);
            log.info("添加延时队列成功，延迟时间：{},队列键：{}，队列值：{}", timeUnit.toSeconds(delayTime) + "秒", key, value);
        } catch (Exception e) {
            log.error("添加延时队列失败 {}", e.getMessage());
        }
    }

    @Override
    public void sendMsg(Object data, long delayTime, TimeUnit timeUnit) {
        String key = FastMQConstant.DEFAULT_DElAY_QUEUE;
        String value = (data instanceof String) ? (String) data : JSON.toJSONString(data, SerializerFeature.DisableCircularReferenceDetect);
        try {
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            RDelayedQueue<Object> delayedQueue = client.getDelayedQueue(blockingDeque);
            delayedQueue.remove(value);
            delayedQueue.offerAsync(value, delayTime, timeUnit);
            log.info("添加延时队列成功,延迟时间：{},队列键：{}，队列值：{}", timeUnit.toSeconds(delayTime) + "秒", key, value);
        } catch (Exception e) {
            log.error("添加延时队列失败 {}", e.getMessage());
        }
    }

    @Override
    public void sendMsg(Object data, long delayTime) {
        String key = FastMQConstant.DEFAULT_DElAY_QUEUE;
        String value = (data instanceof String) ? (String) data : JSON.toJSONString(data, SerializerFeature.DisableCircularReferenceDetect);
        try {
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            RDelayedQueue<Object> delayedQueue = client.getDelayedQueue(blockingDeque);
            delayedQueue.remove(value);
            delayedQueue.offerAsync(value, delayTime, TimeUnit.SECONDS);
            log.info("添加延时队列成功，延迟时间：{},队列键：{}，队列值：{}", delayTime + "秒", key, value);
        } catch (Exception e) {
            log.error("添加延时队列失败 {}", e.getMessage());
        }
    }
}
