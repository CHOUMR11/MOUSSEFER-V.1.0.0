package com.moussefer.analytics.repository;

import com.moussefer.analytics.entity.TripEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TripEventRepository extends JpaRepository<TripEvent, String> {
    long countByEventType(String eventType);
    long countByEventTypeAndRecordedAtBetween(String eventType, LocalDateTime from, LocalDateTime to);

    List<TripEvent> findByRecordedAtBetween(LocalDateTime from, LocalDateTime to);
    List<TripEvent> findByEventTypeAndRecordedAtBetween(String eventType, LocalDateTime from, LocalDateTime to);

    @Query("SELECT SUM(t.revenue) FROM TripEvent t WHERE t.eventType = 'COMPLETED'")
    Double totalRevenue();

    @Query("SELECT SUM(t.revenue) FROM TripEvent t WHERE t.eventType = 'COMPLETED' AND t.recordedAt BETWEEN :from AND :to")
    Double revenueInPeriod(LocalDateTime from, LocalDateTime to);

    @Query("SELECT t.originCity, COUNT(t) FROM TripEvent t GROUP BY t.originCity ORDER BY COUNT(t) DESC")
    List<Object[]> topOriginCities();

    @Query("SELECT t.originCity, t.destinationCity, COUNT(t) FROM TripEvent t WHERE t.eventType = 'BOOKED' AND t.originCity IS NOT NULL AND t.destinationCity IS NOT NULL GROUP BY t.originCity, t.destinationCity ORDER BY COUNT(t) DESC")
    List<Object[]> topRoutes();
}
