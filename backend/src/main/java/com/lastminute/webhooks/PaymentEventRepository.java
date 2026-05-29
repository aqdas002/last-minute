package com.lastminute.webhooks;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
  Optional<PaymentEvent> findByStripeEventId(String stripeEventId);
}
