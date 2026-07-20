package com.campustrade.platform.goods.ratelimit;

import com.campustrade.platform.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryContactEmailRateLimiterTest {

    @Test
    void enforcesPerGoodsCooldownAndBuyerHourlyLimitAtomically() {
        AppProperties properties = properties(2);
        InMemoryContactEmailRateLimiter limiter = new InMemoryContactEmailRateLimiter(properties);

        assertEquals(ContactEmailRateLimiter.Result.ACQUIRED, limiter.tryAcquire(2L, 10L));
        assertEquals(ContactEmailRateLimiter.Result.CONTACT_COOLDOWN, limiter.tryAcquire(2L, 10L));
        assertEquals(ContactEmailRateLimiter.Result.ACQUIRED, limiter.tryAcquire(2L, 11L));
        assertEquals(ContactEmailRateLimiter.Result.HOURLY_LIMIT, limiter.tryAcquire(2L, 12L));
    }

    @Test
    void failedDeliveryReleaseRestoresBothLimits() {
        AppProperties properties = properties(1);
        InMemoryContactEmailRateLimiter limiter = new InMemoryContactEmailRateLimiter(properties);

        assertEquals(ContactEmailRateLimiter.Result.ACQUIRED, limiter.tryAcquire(2L, 10L));
        limiter.release(2L, 10L);

        assertEquals(ContactEmailRateLimiter.Result.ACQUIRED, limiter.tryAcquire(2L, 10L));
    }

    private AppProperties properties(int hourlyLimit) {
        AppProperties properties = new AppProperties();
        properties.getContactEmail().setCooldownHours(24);
        properties.getContactEmail().setHourlyLimit(hourlyLimit);
        properties.getContactEmail().setHourlyWindowMinutes(60);
        return properties;
    }
}
