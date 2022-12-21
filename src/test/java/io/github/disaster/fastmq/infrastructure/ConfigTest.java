package io.github.disaster.fastmq.infrastructure;

import io.github.disaster.fastmq.BaseTest;
import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;


public class ConfigTest extends BaseTest {
    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void fastMQJedisPoolTest() {
        System.out.println(redissonClient);
    }
}
