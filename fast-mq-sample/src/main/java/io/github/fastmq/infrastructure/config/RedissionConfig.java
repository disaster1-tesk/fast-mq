package io.github.fastmq.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证当自定义redissionClient
 */
@Configuration
public class RedissionConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + "127.0.0.1:6379");
        singleServerConfig.setDatabase(1);
        singleServerConfig.setPassword("123456");
        return Redisson.create(config);
    }
}
