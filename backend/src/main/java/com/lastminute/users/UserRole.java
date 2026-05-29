package com.lastminute.users;

/** Maps to Postgres enum {@code user_role}. Values match Flyway V1__init.sql verbatim. */
public enum UserRole {
  consumer,
  provider,
  admin
}
