package com.campustrade.platform.auth.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class VerificationCodeStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "true", matchIfMissing = true)
    public VerificationCodeStore redisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        return new RedisVerificationCodeStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "false")
    public VerificationCodeStore inMemoryVerificationCodeStore() {
        return new InMemoryVerificationCodeStore();
    }
}
