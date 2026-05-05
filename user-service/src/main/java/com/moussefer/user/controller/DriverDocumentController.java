package com.moussefer.user.controller;

import com.moussefer.user.entity.DocumentType;
import com.moussefer.user.entity.DriverDocument;
import com.moussefer.user.service.DriverDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Driver KYC document endpoints.
 *
 * Driver-facing (maquette: "Inscription des informations chauffeur 1 & 2"):
 *   POST  /api/v1/drivers/documents                 upload with type + optional expiry
 *   GET   /api/v1/drivers/documents/me              list current documents
 *   GET   /api/v1/drivers/documents/me/kyc-status   summary for UI progress bar
 *   DELETE /api/v1/drivers/documents/{id}           driver can delete a PENDING doc (before review)
 *
 * Admin-facing (maquette: MODERATOR / SUPER_ADMIN KYC queue):
 *   GET   /api/v1/drivers/documents/internal/admin/pending
 *   POST  /api/v1/drivers/documents/internal/admin/{id}/approve
 *   POST  /api/v1/drivers/documents/internal/admin/{id}/reject
 *   GET   /api/v1/drivers/documents/internal/admin/{id}/preview   (presigned URL)
 *   GET   /api/v1/drivers/documents/internal/admin/user/{userId}  (see all docs of a driver)
 */
@RestController
@RequestMapping("/api/v1/drivers/documents")
@RequiredArgsConstructor
@Tag(name = "Driver Documents (KYC)", description = "Upload and verify driver documents")
public class DriverDocumentController {

    private final DriverDocumentService service;

    // ────────────── Driver endpoints ──────────────

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Driver: upload a KYC document",
            description = "Types: CIN, DRIVING_LICENSE_FRONT, DRIVING_LICENSE_BACK, VEHICLE_PHOTO, " +
                    "INSURANCE, TECHNICAL_VISIT, LOUAGE_AUTHORIZATION, OTHER. " +
                    "expiryDate is required for INSURANCE and TECHNICAL_VISIT.")
    public ResponseEntity<DriverDocument> upload(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam("type") DocumentType type,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "expiryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        DriverDocument doc = service.upload(userId, type, file, expiryDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/me")
    @Operation(summary = "Driver: list my current documents")
    public ResponseEntity<List<DriverDocument>> myDocuments(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.myDocuments(userId));
    }

    @GetMapping("/me/kyc-status")
    @Operation(summary = "Driver: overall KYC progress",
            description = "Returns completion percentage, missing types, pending/expired/rejected lists. " +
                    "Used by the driver UI progress bar (maquette: 'Progression finale : 85%').")
    public ResponseEntity<Map<String, Object>> myKycStatus(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.kycStatus(userId));
    }

    // ────────────── Admin endpoints ──────────────

    @GetMapping("/internal/admin/pending")
    @Operation(summary = "Admin: pending KYC queue")
    public ResponseEntity<Page<DriverDocument>> adminPending(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.adminListPending(pageable));
    }

    @PostMapping("/internal/admin/{id}/approve")
    @Operation(summary = "Admin: approve a document")
    public ResponseEntity<DriverDocument> adminApprove(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId) {
        return ResponseEntity.ok(service.adminApprove(id, adminId));
    }

    @PostMapping("/internal/admin/{id}/reject")
    @Operation(summary = "Admin: reject a document with reason")
    public ResponseEntity<DriverDocument> adminReject(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(service.adminReject(id, adminId, reason));
    }

    @GetMapping("/internal/admin/{id}/preview")
    @Operation(summary = "Admin: get a 1-hour presigned URL to preview a document")
    public ResponseEntity<Map<String, String>> adminPreview(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("url", service.adminPresignedUrl(id)));
    }

    @GetMapping("/internal/admin/user/{userId}/kyc-status")
    @Operation(summary = "Admin: KYC status summary of a specific driver")
    public ResponseEntity<Map<String, Object>> adminKycStatus(@PathVariable String userId) {
        return ResponseEntity.ok(service.kycStatus(userId));
    }
}
