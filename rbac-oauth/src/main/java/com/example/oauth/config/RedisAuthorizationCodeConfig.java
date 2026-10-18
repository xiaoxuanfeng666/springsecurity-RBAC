package com.example.oauth.config;

import com.example.oauth.oauth.RedisAuthorizationCodeServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;

import java.time.Duration;

/**
 * 授权码存储配置：用 Redis 替换默认的内存实现
 * <p>
 * 单独成类是为了避免 {@link AuthorizationServerConfig} 既定义又注入同一个 Bean 的循环依赖。
 * 值用 JDK 序列化：授权码里是一整份 {@code OAuth2Authentication}，JSON 化需要处理多态与权限集合，
 * 而它只在同一个服务的同一版本内读写，JDK 序列化最简单也最忠实。
 */
@Configuration
public class RedisAuthorizationCodeConfig {

    @Bean
    public RedisTemplate<String, OAuth2Authentication> authorizationCodeRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OAuth2Authentication> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public AuthorizationCodeServices authorizationCodeServices(
            @Qualifier("authorizationCodeRedisTemplate")
            RedisTemplate<String, OAuth2Authentication> authorizationCodeRedisTemplate,
            @Value("${oauth2.authorization-code-ttl-seconds:300}") long ttlSeconds) {
        return new RedisAuthorizationCodeServices(
                authorizationCodeRedisTemplate, Duration.ofSeconds(ttlSeconds));
    }
}
