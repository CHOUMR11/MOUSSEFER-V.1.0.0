package com.moussefer.admin.controller;

import com.moussefer.admin.entity.FeatureToggle;
import com.moussefer.admin.repository.FeatureToggleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Platform feature toggles — admin "Fonctionnalités" page.
 *
 * Public read endpoint lets any authenticated app check which features
 * are enabled (used for client-side conditional UI). Writes are
 * SUPER_ADMIN only — guarded by AdminRoleGuard.
 */
@RestController
@RequestMapping("/api/v1/admin/features")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Feature Toggles", description = "Platform-wide feature switches")
public class FeatureToggleController {

    private final FeatureToggleRepository repository;

    @GetMapping
    @Operation(summary = "List all feature toggles grouped by category")
    public ResponseEntity<List<FeatureToggle>> list() {
        return ResponseEntity.ok(repository.findAllByOrderByCategoryAscFeatureKeyAsc());
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get a feature toggle by key")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        return repository.findByFeatureKey(key)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Feature not found: " + key)));
    }

    @PostMapping
    @Operation(summary = "Create or update a feature toggle (SUPER_ADMIN only)")
    public ResponseEntity<FeatureToggle> upsert(
            @RequestHeader("X-User-Id") String adminId,
            @RequestBody FeatureToggle input) {
        FeatureToggle existing = repository.findByFeatureKey(input.getFeatureKey()).orElse(null);
        FeatureToggle saved;
        if (existing == null) {
            input.setId(null);
            input.setUpdatedBy(adminId);
            saved = repository.save(input);
        } else {
            existing.setDisplayName(input.getDisplayName());
            existing.setDescription(input.getDescription());
            existing.setEnabled(input.isEnabled());
            existing.setCategory(input.getCategory());
            existing.setUpdatedBy(adminId);
            saved = repository.save(existing);
        }
        log.info("Admin {} saved feature toggle {} (enabled={})",
                adminId, saved.getFeatureKey(), saved.isEnabled());
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{key}/toggle")
    @Operation(summary = "Flip a feature toggle on/off (SUPER_ADMIN only)")
    public ResponseEntity<FeatureToggle> toggle(
            @PathVariable String key,
            @RequestHeader("X-User-Id") String adminId) {
        FeatureToggle f = repository.findByFeatureKey(key)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + key));
        f.setEnabled(!f.isEnabled());
        f.setUpdatedBy(adminId);
        FeatureToggle saved = repository.save(f);
        log.info("Admin {} toggled feature {} to {}", adminId, key, saved.isEnabled());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a feature toggle (SUPER_ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
