package com.lastminute.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wipes business tables between integration tests. Add new tables here as later milestones
 * introduce them (M3: bookings, payment_events, webhook_dead_letter; M2: admin_actions).
 */
@Component
public class DbTruncate {

  @PersistenceContext private EntityManager em;

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
  }
}
