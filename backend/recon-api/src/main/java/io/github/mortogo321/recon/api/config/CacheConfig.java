package io.github.mortogo321.recon.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.mortogo321.recon.legacy.gateway.MerchantDirectory;

/**
 * Local caching for legacy reads.
 *
 * <p>Caffeine rather than Redis because the cached data is small, immutable-in-practice merchant
 * master data, and a per-instance cache with a short TTL is strictly simpler than a network hop
 * plus a serialisation format plus an eviction protocol. The moment this needs to be shared across
 * instances the interface stays the same and only this class changes.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        manager.setCacheNames(List.of(MerchantDirectory.CACHE));
        return manager;
    }
}
