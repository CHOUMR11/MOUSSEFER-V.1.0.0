package com.moussefer.admin.repository;

import com.moussefer.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    Page<AuditLog> findByAdminId(String adminId, Pageable pageable);
    Page<AuditLog> findByTargetTypeAndTargetId(String targetType, String targetId, Pageable pageable);
}
