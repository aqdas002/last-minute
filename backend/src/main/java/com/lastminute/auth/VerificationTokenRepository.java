package com.lastminute.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository
    extends JpaRepository<VerificationToken, VerificationTokenId> {
  Optional<VerificationToken> findByToken(String token);
}
