package io.github.fastmq.domain.producer.instantaneous;


import org.springframework.lang.NonNull;
import java.util.Map;

public interface FastMQTemplate {
    void sendMsgAsync(@NonNull String topic, Map<String, Object> msg);

    void sendMsgAsync(@NonNull Long id, @NonNull String topic, Map<String, Object> msg);

    void sendMsgAsync(@NonNull String topic, Object msg);

    void sendMsgAsync(@NonNull Long id, @NonNull String topic, Object msg);
}
