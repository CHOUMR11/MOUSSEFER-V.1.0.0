package com.moussefer.payment.service;

import com.moussefer.payment.dto.request.CreatePromoCodeRequest;
import com.moussefer.payment.dto.request.UpdatePromoCodeRequest;
import com.moussefer.payment.dto.response.PromoCodeResponse;
import com.moussefer.payment.dto.response.PromoCodeStatsResponse;
import com.moussefer.payment.entity.PromoCode;
import com.moussefer.payment.exception.BusinessException;
import com.moussefer.payment.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    // ── CREATE ──────────────────────────────────────────────────────────
    @Transactional
    public PromoCodeResponse create(String adminId, CreatePromoCodeRequest req) {
        if (promoCodeRepository.existsByCodeIgnoreCase(req.getCode())) {
            throw new BusinessException("Promo code already exists: " + req.getCode());
        }
        if (req.getValidFrom() != null && req.getValidUntil() != null
                && req.getValidFrom().isAfter(req.getValidUntil())) {
            throw new BusinessException("validFrom must be before validUntil");
        }

        PromoCode promo = PromoCode.builder()
                .code(req.getCode().toUpperCase())
                .discountType(req.getDiscountType())
                .discountValue(req.getDiscountValue())
                .validFrom(req.getValidFrom())
                .validUntil(req.getValidUntil())
                .maxUses(req.getMaxUses())
                .minAmount(req.getMinAmount())
                .applicableTo(req.getApplicableTo() != null ? req.getApplicableTo().toUpperCase() : "ALL")
                .active(true)
                .build();

        promo = promoCodeRepository.save(promo);
        log.info("Promo code created: {} by admin {}", promo.getCode(), adminId);
        return PromoCodeResponse.from(promo);
    }

    // ── READ ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<PromoCodeResponse> listAll(Pageable pageable) {
        return promoCodeRepository.findAll(pageable).map(PromoCodeResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PromoCodeResponse> listActive(Pageable pageable) {
        return promoCodeRepository.findCurrentlyValid(LocalDateTime.now(), pageable)
                .map(PromoCodeResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PromoCodeResponse> listExpiredOrExhausted(Pageable pageable) {
        return promoCodeRepository.findExpiredOrExhausted(LocalDateTime.now(), pageable)
                .map(PromoCodeResponse::from);
    }

    @Transactional(readOnly = true)
    public PromoCodeResponse getById(String id) {
        return PromoCodeResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PromoCodeStatsResponse getStats() {
        LocalDateTime now = LocalDateTime.now();
        long total    = promoCodeRepository.count();
        long active   = promoCodeRepository.countByActive(true);
        long expired  = promoCodeRepository.countExpired(now);
        long exhaust  = promoCodeRepository.countExhausted();
        long usages   = promoCodeRepository.sumTotalUsages();
        return PromoCodeStatsResponse.builder()
                .totalCreated(total)
                .totalActive(active)
                .totalExpired(expired)
                .totalExhausted(exhaust)
                .totalUsages(usages)
                .build();
    }

    // ── UPDATE ───────────────────────────────────────────────────────────
    @Transactional
    public PromoCodeResponse update(String adminId, String id, UpdatePromoCodeRequest req) {
        PromoCode promo = findOrThrow(id);

        if (req.getDiscountType()  != null) promo.setDiscountType(req.getDiscountType());
        if (req.getDiscountValue() != null) promo.setDiscountValue(req.getDiscountValue());
        if (req.getValidFrom()     != null) promo.setValidFrom(req.getValidFrom());
        if (req.getValidUntil()    != null) promo.setValidUntil(req.getValidUntil());
        if (req.getMaxUses()       != null) promo.setMaxUses(req.getMaxUses());
        if (req.getMinAmount()     != null) promo.setMinAmount(req.getMinAmount());
        if (req.getApplicableTo()  != null) promo.setApplicableTo(req.getApplicableTo().toUpperCase());
        if (req.getActive()        != null) promo.setActive(req.getActive());

        if (promo.getValidFrom() != null && promo.getValidUntil() != null
                && promo.getValidFrom().isAfter(promo.getValidUntil())) {
            throw new BusinessException("validFrom must be before validUntil");
        }

        promo = promoCodeRepository.save(promo);
        log.info("Promo code updated: {} by admin {}", promo.getCode(), adminId);
        return PromoCodeResponse.from(promo);
    }

    // ── ACTIVATE / DEACTIVATE ────────────────────────────────────────────
    @Transactional
    public PromoCodeResponse setActive(String adminId, String id, boolean active) {
        PromoCode promo = findOrThrow(id);
        promo.setActive(active);
        promo = promoCodeRepository.save(promo);
        log.info("Promo code {} {}: {}", promo.getCode(), active ? "activated" : "deactivated", id);
        return PromoCodeResponse.from(promo);
    }

    // ── DELETE ───────────────────────────────────────────────────────────
    @Transactional
    public void delete(String adminId, String id) {
        PromoCode promo = findOrThrow(id);
        if (promo.getUsedCount() > 0) {
            throw new BusinessException(
                    "Cannot delete a promo code that has been used (" + promo.getUsedCount() + " times). Deactivate it instead.");
        }
        promoCodeRepository.deleteById(id);
        log.info("Promo code deleted: {} by admin {}", promo.getCode(), adminId);
    }

    // ── PRIVATE HELPER ───────────────────────────────────────────────────
    private PromoCode findOrThrow(String id) {
        return promoCodeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Promo code not found: " + id));
    }
}