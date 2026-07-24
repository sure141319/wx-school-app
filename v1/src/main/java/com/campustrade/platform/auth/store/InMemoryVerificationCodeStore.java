package com.campustrade.platform.auth.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private final Map<String, ExpiringStringValue> stringStore = new HashMap<>();
    private final Map<String, ExpiringCounterValue> counterStore = new HashMap<>();

    @Override
    public synchronized IssueResult tryIssue(String codeKey,
                                              String limitKey,
                                              String attemptKey,
                                              String storedValue,
                                              Duration codeTtl,
                                              Duration limitWindow,
                                              long cooldownThresholdSeconds,
                                              int hourlyLimit) {
        ExpiringStringValue currentCode = liveString(codeKey);
        if (currentCode != null && remainingSeconds(currentCode) > cooldownThresholdSeconds) {
            return IssueResult.COOLDOWN;
        }

        ExpiringCounterValue currentLimit = liveCounter(limitKey);
        long count = currentLimit == null ? 0L : currentLimit.value();
        if (count >= hourlyLimit) {
            return IssueResult.HOURLY_LIMIT;
        }

        stringStore.put(codeKey, new ExpiringStringValue(storedValue, expiresAt(codeTtl)));
        counterStore.put(
                limitKey,
                new ExpiringCounterValue(
                        count + 1L,
                        currentLimit == null ? expiresAt(limitWindow) : currentLimit.expireAt()
                )
        );
        counterStore.remove(attemptKey);
        return IssueResult.ISSUED;
    }

    @Override
    public synchronized boolean rollbackIssue(String codeKey,
                                              String limitKey,
                                              String attemptKey,
                                              String expectedStoredValue) {
        ExpiringStringValue currentCode = liveString(codeKey);
        if (currentCode == null || !currentCode.value().equals(expectedStoredValue)) {
            return false;
        }

        stringStore.remove(codeKey);
        counterStore.remove(attemptKey);
        ExpiringCounterValue currentLimit = liveCounter(limitKey);
        if (currentLimit != null && currentLimit.value() > 1L) {
            counterStore.put(limitKey, new ExpiringCounterValue(currentLimit.value() - 1L, currentLimit.expireAt()));
        } else {
            counterStore.remove(limitKey);
        }
        return true;
    }

    @Override
    public synchronized ValidationResult validateAndConsume(String codeKey,
                                                            String attemptKey,
                                                            String inputCode,
                                                            int maxAttempts,
                                                            Duration attemptTtl) {
        ExpiringStringValue currentCode = liveString(codeKey);
        if (currentCode == null) {
            return ValidationResult.MISSING;
        }

        ExpiringCounterValue currentAttempts = liveCounter(attemptKey);
        long attempts = currentAttempts == null ? 0L : currentAttempts.value();
        if (attempts >= maxAttempts) {
            stringStore.remove(codeKey);
            return ValidationResult.TOO_MANY_ATTEMPTS;
        }

        if (extractCode(currentCode.value()).equals(inputCode)) {
            stringStore.remove(codeKey);
            counterStore.remove(attemptKey);
            return ValidationResult.VALID;
        }

        long nextAttempts = attempts + 1L;
        counterStore.put(
                attemptKey,
                new ExpiringCounterValue(
                        nextAttempts,
                        currentAttempts == null ? expiresAt(attemptTtl) : currentAttempts.expireAt()
                )
        );
        if (nextAttempts >= maxAttempts) {
            stringStore.remove(codeKey);
            return ValidationResult.TOO_MANY_ATTEMPTS;
        }
        return ValidationResult.INVALID;
    }

    private Instant expiresAt(Duration ttl) {
        return Instant.now().plus(ttl);
    }

    private ExpiringStringValue liveString(String key) {
        ExpiringStringValue value = stringStore.get(key);
        if (value != null && value.isExpired()) {
            stringStore.remove(key);
            return null;
        }
        return value;
    }

    private ExpiringCounterValue liveCounter(String key) {
        ExpiringCounterValue value = counterStore.get(key);
        if (value != null && value.isExpired()) {
            counterStore.remove(key);
            return null;
        }
        return value;
    }

    private long remainingSeconds(ExpiringStringValue value) {
        return Math.max(Duration.between(Instant.now(), value.expireAt()).toSeconds(), 0L);
    }

    private String extractCode(String storedValue) {
        int separator = storedValue.indexOf(':');
        return separator < 0 ? storedValue : storedValue.substring(separator + 1);
    }

    private record ExpiringStringValue(String value, Instant expireAt) {
        boolean isExpired() {
            return !expireAt.isAfter(Instant.now());
        }
    }

    private record ExpiringCounterValue(long value, Instant expireAt) {
        boolean isExpired() {
            return !expireAt.isAfter(Instant.now());
        }
    }
}
