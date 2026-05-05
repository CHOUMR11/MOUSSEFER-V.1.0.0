package com.moussefer.trajet.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Summary of a bulk fare import.
 *
 * Example:
 * {
 *   "format": "JSON",
 *   "total": 120,
 *   "created": 45,
 *   "updated": 72,
 *   "skipped": 3,
 *   "errors": [
 *     "Row 17 (Tunis → Tunis): departureCity and arrivalCity must be different",
 *     "Row 89 (Sfax → Gabès): pricePerSeat must be greater than 0",
 *     "Row 102 (? → Béja): departureCity is required"
 *   ],
 *   "importedBy": "admin_7f3c"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareImportReport {
    private String format;
    private int total;
    private int created;
    private int updated;
    private int skipped;
    private List<String> errors;
    private String importedBy;
}
