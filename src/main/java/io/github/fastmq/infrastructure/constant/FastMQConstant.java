package io.github.fastmq.infrastructure.constant;

public interface FastMQConstant {
    String PREFIX = "fastmq.config";

    String DEFAULT_CONSUMERGROUP = "fast:mq:default_consumer_group";

    String DEFAULT_CONSUMER = "fast:mq:default_consumer";

    String DEFAULT_TOPIC = "fast:mq:default_topic";

    String DEFAULT_DEAD_STREAM = "fast:mq:dead_stream";

    String DEFAULT_DElAY_QUEUE = "fast:mq:default_delay_queue";

    String DEFAULT_DELAY_EXECUTOR = "fast_mq_default_delay_executor";

    Long GLOBAL_MARK = 4194304l;

}
