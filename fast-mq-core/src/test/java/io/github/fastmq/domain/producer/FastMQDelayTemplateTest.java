package io.github.fastmq.domain.producer;


import io.github.fastmq.BaseTest;
import io.github.fastmq.domain.producer.delay.FastMQDelayTemplate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

public class FastMQDelayTemplateTest extends BaseTest {
    @Autowired
    private FastMQDelayTemplate fastMQDelayTemplate;

    @Test
    public void sendMsgTest() throws InterruptedException {
        Thread.sleep(2000l);
        fastMQDelayTemplate.msgEnQueue("hello", 20, null, TimeUnit.SECONDS);
        while (true) {
        }
    }
}
