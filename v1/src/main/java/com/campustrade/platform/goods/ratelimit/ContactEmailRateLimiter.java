package com.campustrade.platform.goods.ratelimit;

public interface ContactEmailRateLimiter {

    Result tryAcquire(Long buyerId, Long goodsId);

    void release(Long buyerId, Long goodsId);

    enum Result {
        ACQUIRED,
        CONTACT_COOLDOWN,
        HOURLY_LIMIT
    }
}
