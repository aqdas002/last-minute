package com.lastminute.admin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {}
