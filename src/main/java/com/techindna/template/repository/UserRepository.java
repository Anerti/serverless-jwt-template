package com.techindna.template.repository;

import com.techindna.template.repository.model.JUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<JUser, UUID> {

    Optional<JUser> findByUsername(String username);

    Optional<JUser> findByEmail(String email);

    @Query(
            """
            SELECT u FROM JUser u
            WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<JUser> findBySearch(@Param("search") String search, Pageable pageable);
}
