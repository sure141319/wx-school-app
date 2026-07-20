package com.campustrade.platform.goods.ratelimit;

import com.campustrade.platform.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

public class RedisContactEmailRateLimiter implements ContactEmailRateLimiter {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            local current = tonumber(redis.call('GET', KEYS[2]) or '0')
            if current >= tonumber(ARGV[3]) then
                return -1
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            current = redis.call('INCR', KEYS[2])
            if current == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('DEL', KEYS[1]) == 0 then
                return 0
            end
            local current = redis.call('GET', KEYS[2])
            if current then
                local remaining = redis.call('DECR', KEYS[2])
                if remaining <= 0 then
                    redis.call('DEL', KEYS[2])
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration contactCooldown;
    private final Duration hourlyWindow;
    private final int hourlyLimit;

    public RedisContactEmailRateLimiter(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        AppProperties.ContactEmail settings = appProperties.getContactEmail();
        this.redisTemplate = redisTemplate;
        this.keyPrefix = settings.getKeyPrefix();
        this.contactCooldown = Duration.ofHours(settings.getCooldownHours());
        this.hourlyWindow = Duration.ofMinutes(settings.getHourlyWindowMinutes());
        this.hourlyLimit = settings.getHourlyLimit();
    }

    @Override
    public Result tryAcquire(Long buyerId, Long goodsId) {
        Long result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                keys(buyerId, goodsId),
                String.valueOf(contactCooldown.toSeconds()),
                String.valueOf(hourlyWindow.toSeconds()),
                String.valueOf(hourlyLimit)
        );
        if (result == null) {
            throw new IllegalStateException("Redis 未返回联系邮件限流结果");
        }
        if (result == 1L) {
            return Result.ACQUIRED;
        }
        if (result == 0L) {
            return Result.CONTACT_COOLDOWN;
        }
        return Result.HOURLY_LIMIT;
    }

    @Override
    public void release(Long buyerId, Long goodsId) {
        redisTemplate.execute(RELEASE_SCRIPT, keys(buyerId, goodsId));
    }

    private List<String> keys(Long buyerId, Long goodsId) {
        String buyerHashTag = "{" + buyerId + "}";
        return List.of(
                keyPrefix + buyerHashTag + ":cooldown:" + goodsId,
                keyPrefix + buyerHashTag + ":hourly"
        );
    }
}
