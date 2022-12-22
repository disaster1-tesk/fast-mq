package io.github.fastmq.domain.producer.delay;

import com.alibaba.fastjson.JSON;
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
    public void msgEnQueue(Object data, long delayTime, String delayQueue, TimeUnit timeUnit) {
        String key = StringUtils.isEmpty(delayQueue) ? FastMQConstant.DEFAULT_DElAY_QUEUE : delayQueue;
        String value = (data instanceof String) ? (String) data : JSON.toJSONString(data);
        try {
            RBlockingDeque<Object> blockingDeque = client.getBlockingDeque(key);
            RDelayedQueue<Object> delayedQueue = client.getDelayedQueue(blockingDeque);
            //如果延时队列中原先存在这条消息，remove可以删除延时队列中的这条消息
            //如果多次发送相同的消息，先remove再offer，只有最后一条会被延时消费，延时时间以最后一条的发送时间开始延时
            delayedQueue.remove(value);
            delayedQueue.offer(value, delayTime, timeUnit);
            log.info("添加延时队列成功,队列键：{}，队列值：{}，延迟时间：{}", key, value, timeUnit.toSeconds(delayTime) + "秒");
        } catch (Exception e) {
            log.error("添加延时队列失败 {}", e.getMessage());
        }
    }
}
