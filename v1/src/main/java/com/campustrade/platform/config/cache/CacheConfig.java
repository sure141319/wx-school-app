package com.campustrade.platform.config.cache;

import com.campustrade.platform.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final List<String> CACHE_VALUE_TYPE_ALLOWLIST = List.of(
            "com.campustrade.platform.",
            "java.lang.",
            "java.math.",
            "java.time.",
            "java.util."
    );

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "true", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, AppProperties appProperties) {
        GenericJackson2JsonRedisSerializer serializer = redisValueSerializer();

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("category:list", baseConfig.entryTtl(Duration.ofMinutes(appProperties.getCache().getCategoryTtlMinutes())));
        cacheConfigs.put("goods:list", baseConfig.entryTtl(Duration.ofMinutes(appProperties.getCache().getGoodsListTtlMinutes())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }

    static GenericJackson2JsonRedisSerializer redisValueSerializer() {
        return new GenericJackson2JsonRedisSerializer(cacheObjectMapper());
    }

    static ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                cacheTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    private static BasicPolymorphicTypeValidator cacheTypeValidator() {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();
        for (String packagePrefix : CACHE_VALUE_TYPE_ALLOWLIST) {
            builder = builder.allowIfSubType(packagePrefix);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "required", havingValue = "false")
    public CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager("category:list", "goods:list");
    }
}
