package io.github.fastmq.domain.service.impl;

import io.github.fastmq.domain.consumer.instantaneous.FastMQListener;
import io.github.fastmq.domain.consumer.instantaneous.FastMQMessageListener;
import io.github.fastmq.domain.service.FastMQService;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import io.github.fastmq.infrastructure.prop.FastMQProperties;
import io.github.fastmq.infrastructure.utils.BeanMapUtils;
import io.github.fastmq.infrastructure.utils.ThreadPoolUtil;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Primary
@Slf4j
public class FastMQServiceImpl implements FastMQService {
    /**
     * redis客户端
     */
    private final RedissonClient client;
    /**
     * 死信流
     */
    private RStream<Object, Object> deadStream;
    /**
     * 配置对象
     */
    private final FastMQProperties fastMQProperties;

    @Autowired
    public FastMQServiceImpl(RedissonClient client, FastMQProperties fastMQProperties) {
        this.client = client;
        this.fastMQProperties = fastMQProperties;
    }

    @Override
    public void consumeIdleMessages(Set<StreamMessageId> idleIds, FastMQListener fastMQListener) {
        if (CollectionUtils.isEmpty(idleIds)) return;

        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //执行XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds]  [NOACK] STREAMS key [key ...] ID [ID ...] 读取当前消费组的所有的消息
        //可思考一下为什么这里不用range -- 关键字（group）

        Map<StreamMessageId, Map<Object, Object>> messages = stream.readGroup(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP : fastMQMessageListener.groupName(),
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER : fastMQMessageListener.consumeName(), StreamMessageId.ALL);

        //过滤出需要消息重传的数据
        messages = messages.entrySet().stream().
                filter(row -> !idleIds.contains(row.getKey())).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!CollectionUtils.isEmpty(messages)) {
            log.info("消费组 = {},消费名称 = {},id = {},开始重新消费数据: {}", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                            : fastMQMessageListener.groupName(),
                    Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                            : fastMQMessageListener.consumeName()
                    , messages.keySet(), messages);
        }
        consumeMessages(messages, fastMQListener, stream, fastMQMessageListener);

    }

    @Override
    public void consumeDeadLetterMessages(Set<StreamMessageId> deadLetterIds, FastMQListener fastMQListener) {
        if (CollectionUtils.isEmpty(deadLetterIds)) return;

        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //获取死信流
        deadStream = client.getStream(FastMQConstant.DEFAULT_DEAD_STREAM);
        for (StreamMessageId id :
                deadLetterIds) {
            //通过XRANGE streamName 1624516905844-0 1624516905844-0  通过ID获取每一条消息的详细内容
            Map<StreamMessageId, Map<Object, Object>> range = stream.range(id, id);

            if (!CollectionUtils.isEmpty(range)) {
                Map<Object, Object> map = range.get(id);

                //XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold [LIMIT count]] *|ID field value [field value ...]
                //插入数据
                deadStream.add(StreamMessageId.AUTO_GENERATED, StreamAddArgs.entries(map));
                stream.remove(id);
                long ack = stream.ack(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                        : fastMQMessageListener.groupName(), id);
                if (ack == 1) {
                    log.info("消费组 = {},消费名称 = {},id = {} 死信移除成功", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                                    : fastMQMessageListener.groupName(),
                            Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                                    : fastMQMessageListener.consumeName()
                            , id);
                } else {
                    log.info("消费组 = {},消费名称 = {},id = {} 死信移除失败", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                                    : fastMQMessageListener.groupName(),
                            Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                                    : fastMQMessageListener.consumeName()
                            , id);
                }
            }
        }
        //TODO 通知管理员，通过后续的管理员页面进行相关死信队列的处理
    }

    @Override
    public void claimIdleConsumer(FastMQListener fastMQListener) {
        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //XINFO [CONSUMERS key groupname] [GROUPS key] [STREAM key] [HELP]命令获取其他信息
        RFuture<PendingResult> infoAsync = stream.getPendingInfoAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                : fastMQMessageListener.groupName());
        PendingResult res = stream.getPendingInfo(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                : fastMQMessageListener.groupName());
        //获取组内的所有消费者的名称
        Map<String, Long> consumerNames = res.getConsumerNames();
        if (consumerNames.size() <= 1) return;

        //异步执行XPENDING key group [[IDLE min-idle-time] start end count  [consumer]]
        List<PendingEntry> pendingEntries = stream.listPending(
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                        : fastMQMessageListener.groupName(),
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                        : fastMQMessageListener.consumeName(),
                StreamMessageId.MIN,
                StreamMessageId.MAX,
                fastMQProperties.getClaim().getClaimThreshold(),
                fastMQProperties.getClaim().getTimeUnit(),
                fastMQProperties.getPullPendingListSize());
        pendingEntries = pendingEntries.stream()
                .filter(entry -> entry.getLastTimeDelivered() >= fastMQProperties.getDeadLetterThreshold())
                .collect(Collectors.toList());

        //优化点：是否采用XAUTOCLAIM key group consumer min-idle-time start [COUNT count] [JUSTID]指令
        //随机获取一个消费者
        String randConsumerName = getRandConsumerName(consumerNames, fastMQMessageListener);

        for (PendingEntry entry :
                pendingEntries) {
            StreamMessageId id = entry.getId();
            //XCLAIM key  group consumer min-idle-time ID [ID ...] [IDLE ms]  [TIME ms-unix-time] [RETRYCOUNT count] [FORCE] [JUSTID]
            //转移消息
            stream.claim(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                    : fastMQMessageListener.groupName(), randConsumerName, fastMQProperties.getClaim().getClaimThreshold(), fastMQProperties.getClaim().getTimeUnit(), id, id);
        }


    }

    @Override
    public void consumeMessages(Map<StreamMessageId, Map<Object, Object>> res, FastMQListener data, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener) {
        for (Map.Entry<StreamMessageId, Map<Object, Object>> entry :
                res.entrySet()) {
            consumeMessage(entry.getKey(), entry.getValue(), data, stream, fastMQMessageListener);
        }
    }

    @Override
    public void checkPendingList(FastMQListener fastMQListener) {
        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);

        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //异步执行XPENDING key group [[IDLE min-idle-time] start end count  [consumer]] 获取某个消费者组中的未处理消息的相关信息
        List<PendingEntry> pendingEntries = stream.listPending(
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                        : fastMQMessageListener.groupName(),
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                        : fastMQMessageListener.consumeName(),
                StreamMessageId.MIN,
                StreamMessageId.MAX,
                fastMQProperties.getIdle().getPendingListIdleThreshold(),
                fastMQProperties.getIdle().getTimeUnit(),
                fastMQProperties.getPullPendingListSize());

        Set<StreamMessageId> deadLetterIds = new HashSet<>();
        Set<StreamMessageId> idleIds = new HashSet<>();
        //获取死信消息
        for (PendingEntry entry :
                pendingEntries) {
            long cnt = entry.getLastTimeDelivered();
            //判断是否超过fastMQProperties中指定的时间
            if (cnt >= this.fastMQProperties.getDeadLetterThreshold()) {
                //加入死信队列中
                deadLetterIds.add(entry.getId());
            } else {
                //否则加入超时队列中
                idleIds.add(entry.getId());
            }
        }
        //处理超时队列逻辑：目前仅支持随机消费者重新消费
        consumeIdleMessages(idleIds, fastMQListener);
        consumeDeadLetterMessages(deadLetterIds, fastMQListener);
        claimIdleConsumer(fastMQListener);
    }

    @Override
    public void consumeFastMQListeners(FastMQListener fastMQListener) {
        FastMQMessageListener fastMQMessageListener = AnnotationUtils.findAnnotation(fastMQListener.getClass(), FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //执行XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds]  [NOACK] STREAMS key [key ...] ID [ID ...]
        Map<StreamMessageId, Map<Object, Object>> res = stream.readGroup(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                        : fastMQMessageListener.groupName(),
                Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                        : fastMQMessageListener.consumeName(),
                Objects.nonNull(fastMQMessageListener) && fastMQMessageListener.readSize() >= -1 ? fastMQMessageListener.readSize(): fastMQProperties.getFetchMessageSize(),
                StreamMessageId.NEVER_DELIVERED);


        //执行的后续操作
        consumeMessages(res, fastMQListener, stream, fastMQMessageListener);

    }

    private void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap, FastMQListener fastMQListener, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener) {
        //如果操作是幂等的则不需要加分布式锁
        if (Objects.nonNull(fastMQMessageListener) && fastMQMessageListener.idempotent()) {
            ThreadPoolUtil.run(() -> {
                //消费端逻辑回调
                _onMessage(id, dtoMap, fastMQListener, stream, fastMQMessageListener);
            });
        } else {
            ThreadPoolUtil.run(() -> {
                String lockName = Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP + ":" + id.toString()
                        : fastMQMessageListener.groupName() + ":" + id.toString();
                RLock lock = client.getLock(lockName);
                try {
                    //分布式锁保证分布式环境下的原子性
                    boolean b = lock.tryLock(100, 10, TimeUnit.SECONDS);
                    //通过bucket桶表示某id消息已经被消费
                    if (b) {
                        RBucket<String> bucket = client.getBucket("bucket:" + lockName);
                        String bucketRes = bucket.get();
                        if (StringUtil.isNullOrEmpty(bucketRes)) {
                            //消费端逻辑回调
                            _onMessage(id, dtoMap, fastMQListener, stream, fastMQMessageListener);
                            bucket.set("consumed");
                            bucket.expire(30, TimeUnit.MINUTES);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //解锁
                    lock.unlockAsync();
                }
            });
        }

    }

    private void _onMessage(StreamMessageId id, Map<Object, Object> dtoMap, FastMQListener fastMQListener, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener) {
        try {
            fastMQListener.onMessage(BeanMapUtils.toBean(fastMQListener.getClass(),dtoMap));
            if (Objects.isNull(fastMQMessageListener)) {
                //ACK机制，比pubsub优秀
                stream.ackAsync(FastMQConstant.DEFAULT_CONSUMER_GROUP, id).thenAccept(ack -> {
                    printAckLog(id, fastMQMessageListener, ack);
                });
            } else {
                stream.ackAsync(fastMQMessageListener.groupName(), id).thenAccept(ack -> {
                    printAckLog(id, fastMQMessageListener, ack);
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
            log.error("消费组 = {}，消费名称 = {}，id = {} 逻辑回调处理异常，消息消费失败！！",Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                            : fastMQMessageListener.groupName(),
                    Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                            : fastMQMessageListener.consumeName()
                    , id);
        }
    }

    private void printAckLog(StreamMessageId id, FastMQMessageListener fastMQMessageListener, long ack) {
        if (ack == 1) {
            log.info("ACK成功,消费组 = {}，消费名称 = {}，id = {} 成功消费", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                            : fastMQMessageListener.groupName(),
                    Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                            : fastMQMessageListener.consumeName()
                    , id);
        } else {
            log.info("ACK失败,消费组 = {}，消费名称 = {}，id = {} 消费失败", Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER_GROUP
                            : fastMQMessageListener.groupName(),
                    Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                            : fastMQMessageListener.consumeName()
                    , id);
        }
    }

}
