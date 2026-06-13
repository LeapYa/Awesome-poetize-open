package com.ld.poetry.config;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.DefaultBaseTypeLimitingValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * 
 * 注意：从 Jackson 2.x (com.fasterxml) 升级到 Jackson 3.x (tools.jackson) 后，
 * 默认类型标识的包名已变更，旧缓存数据无法反序列化。
 * 升级后首次启动前请清空 Redis 数据库（FLUSHDB），否则读取旧缓存会报错。
 * 
 * @author LeapYa
 * @since 2024-12-20
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    @Value("${spring.data.redis.password:123456}")
    private String redisPassword;
    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);
        if (!redisPassword.isEmpty()) config.setPassword(redisPassword);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        tools.jackson.databind.jsontype.PolymorphicTypeValidator ptv = tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        JsonMapper objectMapper = JsonMapper.builder()
                .activateDefaultTyping(ptv, tools.jackson.databind.DefaultTyping.NON_FINAL)
                .build();

        RedisSerializer<Object> jsonSerializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                if (value == null) return new byte[0];
                try { return objectMapper.writeValueAsBytes(value); }
                catch (Exception e) { throw new org.springframework.data.redis.serializer.SerializationException("无法序列化", e); }
            }
            @Override
            public Object deserialize(byte[] bytes) {
                if (bytes == null || bytes.length == 0) return null;
                try { return objectMapper.readValue(bytes, Object.class); }
                catch (Exception e) { throw new org.springframework.data.redis.serializer.SerializationException("无法反序列化", e); }
            }
        };

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}