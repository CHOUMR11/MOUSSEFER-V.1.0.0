package com.moussefer.loyalty.repository;

import com.moussefer.loyalty.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, String> {
    Optional<LoyaltyAccount> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
