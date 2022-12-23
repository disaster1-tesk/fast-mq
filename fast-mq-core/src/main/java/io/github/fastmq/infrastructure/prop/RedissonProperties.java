package io.github.fastmq.infrastructure.prop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "redisson.server")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedissonProperties {
    /**
     * redis主机地址
     */
    private String host;

    /**
     * PORT
     */
    private String port;

    /**
     * 连接类型，支持stand-alone-单机节点，sentinel-哨兵，cluster-集群，master-slave-主从
     */
    private Deployment deployment = Deployment.STAND_ALONE;

    /**
     * redis 连接密码
     */
    private String password;

    /**
     * 选取那个数据库
     */
    private String database;

    /**
     * 节点信息
     */
    private String nodes;

    /**
     * 主节点
     */
    private String master;


    public RedissonProperties setPassword(String password) {
        this.password = password;
        return this;
    }

    public RedissonProperties setDatabase(String database) {
        this.database = database;
        return this;
    }

    public enum Deployment {
        STAND_ALONE("stand_alone"),
        MASTER_SLAVE("master_slave"),
        CLUSTER("cluster"),
        SENTINEL("sentinel");

        private String value;

        Deployment(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }


}
