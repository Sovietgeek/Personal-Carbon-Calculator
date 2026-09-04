package com.ecoverse.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration — Phase 3.
 *
 * Enables Spring caching with Caffeine as the in-process provider.
 * - Weather cache: 10-minute TTL (weather changes slowly, reduces Open-Meteo calls)
 * - News cache: 15-minute TTL (RSS feeds update infrequently, protects rss2json rate limit)
 * - Default cache: 10-minute TTL for any other cacheable operations
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("expireAfterWrite=10m,maximumSize=100");
        return manager;
    }

    @Bean
    public Caffeine<Object, Object> weatherCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(50)
                .recordStats();
    }

    @Bean
    public Caffeine<Object, Object> newsCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(20)
                .recordStats();
    }
}
