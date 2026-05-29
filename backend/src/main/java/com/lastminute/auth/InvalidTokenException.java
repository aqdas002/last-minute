package com.lastminute.auth;

public class InvalidTokenException extends RuntimeException {
  public InvalidTokenException(String reason) {
    super(reason);
  }
}
