package com.restaurant.menu.service;

import com.restaurant.common.util.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/**
 * 公开菜单缓存的事务后失效器（T41）。
 *
 * <p>库存/菜品/分类的写操作必须在数据库事务提交之后才允许清缓存：若在事务提交前 evict，
 * 并发请求会把旧值重新写回缓存，且事务回滚时缓存已被清空。这里统一注册
 * {@link TransactionSynchronization#afterCommit()}，提交后按 merchantId / dishId 精准失效，
 * 无事务时（单条自动提交写）立即失效。
 *
 * <p>不修改 TTL（仍由 {@code CacheConfig} 的 600s 控制），不做全量 flush。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuCacheEvictor {

    private final CacheManager cacheManager;

    private final StringRedisTemplate stringRedisTemplate;

    /** 库存变更：只影响菜品分页与菜品详情，不影响分类 dishCount。 */
    public void evictInventoryAfterCommit(Long merchantId, Long dishId) {
        afterCommit(() -> evictDishKeys(merchantId, dishId, false));
    }

    /** 菜品写操作（增删改/上下架/价格）：菜品分页 + 详情 + 分类 dishCount。 */
    public void evictDishAfterCommit(Long merchantId, Long dishId) {
        afterCommit(() -> evictDishKeys(merchantId, dishId, true));
    }

    /** 分类写操作：分类列表 + 该商家菜品分页 + 所有菜品详情（详情含 categoryName）。 */
    public void evictCategoryAfterCommit(Long merchantId) {
        afterCommit(() -> {
            evictCacheKey(CacheKeys.MENU_CATEGORIES, merchantId);
            deleteByPattern(CacheKeys.MENU_DISHES + ":" + merchantId + ":*");
            deleteByPattern(CacheKeys.MENU_DISH + ":*");
        });
    }

    private void afterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(eviction);
                }
            });
        } else {
            runSafely(eviction);
        }
    }

    private void evictDishKeys(Long merchantId, Long dishId, boolean includeCategories) {
        if (merchantId != null) {
            if (includeCategories) {
                evictCacheKey(CacheKeys.MENU_CATEGORIES, merchantId);
            }
            deleteByPattern(CacheKeys.MENU_DISHES + ":" + merchantId + ":*");
        }
        if (dishId != null) {
            evictCacheKey(CacheKeys.MENU_DISH, dishId);
        }
    }

    private void evictCacheKey(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void deleteByPattern(String pattern) {
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("菜单缓存按模式失效失败 pattern={}", pattern, ex);
        }
    }

    private void runSafely(Runnable eviction) {
        try {
            eviction.run();
        } catch (RuntimeException ex) {
            log.warn("菜单缓存失效失败，等待 TTL 自然过期", ex);
        }
    }
}
