package com.moussefer.payment.repository;

import com.moussefer.payment.entity.StripeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, String> {
    Optional<StripeWebhookEvent> findByEventId(String eventId);
}