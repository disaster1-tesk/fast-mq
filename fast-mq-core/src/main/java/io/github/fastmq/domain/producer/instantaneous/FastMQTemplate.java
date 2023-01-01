package io.github.fastmq.domain.producer.instantaneous;


import org.springframework.lang.NonNull;
import java.util.Map;

/**
 * The interface Fast mq template.
 */
public interface FastMQTemplate {
    /**
     * Send msg async.
     *
     * @param topic the topic
     * @param msg   the msg
     */
    void sendMsgAsync(@NonNull String topic, Map<String, Object> msg);

    /**
     * Send msg async.
     *
     * @param id    the id
     * @param topic the topic
     * @param msg   the msg
     */
    void sendMsgAsync(@NonNull Long id, @NonNull String topic, Map<String, Object> msg);

    /**
     * Send msg async.
     *
     * @param topic the topic
     * @param msg   the msg
     */
    void sendMsgAsync(@NonNull String topic, Object msg);

    /**
     * Send msg async.
     *
     * @param id    the id
     * @param topic the topic
     * @param msg   the msg
     */
    void sendMsgAsync(@NonNull Long id, @NonNull String topic, Object msg);
}
