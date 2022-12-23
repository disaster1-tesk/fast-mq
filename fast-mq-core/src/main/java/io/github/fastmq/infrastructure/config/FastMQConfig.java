package io.github.fastmq.infrastructure.config;

import io.github.fastmq.infrastructure.prop.FastMQProperties;
import io.github.fastmq.infrastructure.prop.RedissonProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import java.util.HashSet;
import java.util.Set;

/**
 * The type Fast mq redis config.
 */
@Configuration
@Slf4j
@ComponentScan("io.github.fastmq")
@EnableConfigurationProperties({FastMQProperties.class, RedissonProperties.class})
public class FastMQConfig {

    private static final String PREFIX = "redis://";

    /**
     * Redisson client redisson client.
     *
     * @param redissonProperties the redisson properties
     * @return the redisson client
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(RedissonProperties.class)
    public RedissonClient redissonClient(RedissonProperties redissonProperties) {
        RedissonProperties.Deployment deployment = redissonProperties.getDeployment();
        switch (deployment) {
            case CLUSTER:
                return getCluster(redissonProperties);
            case SENTINEL:
                return getSentinel(redissonProperties);
            case STAND_ALONE:
                return getStandAloneInstance(redissonProperties);
            case MASTER_SLAVE:
                return getMasterSlaveInstance(redissonProperties);
            default:
                return null;
        }
    }

    /**
     *
     * STAND_ALONE("stand_alone"),
     * MASTER_SLAVE("master_slave"),
     * CLUSTER("cluster"),
     * SENTINEL("sentinel");
     *
     * @return
     */
    private RedissonClient getStandAloneInstance(RedissonProperties redissonProperties) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(PREFIX + redissonProperties.getHost() + ":" + redissonProperties.getPort());
        if (StringUtils.hasText(redissonProperties.getPassword())) {
            singleServerConfig.setPassword(redissonProperties.getPassword());
        }
        if (StringUtils.hasText(redissonProperties.getDatabase())) {
            singleServerConfig.setDatabase(Integer.parseInt(redissonProperties.getDatabase()));
        }
        return Redisson.create(config);
    }

    private RedissonClient getMasterSlaveInstance(RedissonProperties redissonProperties) {
        Config config = new Config();
        String[] adds = redissonProperties.getNodes().split(",");
        Set<String> nodeAddress = new HashSet<>();
        for (int i = 0; i < adds.length; i++) {
            StringBuilder stringBuilder = new StringBuilder(PREFIX);
            adds[i] = stringBuilder.append(adds[i]).toString();
            nodeAddress.add(adds[i]);
        }
        MasterSlaveServersConfig masterSlaveServersConfig = config.useMasterSlaveServers().setMasterAddress(adds[0]);
        masterSlaveServersConfig.setSlaveAddresses(nodeAddress);
        if (StringUtils.hasText(redissonProperties.getPassword())) {
            masterSlaveServersConfig.setPassword(redissonProperties.getPassword());
        }
        if (StringUtils.hasText(redissonProperties.getDatabase())) {
            masterSlaveServersConfig.setDatabase(Integer.parseInt(redissonProperties.getDatabase()));
        }
        return Redisson.create(config);
    }

    private RedissonClient getCluster(RedissonProperties redissonProperties) {
        String[] nodesList = redissonProperties.getNodes().split(",");
        for (int i = 0; i < nodesList.length; i++) {
            StringBuilder stringBuilder = new StringBuilder(PREFIX);
            nodesList[i] = stringBuilder.append(nodesList[i]).toString();
        }
        Config config = new Config();
        ClusterServersConfig clusterServersConfig = config.useClusterServers().addNodeAddress(nodesList);
        if (StringUtils.hasText(redissonProperties.getPassword())) {
            clusterServersConfig.setPassword(redissonProperties.getPassword());
        }
        return Redisson.create(config);
    }

    private RedissonClient getSentinel(RedissonProperties redissonProperties) {
        Config config = new Config();
        String[] adds = redissonProperties.getNodes().split(",");
        for (int i = 0; i < adds.length; i++) {
            StringBuilder stringBuilder = new StringBuilder(PREFIX);
            adds[i] = stringBuilder.append(adds[i]).toString();
        }
        SentinelServersConfig serverConfig = config.useSentinelServers()
                .addSentinelAddress(adds)
                .setMasterName(redissonProperties.getMaster());
        if (StringUtils.hasText(redissonProperties.getPassword())) {
            serverConfig.setSentinelPassword(redissonProperties.getPassword());
        }
        if (StringUtils.hasText(redissonProperties.getDatabase())) {
            serverConfig.setDatabase(Integer.parseInt(redissonProperties.getDatabase()));
        }
        return Redisson.create(config);
    }
}

