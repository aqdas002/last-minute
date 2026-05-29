package com.lastminute.webhooks;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeadLetterRepository extends JpaRepository<WebhookDeadLetter, UUID> {
  List<WebhookDeadLetter> findAllByOrderByFirstFailedAtAsc(Limit limit);
}
