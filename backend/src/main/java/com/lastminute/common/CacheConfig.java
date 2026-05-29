package com.lastminute.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spec §2 caching. Short TTL (15s) is a safety net; the correctness layer is the
 * {@code listing_expires_at > clock.now()} filter the repository already applies, and admin
 * writes do {@code @CacheEvict allEntries=true}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager mgr = new CaffeineCacheManager("listings-by-category", "starting-soon");
    mgr.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(15)).maximumSize(1_000));
    return mgr;
  }
}
