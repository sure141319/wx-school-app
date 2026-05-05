package com.campustrade.platform.auth.store;

import java.time.Duration;

public interface VerificationCodeStore {

    String get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);

    Long getExpire(String key);

    Long increment(String key, Duration ttlOnCreate);
}
