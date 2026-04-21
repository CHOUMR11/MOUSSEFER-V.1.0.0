package com.moussefer.admin.service;

import com.moussefer.admin.entity.AuditLog;
import com.moussefer.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog logAction(String adminId, String action,
                               String targetType, String targetId,
                               String details, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        log.info("Admin {} performed {} on {}/{}", adminId, action, targetType, targetId);
        return auditLogRepository.save(entry);
    }

    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    public Page<AuditLog> getLogsByAdmin(String adminId, Pageable pageable) {
        return auditLogRepository.findByAdminId(adminId, pageable);
    }

    public Page<AuditLog> getLogsByTarget(String targetType, String targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    }
}
