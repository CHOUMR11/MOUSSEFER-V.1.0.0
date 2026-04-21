package com.moussefer.admin.repository;

import com.moussefer.admin.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface AdminRoleRepository extends JpaRepository<AdminRole, String> {

    Optional<AdminRole> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** ADMIN-01: Load all active role names into a Set for fast lookup in AdminRoleGuard. */
    @Query("SELECT r.name FROM AdminRole r WHERE r.active = true")
    Set<String> findAllActiveRoleNames();
}
