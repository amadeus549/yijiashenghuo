package com.yijia.config;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private int port;
    @Value("${spring.redis.password:}")
    private String password;
    @Value("${spring.redis.database:0}")
    private int database;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer()
                // 跟 spring.redis 保持一致，不再写死地址；换 Redis 只改 yaml
                .setAddress("redis://" + host + ":" + port)
                .setPassword(StrUtil.isBlank(password) ? null : password)
                .setDatabase(database);
        try {
            return Redisson.create(config);
        } catch (Exception e) {
            // Redisson 默认会抛一大串 Netty 堆栈，这里换成一句能直接照做的提示
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("无法连接 Redis（{}:{}）：{}", host, port, reason);
            throw new IllegalStateException(String.format(
                    "启动失败：连不上 Redis %s:%d。请依次确认 1) Redis 已启动；2) spring.redis.password 正确；"
                            + "3) 本机能访问该地址（虚拟机/容器需放行 6379）；4) 若只想本地跑，把 host 改成 127.0.0.1",
                    host, port), e);
        }
    }
}
