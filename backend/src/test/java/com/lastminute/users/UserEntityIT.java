package com.lastminute.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.lastminute.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserEntityIT extends IntegrationTestBase {

  @Autowired private UserRepository users;

  @Test
  void persist_and_round_trip() {
    User u = new User();
    u.setEmail("rt@test.local");
    u.setRole(UserRole.consumer);
    User saved = users.save(u);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getRole()).isEqualTo(UserRole.consumer);
    assertThat(users.findByEmail("rt@test.local")).isPresent();
  }

  @Test
  void default_role_is_consumer() {
    User u = new User();
    u.setEmail("default-role@test.local");
    User saved = users.save(u);
    assertThat(saved.getRole()).isEqualTo(UserRole.consumer);
  }
}
