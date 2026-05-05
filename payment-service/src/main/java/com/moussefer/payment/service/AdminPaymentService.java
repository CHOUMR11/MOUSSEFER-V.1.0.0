package com.moussefer.payment.service;

import com.moussefer.payment.dto.response.PaymentResponse;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.exception.BusinessException;
import com.moussefer.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(PaymentStatus status, String passengerId,
                                              LocalDateTime from, LocalDateTime to,
                                              Pageable pageable) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status      != null) predicates.add(cb.equal(root.get("status"),      status));
            if (passengerId != null) predicates.add(cb.equal(root.get("passengerId"), passengerId));
            if (from        != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to          != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return paymentRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(String paymentId) {
        return toResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Payment not found: " + paymentId)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFinancialStats(LocalDateTime from, LocalDateTime to) {
        List<Payment> payments = paymentRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to   != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        BigDecimal totalRevenue   = BigDecimal.ZERO;
        BigDecimal totalRefunded  = BigDecimal.ZERO;
        long countSucceeded = 0, countRefunded = 0, countPending = 0, countFailed = 0;

        for (Payment p : payments) {
            switch (p.getStatus()) {
                case SUCCEEDED -> { totalRevenue  = totalRevenue.add(p.getAmount());  countSucceeded++; }
                case REFUNDED  -> { totalRefunded = totalRefunded.add(p.getAmount()); countRefunded++;  }
                case PENDING   -> countPending++;
                case FAILED    -> countFailed++;
            }
        }

        BigDecimal netRevenue = totalRevenue.subtract(totalRefunded);
        BigDecimal avg = countSucceeded > 0
                ? totalRevenue.divide(BigDecimal.valueOf(countSucceeded), 2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;

        return Map.of(
                "totalRevenue",          totalRevenue,
                "totalRefunded",         totalRefunded,
                "netRevenue",            netRevenue,
                "countSucceeded",        countSucceeded,
                "countRefunded",         countRefunded,
                "countPending",          countPending,
                "countFailed",           countFailed,
                "avgTransactionAmount",  avg
        );
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .reservationId(p.getReservationId())
                .passengerId(p.getPassengerId())
                .driverId(p.getDriverId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .invoiceUrl(p.getInvoiceUrl())
                .promoCode(p.getPromoCode())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
