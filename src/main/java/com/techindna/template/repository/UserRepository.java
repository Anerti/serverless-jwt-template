package com.techindna.template.repository;

import com.techindna.template.entity.enums.UserRole;
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
            WHERE u.role = :role
              AND (:search IS NULL
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<JUser> findByRoleAndSearch(
            @Param("role") UserRole role, @Param("search") String search, Pageable pageable);
}
