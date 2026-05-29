package com.lastminute.bookings;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Spec §4: 8-char alphanumeric uppercase excluding 0/O/1/I/L. */
@Component
public class RedemptionCodeGenerator {

  private static final char[] ALPHABET =
      "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray(); // 31 chars (no 0/O/1/I/L)
  private static final SecureRandom RNG = new SecureRandom();

  public String generate() {
    char[] out = new char[8];
    for (int i = 0; i < 8; i++) out[i] = ALPHABET[RNG.nextInt(ALPHABET.length)];
    return new String(out);
  }
}
