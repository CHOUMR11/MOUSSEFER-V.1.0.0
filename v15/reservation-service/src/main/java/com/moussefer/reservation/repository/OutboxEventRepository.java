package com.moussefer.reservation.repository;

import com.moussefer.reservation.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByProcessedAtIsNull();

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.processedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markAsProcessed(String id);
}