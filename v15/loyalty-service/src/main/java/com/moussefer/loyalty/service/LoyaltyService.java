package com.moussefer.loyalty.service;

import com.moussefer.loyalty.entity.LoyaltyAccount;
import com.moussefer.loyalty.entity.LoyaltyTier;
import com.moussefer.loyalty.entity.PointTransaction;
import com.moussefer.loyalty.repository.LoyaltyAccountRepository;
import com.moussefer.loyalty.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private static final int POINTS_PER_TRIP = 10;

    private final LoyaltyAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;

    public LoyaltyAccount getOrCreate(String userId) {
        return accountRepository.findByUserId(userId).orElseGet(() -> {
            log.info("Creating new loyalty account for user {}", userId);
            return accountRepository.save(LoyaltyAccount.builder().userId(userId).build());
        });
    }

    @Transactional
    public LoyaltyAccount earnPoints(String userId, int points, String reason, String referenceId) {
        LoyaltyAccount account = getOrCreate(userId);
        account.setPoints(account.getPoints() + points);
        account.setTotalEarned(account.getTotalEarned() + points);
        account.setTier(computeTier(account.getTotalEarned()));
        accountRepository.save(account);

        transactionRepository.save(PointTransaction.builder()
                .accountId(account.getId())
                .userId(userId)
                .pointsDelta(points)
                .reason(reason)
                .referenceId(referenceId)
                .build());

        log.info("User {} earned {} points ({}). Total: {}", userId, points, reason, account.getPoints());
        return account;
    }

    @Transactional
    public LoyaltyAccount earnTripPoints(String userId, String reservationId) {
        return earnPoints(userId, POINTS_PER_TRIP, "TRIP_COMPLETED", reservationId);
    }

    @Transactional
    public LoyaltyAccount redeemPoints(String userId, int points, String referenceId) {
        LoyaltyAccount account = getOrCreate(userId);
        if (account.getPoints() < points) {
            throw new IllegalArgumentException("Insufficient points. Available: " + account.getPoints());
        }
        account.setPoints(account.getPoints() - points);
        account.setTotalRedeemed(account.getTotalRedeemed() + points);
        accountRepository.save(account);

        transactionRepository.save(PointTransaction.builder()
                .accountId(account.getId())
                .userId(userId)
                .pointsDelta(-points)
                .reason("REDEEMED")
                .referenceId(referenceId)
                .build());

        log.info("User {} redeemed {} points. Remaining: {}", userId, points, account.getPoints());
        return account;
    }

    public Page<PointTransaction> getHistory(String userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    private LoyaltyTier computeTier(int totalEarned) {
        if (totalEarned >= 5000) return LoyaltyTier.PLATINUM;
        if (totalEarned >= 2000) return LoyaltyTier.GOLD;
        if (totalEarned >= 500)  return LoyaltyTier.SILVER;
        return LoyaltyTier.BRONZE;
    }
}
