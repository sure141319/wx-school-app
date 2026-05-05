package com.campustrade.platform.auth.store;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private final Map<String, ExpiringStringValue> stringStore = new ConcurrentHashMap<>();
    private final Map<String, ExpiringCounterValue> counterStore = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        ExpiringStringValue value = stringStore.get(key);
        if (value == null || value.isExpired()) {
            stringStore.remove(key);
            ExpiringCounterValue counterValue = counterStore.get(key);
            if (counterValue == null || counterValue.isExpired()) {
                counterStore.remove(key);
                return null;
            }
            return String.valueOf(counterValue.counter().get());
        }
        return value.value();
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        stringStore.put(key, new ExpiringStringValue(value, expiresAt(ttl)));
    }

    @Override
    public void delete(String key) {
        stringStore.remove(key);
        counterStore.remove(key);
    }

    @Override
    public Long getExpire(String key) {
        ExpiringStringValue value = stringStore.get(key);
        if (value == null || value.isExpired()) {
            stringStore.remove(key);
            return -2L;
        }
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), value.expireAt());
        return Math.max(seconds, 0L);
    }

    @Override
    public Long increment(String key, Duration ttlOnCreate) {
        ExpiringCounterValue next = counterStore.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new ExpiringCounterValue(new AtomicLong(1L), expiresAt(ttlOnCreate));
            }
            existing.counter().incrementAndGet();
            return existing;
        });
        return next.counter().get();
    }

    private LocalDateTime expiresAt(Duration ttl) {
        return LocalDateTime.now().plus(ttl);
    }

    private record ExpiringStringValue(String value, LocalDateTime expireAt) {
        boolean isExpired() {
            return expireAt.isBefore(LocalDateTime.now());
        }
    }

    private record ExpiringCounterValue(AtomicLong counter, LocalDateTime expireAt) {
        boolean isExpired() {
            return expireAt.isBefore(LocalDateTime.now());
        }
    }
}
