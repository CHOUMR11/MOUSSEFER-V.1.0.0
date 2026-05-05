package com.moussefer.trajet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.trajet.dto.request.RegulatedFareDto;
import com.moussefer.trajet.dto.response.FareImportReport;
import com.moussefer.trajet.entity.RegulatedFare;
import com.moussefer.trajet.exception.BusinessException;
import com.moussefer.trajet.repository.RegulatedFareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages government-regulated louage fares.
 *
 * Supported import formats:
 *   - JSON  (array of objects — the canonical format)
 *   - CSV   (one fare per line, header row required)
 *
 * JSON example:
 * [
 *   { "departureCity": "Tunis", "arrivalCity": "Sousse", "pricePerSeat": 15.00,
 *     "distanceKm": 140, "effectiveDate": "2026-01-01",
 *     "source": "MINISTERE_TRANSPORT_CIRC_2026_01" }
 * ]
 *
 * CSV example:
 *   departureCity,arrivalCity,pricePerSeat,distanceKm,effectiveDate,source
 *   Tunis,Sousse,15.00,140,2026-01-01,MINISTERE_TRANSPORT_CIRC_2026_01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatedFareService {

    private final RegulatedFareRepository repository;
    private final ObjectMapper objectMapper;

    // ────────────────────── Lookup ──────────────────────

    /** Looks up the official fare for a route. Returns empty if none is set. */
    @Transactional(readOnly = true)
    public Optional<RegulatedFare> findActiveFare(String dep, String arr) {
        return repository.findActiveFare(dep, arr);
    }

    @Transactional(readOnly = true)
    public Page<RegulatedFare> search(String city, Boolean active, Pageable pageable) {
        return repository.search(city, active, pageable);
    }

    // ────────────────────── CRUD ──────────────────────

    @Transactional
    public RegulatedFare upsert(RegulatedFareDto dto, String adminId) {
        validate(dto);
        RegulatedFare existing = repository.findByRoute(dto.getDepartureCity(), dto.getArrivalCity())
                .orElse(null);
        if (existing == null) {
            RegulatedFare created = RegulatedFare.builder()
                    .departureCity(dto.getDepartureCity().trim())
                    .arrivalCity(dto.getArrivalCity().trim())
                    .pricePerSeat(dto.getPricePerSeat())
                    .distanceKm(dto.getDistanceKm())
                    .effectiveDate(dto.getEffectiveDate())
                    .source(dto.getSource())
                    .active(dto.getActive() != null ? dto.getActive() : true)
                    .importedBy(adminId)
                    .build();
            return repository.save(created);
        }
        existing.setPricePerSeat(dto.getPricePerSeat());
        if (dto.getDistanceKm() != null)   existing.setDistanceKm(dto.getDistanceKm());
        if (dto.getEffectiveDate() != null) existing.setEffectiveDate(dto.getEffectiveDate());
        if (dto.getSource() != null)        existing.setSource(dto.getSource());
        if (dto.getActive() != null)        existing.setActive(dto.getActive());
        existing.setImportedBy(adminId);
        return repository.save(existing);
    }

    @Transactional
    public void setActive(String id, boolean active) {
        RegulatedFare f = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Regulated fare not found: " + id));
        f.setActive(active);
        repository.save(f);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new BusinessException("Regulated fare not found: " + id);
        }
        repository.deleteById(id);
    }

    // ────────────────────── Bulk import ──────────────────────

    /**
     * Bulk-imports fares from a JSON file (array of RegulatedFareDto).
     * Uses upsert semantics: existing routes are updated, new routes created.
     * Invalid rows are skipped and reported — the import does NOT abort on a
     * single bad row, so a partial import stays useful.
     */
    @Transactional
    public FareImportReport importFromJson(byte[] content, String adminId) {
        try {
            List<RegulatedFareDto> dtos = objectMapper.readValue(
                    content, new TypeReference<List<RegulatedFareDto>>() {});
            return applyImport(dtos, adminId, "JSON");
        } catch (Exception e) {
            log.error("JSON import failed: {}", e.getMessage(), e);
            throw new BusinessException("Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Bulk-imports from CSV. Expected header:
     * departureCity,arrivalCity,pricePerSeat[,distanceKm,effectiveDate,source]
     * Empty lines and lines starting with # are skipped.
     */
    @Transactional
    public FareImportReport importFromCsv(byte[] content, String adminId) {
        List<RegulatedFareDto> dtos = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) {
                throw new BusinessException("CSV file is empty");
            }
            String[] cols = header.split(",");
            int idxDep = indexOf(cols, "departureCity");
            int idxArr = indexOf(cols, "arrivalCity");
            int idxPrice = indexOf(cols, "pricePerSeat");
            int idxDist = indexOf(cols, "distanceKm");
            int idxDate = indexOf(cols, "effectiveDate");
            int idxSrc  = indexOf(cols, "source");

            if (idxDep == -1 || idxArr == -1 || idxPrice == -1) {
                throw new BusinessException(
                        "CSV header must contain: departureCity,arrivalCity,pricePerSeat");
            }

            String line;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] v = line.split(",", -1);
                try {
                    RegulatedFareDto dto = new RegulatedFareDto();
                    dto.setDepartureCity(v[idxDep].trim());
                    dto.setArrivalCity(v[idxArr].trim());
                    dto.setPricePerSeat(new BigDecimal(v[idxPrice].trim()));
                    if (idxDist  != -1 && !v[idxDist].isBlank()) dto.setDistanceKm(new BigDecimal(v[idxDist].trim()));
                    if (idxDate  != -1 && !v[idxDate].isBlank()) dto.setEffectiveDate(LocalDate.parse(v[idxDate].trim()));
                    if (idxSrc   != -1 && !v[idxSrc].isBlank())  dto.setSource(v[idxSrc].trim());
                    dtos.add(dto);
                } catch (Exception e) {
                    parseErrors.add("Line " + lineNo + ": " + e.getMessage());
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("CSV parsing failed: " + e.getMessage());
        }
        FareImportReport report = applyImport(dtos, adminId, "CSV");
        if (!parseErrors.isEmpty()) {
            List<String> merged = new ArrayList<>(report.getErrors());
            merged.addAll(parseErrors);
            report.setErrors(merged);
        }
        return report;
    }

    private int indexOf(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (name.equalsIgnoreCase(cols[i].trim())) return i;
        }
        return -1;
    }

    private FareImportReport applyImport(List<RegulatedFareDto> dtos, String adminId, String format) {
        FareImportReport report = FareImportReport.builder()
                .format(format)
                .total(dtos.size())
                .created(0)
                .updated(0)
                .skipped(0)
                .errors(new ArrayList<>())
                .importedBy(adminId)
                .build();

        for (int i = 0; i < dtos.size(); i++) {
            RegulatedFareDto dto = dtos.get(i);
            try {
                validate(dto);
                boolean exists = repository.findByRoute(dto.getDepartureCity(), dto.getArrivalCity()).isPresent();
                upsert(dto, adminId);
                if (exists) report.setUpdated(report.getUpdated() + 1);
                else        report.setCreated(report.getCreated() + 1);
            } catch (Exception e) {
                report.setSkipped(report.getSkipped() + 1);
                report.getErrors().add("Row " + (i + 1) + " ("
                        + (dto.getDepartureCity() != null ? dto.getDepartureCity() : "?") + " → "
                        + (dto.getArrivalCity() != null ? dto.getArrivalCity() : "?")
                        + "): " + e.getMessage());
            }
        }

        log.info("Fare import by admin {}: format={}, total={}, created={}, updated={}, skipped={}",
                adminId, format, report.getTotal(), report.getCreated(),
                report.getUpdated(), report.getSkipped());
        return report;
    }

    private void validate(RegulatedFareDto dto) {
        if (dto.getDepartureCity() == null || dto.getDepartureCity().isBlank()) {
            throw new BusinessException("departureCity is required");
        }
        if (dto.getArrivalCity() == null || dto.getArrivalCity().isBlank()) {
            throw new BusinessException("arrivalCity is required");
        }
        if (dto.getDepartureCity().equalsIgnoreCase(dto.getArrivalCity())) {
            throw new BusinessException("departureCity and arrivalCity must be different");
        }
        if (dto.getPricePerSeat() == null
                || dto.getPricePerSeat().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("pricePerSeat must be greater than 0");
        }
        if (dto.getPricePerSeat().compareTo(new BigDecimal("9999.99")) > 0) {
            throw new BusinessException("pricePerSeat cannot exceed 9999.99");
        }
    }
}
