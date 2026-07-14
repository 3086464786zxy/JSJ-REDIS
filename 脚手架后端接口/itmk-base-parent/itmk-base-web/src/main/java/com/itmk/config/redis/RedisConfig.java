package com.itmk.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 统一使用字符串序列化。
 *
 * 业务对象由 RedisService 显式转换为普通 JSON，避免 Java 类型信息写入 Redis，
 * 从根源上规避类升级、反序列化白名单和密码字段误缓存等问题。
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
