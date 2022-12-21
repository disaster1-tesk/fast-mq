<p align="center">
    <img width="400" src="https://gitee.com/d__isaster/cornucopia/raw/master/img/fast-mq.png">
</p>


## 🔥特性（Features）
- 🚀 开箱即用
- 🔆 ACK机制
- 📦 异步通信
- 🎨 消息故障修复
- 🌕 死信队列处理
- 🌪️ 消息、消费组、消费者监控管理
- 💫 灵活接口幂等控制
- 🪐 支持redis单机、主从、集群
- ..........（待续）
## 🖥 环境要求 （Environment Required）
- redis v5.0.0+
- springboot v2.6.5
- jdk 1.8+
- ......

## 🌎 整体架构 （Architecture）



## ☀️ 快速开始（Quick Start）

### 生产者 （Producer）
注入FastMQTemplate即可使用
```java 
public class FastMQTemplateTest extends BaseTest {
    @Autowired
    private FastMQTemplate fastMQTemplate;


    @Test
    public void sendMsgTest() {
        HashMap<String, Object> msg = Maps.newHashMap();
        msg.put("name", "disaster");
        msg.put("age", 20);
        fastMQTemplate.sendMsgAsync("disaster_topic", msg);
        fastMQTemplate.sendMsgAsync(FastMQConstant.DEFAULT_TOPIC, msg);
        while (true){

        }
    }
}

```
### 消费者（Consumer）
```java 

/**
 * 不使用注解，则使用框架默认的topic和consumername
 * 
 */
@Service
@Slf4j
public class FastMQConsumerTest implements FastMQListener {
    @Override
    public void onMessage(Object o) {
        log.info("result = {}", o);
    }
}

/**
 * 使用注解可指定topic和consumername，同时还支持接口幂等处理
 * 
 */
@Service
@FastMQMessageListener(idempotent = true,groupName = "disaster",consumeName = "disaster1",topic = "disaster_topic", readSize = 0)
@Slf4j
public class FastMQConsumerAnnotationTest implements FastMQListener{
    @Override
    public void onMessage(Object t) {
        log.info("result = {}", t);
    }
}
```
##  💐 配置 （Configuration）
### Redission配置项
```
## 单机版本
redisson:
  server:
    host: 127.0.0.1
    port: 6379
    database: 0
    deployment: stand_alone
## 主从版本
redisson:
  server:
    host: 127.0.0.1
    port: 6379
    database: 0
    nodes: 127.0.0.1:xxx,127.0.0.1:xxx,127.0.0.1:xxx
    master: mymaster
    deployment: master_slave
## 集群
redisson:
  server:
    host: 127.0.0.1
    port: 6379
    database: 0
    nodes: 127.0.0.1:xxx,127.0.0.1:xxx,127.0.0.1:xxx
    deployment: cluster
```
### FastMQ配置项

```
fastmq:
  redis:
    # 每次拉取数据的量
    fetchMessageSize: 5
    #每次拉取PendingList的大小
    checkPendingListSize: 1000
    #死信门槛（计次器次数）
    deadLetterThreshold: 32
    #是否从头开始订阅消息
    isStartFromHead: true
    #超过了该长度stream前面部分会被持久化（非严格模式——MAXLEN~）
    trimThreshold: 10000
    executor:
      #拉取信息的周期(单位秒)
      checkPendingListsPeriod: 10
      #检查PendingList周期(单位秒)
      pullHealthyMessagesPeriod: 1
      #线程池的核心线程数
      executorCoreSize: 16
      time-unit: seconds
    claim:
      #认领门槛(单位毫秒)
      claimThreshold: 3600
      time-unit: milliseconds
    idle:
      #检查consumer不活跃的门槛（单位秒）
      pendingListIdleThreshold: 10
      time-unit: seconds
```

