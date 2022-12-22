package io.github.fastmq.domain.producer;

import io.github.fastmq.BaseTest;
import io.github.fastmq.domain.producer.instantaneous.FastMQTemplate;
import io.github.fastmq.infrastructure.constant.FastMQConstant;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

public class FastMQTemplateTest extends BaseTest {
    @Autowired
    private FastMQTemplate fastMQTemplate;


    @Test
    public void sendMsgTest() {
        HashMap<String, Object> msg = Maps.newHashMap();
        msg.put("name", "disaster");
        msg.put("age", 20);
        fastMQTemplate.sendMsgAsync("disaster_topic", msg);
        fastMQTemplate.sendMsgAsync("disaster_topic", msg);
        fastMQTemplate.sendMsgAsync(FastMQConstant.DEFAULT_TOPIC, msg);
        while (true){

        }
    }
}
