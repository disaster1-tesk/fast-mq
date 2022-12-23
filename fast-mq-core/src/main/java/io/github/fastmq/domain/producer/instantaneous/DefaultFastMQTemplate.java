package io.github.fastmq.domain.producer.instantaneous;

import cn.hutool.core.bean.BeanUtil;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.prop.FastMQProperties;
import io.github.fastmq.infrastructure.utils.BeanMapUtils;
import io.github.fastmq.infrastructure.utils.SequenceUtil;
import io.github.fastmq.infrastructure.utils.SystemClock;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RFuture;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.TrimStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@Primary
public class DefaultFastMQTemplate implements FastMQTemplate {
    private final RedissonClient client;
    private final FastMQProperties properties;

    @Autowired
    public DefaultFastMQTemplate(RedissonClient client, FastMQProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void sendMsgAsync(String topic, Map<String, Object> msg) {
        SequenceUtil sq = SequenceUtil.getInstance();
        long nextId = sq.nextId();
        _sendMsgAsync(topic, msg, nextId, true);
    }


    @Override
    public void sendMsgAsync(Long id, String topic, Map<String, Object> msg) {
        _sendMsgAsync(topic, msg, id, false);
    }

    @Override
    public void sendMsgAsync(String topic, Object msg) {
        SequenceUtil sq = SequenceUtil.getInstance();
        long nextId = sq.nextId();
        _sendMsgAsync(topic, msg, nextId, true);
    }

    @Override
    public void sendMsgAsync(Long id, String topic, Object msg) {
        _sendMsgAsync(topic, msg, id, false);
    }


    private void _sendMsgAsync(String topic, Map<String, Object> msg, long nextId, Boolean isSequence) {
        StreamMessageId msgId = isSequence ? new StreamMessageId(nextId / FastMQConstant.GLOBAL_MARK, nextId % FastMQConstant.GLOBAL_MARK) : new StreamMessageId(SystemClock.now(), nextId);
        _sendMsg(topic, msgId, msg);
    }


    @SneakyThrows
    private void _sendMsgAsync(String topic, Object obj, long nextId, Boolean isSequence) {
        StreamMessageId msgId = isSequence ? new StreamMessageId(nextId / FastMQConstant.GLOBAL_MARK, nextId % FastMQConstant.GLOBAL_MARK) : new StreamMessageId(SystemClock.now(), nextId);
        Map<String, Object> msg = BeanUtil.beanToMap(obj, false, false);
        _sendMsg(topic, msgId, msg);
    }


    private void _sendMsg(String topic, StreamMessageId msgId, Map<String, Object> msg) {
        RStream stream = client.getStream(topic);
        //异步执行ADD topic MAXLEN ~ 1000 * ... entry fields here ... 命令
        RFuture<Void> sendMessageFuture = stream.addAsync(msgId, StreamAddArgs.entries(msg).trim(TrimStrategy.MAXLEN, properties.getTrimThreshold()));
        //异常处理和日志打印工作
        sendMessageFuture
                .thenAccept(res -> log.info("主题 : {} 添加消息: {} 成功,id = {}", topic, msg, msgId))
                .exceptionally(exception -> {
                    log.info("主题 : {} 添加消息:{} 错误, 异常信息为:{}",
                            topic,
                            msg,
                            exception.getMessage());
                    return null;
                });

    }
}
