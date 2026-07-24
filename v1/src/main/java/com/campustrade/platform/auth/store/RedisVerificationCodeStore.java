package com.campustrade.platform.auth.store;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local ttl = redis.call('TTL', KEYS[1])
            if ttl > tonumber(ARGV[4]) then
                return 1
            end

            local rawCount = redis.call('GET', KEYS[2])
            local count = tonumber(rawCount)
            if not count then
                redis.call('DEL', KEYS[2])
                count = 0
            end
            if count >= tonumber(ARGV[5]) then
                return 2
            end

            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            local nextCount = redis.call('INCR', KEYS[2])
            if nextCount == 1 or redis.call('PTTL', KEYS[2]) < 0 then
                redis.call('PEXPIRE', KEYS[2], ARGV[3])
            end
            redis.call('DEL', KEYS[3])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end

            redis.call('DEL', KEYS[1], KEYS[3])
            local count = tonumber(redis.call('GET', KEYS[2]))
            if not count or count <= 1 then
                redis.call('DEL', KEYS[2])
            else
                redis.call('DECR', KEYS[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> VALIDATE_SCRIPT = new DefaultRedisScript<>("""
            local storedValue = redis.call('GET', KEYS[1])
            if not storedValue then
                return 1
            end

            local rawAttempts = redis.call('GET', KEYS[2])
            local attempts = tonumber(rawAttempts)
            if not attempts then
                redis.call('DEL', KEYS[2])
                attempts = 0
            end
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                return 3
            end

            local separator = string.find(storedValue, ':', 1, true)
            local code = separator and string.sub(storedValue, separator + 1) or storedValue
            if code == ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 0
            end

            local nextAttempts = redis.call('INCR', KEYS[2])
            if nextAttempts == 1 or redis.call('PTTL', KEYS[2]) < 0 then
                redis.call('PEXPIRE', KEYS[2], ARGV[3])
            end
            if nextAttempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                return 3
            end
            return 2
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public IssueResult tryIssue(String codeKey,
                                String limitKey,
                                String attemptKey,
                                String storedValue,
                                Duration codeTtl,
                                Duration limitWindow,
                                long cooldownThresholdSeconds,
                                int hourlyLimit) {
        Long result = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(codeKey, limitKey, attemptKey),
                storedValue,
                String.valueOf(codeTtl.toMillis()),
                String.valueOf(limitWindow.toMillis()),
                String.valueOf(cooldownThresholdSeconds),
                String.valueOf(hourlyLimit)
        );
        return switch (result == null ? -1 : result.intValue()) {
            case 0 -> IssueResult.ISSUED;
            case 1 -> IssueResult.COOLDOWN;
            case 2 -> IssueResult.HOURLY_LIMIT;
            default -> throw new IllegalStateException("Unexpected verification-code issue result: " + result);
        };
    }

    @Override
    public boolean rollbackIssue(String codeKey,
                                 String limitKey,
                                 String attemptKey,
                                 String expectedStoredValue) {
        Long result = redisTemplate.execute(
                ROLLBACK_SCRIPT,
                List.of(codeKey, limitKey, attemptKey),
                expectedStoredValue
        );
        return result != null && result == 1L;
    }

    @Override
    public ValidationResult validateAndConsume(String codeKey,
                                               String attemptKey,
                                               String inputCode,
                                               int maxAttempts,
                                               Duration attemptTtl) {
        Long result = redisTemplate.execute(
                VALIDATE_SCRIPT,
                List.of(codeKey, attemptKey),
                inputCode,
                String.valueOf(maxAttempts),
                String.valueOf(attemptTtl.toMillis())
        );
        return switch (result == null ? -1 : result.intValue()) {
            case 0 -> ValidationResult.VALID;
            case 1 -> ValidationResult.MISSING;
            case 2 -> ValidationResult.INVALID;
            case 3 -> ValidationResult.TOO_MANY_ATTEMPTS;
            default -> throw new IllegalStateException("Unexpected verification-code validation result: " + result);
        };
    }
}
