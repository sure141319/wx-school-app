package com.campustrade.platform.goods.ratelimit;

import com.campustrade.platform.config.AppProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InMemoryContactEmailRateLimiter implements ContactEmailRateLimiter {

    private final Duration contactCooldown;
    private final int hourlyLimit;
    private final Duration hourlyWindow;
    private final Clock clock;
    private final Map<BuyerGoodsKey, Instant> contactCooldowns = new HashMap<>();
    private final Map<Long, WindowCounter> hourlyCounters = new HashMap<>();

    public InMemoryContactEmailRateLimiter(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC());
    }

    InMemoryContactEmailRateLimiter(AppProperties appProperties, Clock clock) {
        AppProperties.ContactEmail settings = appProperties.getContactEmail();
        this.contactCooldown = Duration.ofHours(settings.getCooldownHours());
        this.hourlyLimit = settings.getHourlyLimit();
        this.hourlyWindow = Duration.ofMinutes(settings.getHourlyWindowMinutes());
        this.clock = clock;
    }

    @Override
    public synchronized Result tryAcquire(Long buyerId, Long goodsId) {
        Instant now = clock.instant();
        cleanupExpiredEntries(now);
        BuyerGoodsKey contactKey = new BuyerGoodsKey(buyerId, goodsId);
        Instant contactExpiresAt = contactCooldowns.get(contactKey);
        if (contactExpiresAt != null && contactExpiresAt.isAfter(now)) {
            return Result.CONTACT_COOLDOWN;
        }
        contactCooldowns.remove(contactKey);

        WindowCounter counter = hourlyCounters.get(buyerId);
        if (counter == null || !counter.expiresAt().isAfter(now)) {
            counter = new WindowCounter(0, now.plus(hourlyWindow));
        }
        if (counter.count() >= hourlyLimit) {
            hourlyCounters.put(buyerId, counter);
            return Result.HOURLY_LIMIT;
        }

        contactCooldowns.put(contactKey, now.plus(contactCooldown));
        hourlyCounters.put(buyerId, new WindowCounter(counter.count() + 1, counter.expiresAt()));
        return Result.ACQUIRED;
    }

    @Override
    public synchronized void release(Long buyerId, Long goodsId) {
        BuyerGoodsKey contactKey = new BuyerGoodsKey(buyerId, goodsId);
        if (contactCooldowns.remove(contactKey) == null) {
            return;
        }

        Instant now = clock.instant();
        WindowCounter counter = hourlyCounters.get(buyerId);
        if (counter == null || !counter.expiresAt().isAfter(now) || counter.count() <= 1) {
            hourlyCounters.remove(buyerId);
            return;
        }
        hourlyCounters.put(buyerId, new WindowCounter(counter.count() - 1, counter.expiresAt()));
    }

    private void cleanupExpiredEntries(Instant now) {
        contactCooldowns.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        hourlyCounters.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record BuyerGoodsKey(Long buyerId, Long goodsId) {
    }

    private record WindowCounter(int count, Instant expiresAt) {
    }
}
