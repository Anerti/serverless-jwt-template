package com.techindna.template.repository;

import com.techindna.template.repository.model.JUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<JUser, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
