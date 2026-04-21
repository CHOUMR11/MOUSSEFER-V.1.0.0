package com.moussefer.notification.repository;

import com.moussefer.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByUserIdOrderBySentAtDesc(String userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId")
    void markAllAsRead(String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
    int markOneAsRead(String id, String userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteOneByIdAndUserId(String id, String userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteAllByUserId(String userId);

    // KAFKA-01: idempotency check — avoid duplicate notifications for same event
    boolean existsByReferenceIdAndReferenceType(String referenceId, String referenceType);

}