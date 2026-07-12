package com.campustrade.platform.config.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class GoodsListCacheInvalidator {

    public static final String GOODS_LIST_CACHE = "goods:list";

    private final CacheManager cacheManager;

    public GoodsListCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow();
            }
        });
    }

    void evictNow() {
        Cache cache = cacheManager.getCache(GOODS_LIST_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
