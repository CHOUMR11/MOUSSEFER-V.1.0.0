package com.moussefer.payment.service;

import com.moussefer.payment.dto.response.PromoCodeValidationResponse;
import com.moussefer.payment.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    @Transactional(readOnly = true)
    public PromoCodeValidationResponse validatePromoCode(String code, BigDecimal originalAmount, String reservationType) {
        var promo = promoCodeRepository.findByCodeIgnoreCase(code).orElse(null);
        if (promo == null) {
            return PromoCodeValidationResponse.invalid("Code promo invalide");
        }
        if (!promo.isValid()) {
            return PromoCodeValidationResponse.invalid("Code promo expiré ou inactif");
        }
        if (promo.getMinAmount() != null && originalAmount.compareTo(promo.getMinAmount()) < 0) {
            return PromoCodeValidationResponse.invalid("Montant minimum non atteint (minimum " + promo.getMinAmount() + " €)");
        }
        if (promo.getApplicableTo() != null && !"ALL".equals(promo.getApplicableTo())
                && !promo.getApplicableTo().equalsIgnoreCase(reservationType)) {
            return PromoCodeValidationResponse.invalid("Ce code n'est pas applicable à ce type de réservation");
        }

        BigDecimal discountedAmount = promo.applyDiscount(originalAmount);
        BigDecimal discountAmount = originalAmount.subtract(discountedAmount);

        return PromoCodeValidationResponse.valid(promo.getCode(), discountAmount, discountedAmount,
                promo.getDiscountType(), promo.getDiscountValue());
    }

    @Transactional
    public void markPromoCodeUsed(String code) {
        promoCodeRepository.findByCodeIgnoreCase(code).ifPresent(promo -> {
            int updated = promoCodeRepository.atomicIncrementUsedCount(promo.getId());
            if (updated > 0) {
                log.info("Promo code {} used (atomic increment), total uses incremented", code);
            } else {
                log.warn("Promo code {} could not be incremented (max uses reached or concurrent update)", code);
            }
        });
    }
}