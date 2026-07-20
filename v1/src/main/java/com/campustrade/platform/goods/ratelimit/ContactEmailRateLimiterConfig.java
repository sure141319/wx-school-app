package com.campustrade.platform.goods.ratelimit;

import com.campustrade.platform.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ContactEmailRateLimiterConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "true", matchIfMissing = true)
    public ContactEmailRateLimiter redisContactEmailRateLimiter(StringRedisTemplate redisTemplate,
                                                                AppProperties appProperties) {
        return new RedisContactEmailRateLimiter(redisTemplate, appProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "false")
    public ContactEmailRateLimiter inMemoryContactEmailRateLimiter(AppProperties appProperties) {
        return new InMemoryContactEmailRateLimiter(appProperties);
    }
}
