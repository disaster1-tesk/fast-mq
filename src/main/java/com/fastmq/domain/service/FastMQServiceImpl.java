package com.fastmq.domain.service;

import com.fastmq.domain.consumer.FastMQListener;
import com.fastmq.domain.consumer.FastMQMessageListener;
import com.fastmq.infrastructure.constant.FastMQConstant;
import com.fastmq.infrastructure.prop.FastMQProperties;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
    public void consumeIdleMessagesAsync(Set<StreamMessageId> idleIds, FastMQListener<?> fastMQListener) {
        if (CollectionUtils.isEmpty(idleIds)) return;

        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //异步执行XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds]  [NOACK] STREAMS key [key ...] ID [ID ...] 读取当前消费组的所有的消息
        //可思考一下为什么这里不用range -- 关键字（group）
        RFuture<Map<StreamMessageId, Map<Object, Object>>> future =
                stream.readGroupAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP : fastMQMessageListener.groupName(),
                        Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER : fastMQMessageListener.consumeName(),
                        StreamMessageId.ALL);

        future.thenAccept(res -> {
                    //过滤出需要消息重传的数据
                    Map<StreamMessageId, Map<Object, Object>> messages = res.entrySet().stream().
                            filter(row -> idleIds.contains(row.getKey())).
                            collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    log.info("开始重新消费数据{}", idleIds);
                    consumeMessagesAsync(messages, fastMQListener, stream, fastMQMessageListener);
                }
        ).exceptionally(exception -> {
            log.info(exception.getMessage());
            return null;
        });
    }

    @Override
    public void consumeDeadLetterMessagesAsync(Set<StreamMessageId> deadLetterIds, FastMQListener<?> fastMQListener) {
        if (CollectionUtils.isEmpty(deadLetterIds)) return;

        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());
        //获取死信流
        deadStream = client.getStream(FastMQConstant.DEAD_STREAM);
        for (StreamMessageId id :
                deadLetterIds) {
            //通过XRANGE streamName 1624516905844-0 1624516905844-0  通过ID获取每一条消息的详细内容
            RFuture<Map<StreamMessageId, Map<Object, Object>>> future = stream.rangeAsync(id, id);
            future.thenAccept(range -> {
                        if (range != null && range.size() != 0) {
                            Map<Object, Object> map = range.get(id);
                            //XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold [LIMIT count]] *|ID field value [field value ...]
                            //插入数据
                            RFuture<Void> addAsync = deadStream.addAsync(StreamMessageId.AUTO_GENERATED, StreamAddArgs.entries(map));
                            addAsync.thenAccept(res -> {
                                        stream.removeAsync(id).thenAccept(aLong -> {
                                            log.info("移除id = {} 的rangeAsync成功", aLong);
                                        });
                                        stream.ackAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                                                        : fastMQMessageListener.groupName(), id)
                                                .thenAccept(aLong -> {
                                                    log.info("ACK成功,,id = {} 已经成功插入死信流中", aLong);
                                                });

                                    }
                            ).exceptionally(exception -> {
                                exception.printStackTrace();
                                return null;
                            });
                        }
                    }
            ).exceptionally(exception -> {
                exception.printStackTrace();
                return null;
            });
        }
        //TODO 通知管理员，通过后续的管理员页面进行相关死信队列的处理
    }

    @Override
    public void claimIdleConsumerAsync(FastMQListener<?> fastMQListener) {
        FastMQMessageListener fastMQMessageListener = fastMQListener.getClass().getAnnotation(FastMQMessageListener.class);
        RStream<Object, Object> stream = client.getStream(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_TOPIC
                : fastMQMessageListener.topic());

        //XINFO [CONSUMERS key groupname] [GROUPS key] [STREAM key] [HELP]命令获取其他信息
        RFuture<PendingResult> infoAsync = stream.getPendingInfoAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                : fastMQMessageListener.groupName());

        infoAsync.thenAccept(res -> {
                    //获取组内的所有消费者的名称
                    Map<String, Long> consumerNames = res.getConsumerNames();
                    if (consumerNames.size() <= 1) return;

                    //异步执行XPENDING key group [[IDLE min-idle-time] start end count  [consumer]]
                    RFuture<List<PendingEntry>> future = stream.listPendingAsync(
                            Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                                    : fastMQMessageListener.groupName(),
                            Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                                    : fastMQMessageListener.consumeName(),
                            StreamMessageId.MIN,
                            StreamMessageId.MAX,
                            fastMQProperties.getClaim().getClaimThreshold(),
                            fastMQProperties.getClaim().getTimeUnit(),
                            fastMQProperties.getCheckPendingListSize());

                    future.thenAccept(pendingEntryList -> {
                                List<PendingEntry> pendingEntries = pendingEntryList.stream()
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
                                    stream.claimAsync(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP
                                            : fastMQMessageListener.groupName(), randConsumerName, fastMQProperties.getClaim().getClaimThreshold(), fastMQProperties.getClaim().getTimeUnit(), id, id);
                                }
                            }
                    ).exceptionally(exception -> {
                        log.info("listPendingAsync Error:{}", exception.getMessage());
                        return null;
                    });
                }
        ).exceptionally(ex -> {
            log.info("Claim Error:{}", ex.getMessage());
            return null;
        });
    }

    @Override
    public void consumeMessagesAsync(Map<StreamMessageId, Map<Object, Object>> res, FastMQListener<?> data, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener) {
        for (Map.Entry<StreamMessageId, Map<Object, Object>> entry :
                res.entrySet()) {
            consumeMessage(entry.getKey(), entry.getValue(), (FastMQListener<Object>) data, stream, fastMQMessageListener);
        }
    }

    private void consumeMessage(StreamMessageId id, Map<Object, Object> dtoMap, FastMQListener<Object> fastMQListener, RStream<Object, Object> stream, FastMQMessageListener fastMQMessageListener) {
        String lockName = Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMERGROUP + ":" + id.toString()
                : fastMQMessageListener.groupName() + ":" + id.toString();
        RLock lock = client.getLock(lockName);
        //如果操作是幂等的则不需要加分布式锁
        if (Objects.nonNull(fastMQMessageListener) && fastMQMessageListener.idempotent()) {
            //消费端逻辑回调
            fastMQListener.onMessage(dtoMap);
            if (Objects.isNull(fastMQMessageListener)) {
                //ACK机制，比pubsub优秀
                stream.ackAsync(FastMQConstant.DEFAULT_CONSUMERGROUP, id).thenAccept(aLong -> {
                    log.info("ACK成功,id = {} 已经成功成功消费", aLong);
                });
            } else {
                stream.ackAsync(fastMQMessageListener.groupName(), id).thenAccept(aLong -> {
                    log.info("ACK成功,id = {} 已经成功成功消费", aLong);
                });
            }
        } else {
            try {
                //分布式锁保证分布式环境下的原子性
                RFuture<Boolean> tryAsync = lock.tryLockAsync(100, 10, TimeUnit.SECONDS);
                tryAsync.thenAccept(tmp -> {
                            //通过bucket桶表示某id消息已经被消费
                            RBucket<String> bucket = client.getBucket("bucket:" + lockName);
                            bucket.getAsync().thenAccept(bucketRes -> {
                                        if (StringUtil.isNullOrEmpty(bucketRes)) {
                                            //消费端逻辑回调
                                            fastMQListener.onMessage(dtoMap);
                                            if (Objects.isNull(fastMQMessageListener)) {
                                                stream.ackAsync(FastMQConstant.DEFAULT_CONSUMERGROUP, id).thenAccept(aLong -> {
                                                    log.info("ACK成功,id = {} 已经成功成功消费", aLong);
                                                });
                                            } else {
                                                stream.ackAsync(fastMQMessageListener.groupName(), id).thenAccept(aLong -> {
                                                    log.info("ACK成功,id = {} 已经成功成功消费", aLong);
                                                });
                                            }
                                            bucket.setAsync("consumed");
                                            bucket.expireAsync(30, TimeUnit.MINUTES);
                                        }
                                    }
                            ).exceptionally(ex -> {
                                ex.printStackTrace();
                                return null;
                            });
                        }
                ).exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
            } finally {
                //解锁
                lock.unlockAsync();
            }
        }

    }


    private String getRandConsumerName(Map<String, Long> consumerNames, FastMQMessageListener fastMQMessageListener) {
        //过滤掉原有的consumerName
        List<Map.Entry<String, Long>> entries = consumerNames.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(Objects.isNull(fastMQMessageListener) ? FastMQConstant.DEFAULT_CONSUMER
                        : fastMQMessageListener.consumeName()))
                .collect(Collectors.toList());
        //随机一个消费者进行消费：后期可以采用多策略去完成此工作
        Random rand = new Random();
        int i = rand.nextInt(entries.size());
        return entries.get(i).getKey();
    }

}
