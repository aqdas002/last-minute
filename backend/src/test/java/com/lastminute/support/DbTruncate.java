package com.lastminute.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wipes business tables AND clears the Caffeine listing caches between integration tests. Add
 * new tables here as later milestones introduce them (M3: bookings, payment_events,
 * webhook_dead_letter; M2: admin_actions).
 */
@Component
public class DbTruncate {

  @PersistenceContext private EntityManager em;
  @Autowired private CacheManager cacheManager;

  @Transactional
  public void truncateAll() {
    em.createNativeQuery(
            """
            TRUNCATE TABLE
              verification_tokens,
              listings,
              providers,
              categories,
              users
            RESTART IDENTITY CASCADE
            """)
        .executeUpdate();

    // Cache lives in-process; truncation alone leaves stale entries from prior tests.
    cacheManager.getCacheNames().forEach(name -> {
      var c = cacheManager.getCache(name);
      if (c != null) c.clear();
    });
  }
}
