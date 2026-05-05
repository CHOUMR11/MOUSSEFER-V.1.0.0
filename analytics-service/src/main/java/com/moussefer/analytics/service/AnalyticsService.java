package com.moussefer.analytics.service;

import com.moussefer.analytics.dto.response.DashboardResponse;
import com.moussefer.analytics.entity.TripEvent;
import com.moussefer.analytics.repository.TripEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final TripEventRepository tripEventRepository;

    public DashboardResponse getDashboard() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return DashboardResponse.builder()
                .totalBookings(tripEventRepository.countByEventType("BOOKED"))
                .totalCancellations(tripEventRepository.countByEventType("CANCELLED"))
                .totalCompleted(tripEventRepository.countByEventType("COMPLETED"))
                .totalEscalated(tripEventRepository.countByEventType("ESCALATED"))
                .totalTrajetsPublished(tripEventRepository.countByEventType("TRAJET_PUBLISHED"))
                .totalNewUsers(tripEventRepository.countByEventType("USER_REGISTERED"))
                .totalRevenue(tripEventRepository.totalRevenue())
                .bookingsToday(tripEventRepository.countByEventTypeAndRecordedAtBetween("BOOKED", startOfDay, now))
                .bookingsThisMonth(tripEventRepository.countByEventTypeAndRecordedAtBetween("BOOKED", startOfMonth, now))
                .completedThisMonth(tripEventRepository.countByEventTypeAndRecordedAtBetween("COMPLETED", startOfMonth, now))
                .revenueThisMonth(tripEventRepository.revenueInPeriod(startOfMonth, now))
                .build();
    }

    public void recordEvent(String eventType, String trajetId, String reservationId,
                            String userId, String driverId,
                            String originCity, String destinationCity, Double revenue) {
        TripEvent event = TripEvent.builder()
                .eventType(eventType)
                .trajetId(trajetId)
                .reservationId(reservationId)
                .userId(userId)
                .driverId(driverId)
                .originCity(originCity)
                .destinationCity(destinationCity)
                .revenue(revenue)
                .build();
        tripEventRepository.save(event);
        log.info("Recorded analytics event: {} for trajet {}", eventType, trajetId);
    }

    // ─── CSV Export ──────────────────────────────────────────────────
    public byte[] exportCsv(String eventType, String fromDate, String toDate) {
        LocalDateTime from = fromDate != null ? LocalDate.parse(fromDate).atStartOfDay() : LocalDateTime.now().minusMonths(1);
        LocalDateTime to = toDate != null ? LocalDate.parse(toDate).plusDays(1).atStartOfDay() : LocalDateTime.now();

        List<TripEvent> events;
        if (eventType != null && !eventType.isBlank()) {
            events = tripEventRepository.findByEventTypeAndRecordedAtBetween(eventType.toUpperCase(), from, to);
        } else {
            events = tripEventRepository.findByRecordedAtBetween(from, to);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("id,eventType,trajetId,reservationId,userId,driverId,originCity,destinationCity,revenue,recordedAt\n");
        for (TripEvent e : events) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    e.getId(), e.getEventType(),
                    e.getTrajetId() != null ? e.getTrajetId() : "",
                    e.getReservationId() != null ? e.getReservationId() : "",
                    e.getUserId() != null ? e.getUserId() : "",
                    e.getDriverId() != null ? e.getDriverId() : "",
                    e.getOriginCity() != null ? e.getOriginCity() : "",
                    e.getDestinationCity() != null ? e.getDestinationCity() : "",
                    e.getRevenue() != null ? e.getRevenue() : "",
                    e.getRecordedAt()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─── Spike Alerts ────────────────────────────────────────────────
    public List<Map<String, Object>> detectSpikeAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        LocalDateTime now = LocalDateTime.now();

        long cancellationsLastHour = tripEventRepository.countByEventTypeAndRecordedAtBetween("CANCELLED", lastHour, now);
        long cancellationsLast24h = tripEventRepository.countByEventTypeAndRecordedAtBetween("CANCELLED", last24h, now);
        double avgCancellationsPerHour = cancellationsLast24h / 24.0;

        if (cancellationsLastHour > avgCancellationsPerHour * 3 && cancellationsLastHour > 5) {
            alerts.add(Map.of(
                    "type", "CANCELLATION_SPIKE",
                    "severity", "HIGH",
                    "message", String.format("Pic d'annulations : %d dans la dernière heure (moyenne: %.1f/h)", cancellationsLastHour, avgCancellationsPerHour),
                    "value", cancellationsLastHour,
                    "threshold", Math.round(avgCancellationsPerHour * 3)
            ));
        }

        long paymentFailuresLastHour = tripEventRepository.countByEventTypeAndRecordedAtBetween("PAYMENT_FAILED", lastHour, now);
        if (paymentFailuresLastHour > 3) {
            alerts.add(Map.of(
                    "type", "PAYMENT_FAILURE_SPIKE",
                    "severity", "HIGH",
                    "message", String.format("Pic d'échecs de paiement : %d dans la dernière heure", paymentFailuresLastHour),
                    "value", paymentFailuresLastHour,
                    "threshold", 3
            ));
        }

        long escalationsLastHour = tripEventRepository.countByEventTypeAndRecordedAtBetween("ESCALATED", lastHour, now);
        if (escalationsLastHour > 5) {
            alerts.add(Map.of(
                    "type", "ESCALATION_SPIKE",
                    "severity", "MEDIUM",
                    "message", String.format("Pic d'escalades : %d dans la dernière heure — chauffeurs non réactifs", escalationsLastHour),
                    "value", escalationsLastHour,
                    "threshold", 5
            ));
        }

        long bookingsLastHour = tripEventRepository.countByEventTypeAndRecordedAtBetween("BOOKED", lastHour, now);
        long bookingsAvgPerHour = tripEventRepository.countByEventTypeAndRecordedAtBetween("BOOKED", last24h, now) / 24;
        if (bookingsLastHour > bookingsAvgPerHour * 3 && bookingsLastHour > 10) {
            alerts.add(Map.of(
                    "type", "HIGH_DEMAND",
                    "severity", "INFO",
                    "message", String.format("Forte demande : %d réservations dans la dernière heure (moyenne: %d/h)", bookingsLastHour, bookingsAvgPerHour),
                    "value", bookingsLastHour,
                    "threshold", bookingsAvgPerHour * 3
            ));
        }

        if (alerts.isEmpty()) {
            alerts.add(Map.of("type", "NONE", "severity", "OK", "message", "Aucune alerte active"));
        }
        return alerts;
    }

    // ─── Top Routes ──────────────────────────────────────────────────
    public List<Map<String, Object>> getTopRoutes() {
        List<Object[]> rows = tripEventRepository.topRoutes();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                result.add(Map.of(
                        "originCity", row[0],
                        "destinationCity", row[1],
                        "totalBookings", row[2]
                ));
            }
            if (result.size() >= 10) break;
        }
        return result;
    }
}
