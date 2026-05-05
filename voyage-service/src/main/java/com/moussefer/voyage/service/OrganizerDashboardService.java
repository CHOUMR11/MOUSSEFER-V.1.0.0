package com.moussefer.voyage.service;

import com.moussefer.voyage.dto.request.OrganizerManualBookingRequest;
import com.moussefer.voyage.entity.*;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.repository.VoyageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for the organizer dashboard.
 *
 * Maps directly to the maquettes:
 *   - Vue d'ensemble        → overview()
 *   - Finances et factures  → finances()
 *   - Clients               → clients()
 *   - Statistiques          → statistics()
 *   - Réservation           → listed separately via VoyageController endpoints
 *   - Manual booking        → createManualBooking()
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizerDashboardService {

    private final VoyageRepository voyageRepository;
    private final ReservationVoyageRepository reservationRepository;

    // ─────────── Vue d'ensemble ───────────
    @Transactional(readOnly = true)
    public Map<String, Object> overview(String organizerId) {
        List<Voyage> voyages = voyageRepository.findByOrganizerId(organizerId);
        int totalVoyages = voyages.size();
        int activeVoyages = (int) voyages.stream()
                .filter(v -> v.getStatus() == VoyageStatus.OPEN
                          || v.getStatus() == VoyageStatus.OPEN).count();

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<ReservationVoyage> thisMonthReservations =
                reservationRepository.findByOrganizerInPeriod(organizerId, monthStart, now);

        int confirmedThisMonth = (int) thisMonthReservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CONFIRMED
                          || r.getStatus() == ReservationVoyageStatus.CONFIRMED).count();
        double revenueThisMonth = thisMonthReservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CONFIRMED)
                .mapToDouble(r -> r.getTotalPrice() != null ? r.getTotalPrice() : 0.0)
                .sum();
        int totalSeatsSold = thisMonthReservations.stream()
                .filter(r -> r.getStatus() != ReservationVoyageStatus.CANCELLED
                          && r.getStatus() != ReservationVoyageStatus.CANCELLED)
                .mapToInt(ReservationVoyage::getSeatsReserved).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("organizerId", organizerId);
        result.put("totalVoyages", totalVoyages);
        result.put("activeVoyages", activeVoyages);
        result.put("confirmedReservationsThisMonth", confirmedThisMonth);
        result.put("revenueThisMonth", revenueThisMonth);
        result.put("seatsSoldThisMonth", totalSeatsSold);
        result.put("quickActions", List.of(
                "Publish new voyage",
                "Add manual booking (Hors Moussefer)",
                "View recent invoices",
                "Open client list"));
        return result;
    }

    // ─────────── Finances & factures ───────────
    // Matches the maquette: 4 KPI cards (Revenus total / Payés / Acomptes reçus / Non encaissé)
    // + Revenus mensuels bar chart + Factures récentes list
    @Transactional(readOnly = true)
    public Map<String, Object> finances(String organizerId) {
        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<ReservationVoyage> reservations =
                reservationRepository.findByOrganizerInPeriod(organizerId, yearStart, now);

        double revenueTotal = 0;     // sum of totalPrice of non-cancelled reservations
        double paid = 0;             // fully paid
        double depositsReceived = 0; // partial deposits collected
        double notCollected = 0;     // unpaid amounts still expected

        for (ReservationVoyage r : reservations) {
            if (r.getStatus() == ReservationVoyageStatus.CANCELLED
                    || r.getStatus() == ReservationVoyageStatus.CANCELLED) continue;
            double total = r.getTotalPrice() != null ? r.getTotalPrice() : 0.0;
            revenueTotal += total;

            if (r.isManualBooking()) {
                String state = r.getPaymentState() != null ? r.getPaymentState() : "UNPAID";
                double deposit = r.getDepositAmount() != null ? r.getDepositAmount() : 0.0;
                switch (state) {
                    case "PAID"    -> paid += total;
                    case "DEPOSIT" -> {
                        depositsReceived += deposit;
                        notCollected += (total - deposit);
                    }
                    default        -> notCollected += total;
                }
            } else {
                if (r.getStatus() == ReservationVoyageStatus.CONFIRMED) paid += total;
                else notCollected += total;
            }
        }

        // Monthly revenue — last 12 months, labels + values for the bar chart
        List<Map<String, Object>> monthlyRevenue = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            YearMonth ym = cursor.plusMonths(i);
            LocalDateTime mStart = ym.atDay(1).atStartOfDay();
            LocalDateTime mEnd = ym.atEndOfMonth().atTime(23, 59, 59);
            double monthRevenue = reservations.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(mStart) && !r.getCreatedAt().isAfter(mEnd))
                    .filter(r -> r.getStatus() != ReservationVoyageStatus.CANCELLED
                              && r.getStatus() != ReservationVoyageStatus.CANCELLED)
                    .mapToDouble(r -> r.getTotalPrice() != null ? r.getTotalPrice() : 0.0).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH));
            m.put("year", ym.getYear());
            m.put("revenue", monthRevenue);
            monthlyRevenue.add(m);
        }

        // Recent invoices — most recent 10 paid or confirmed reservations
        List<Map<String, Object>> recentInvoices = reservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CONFIRMED
                          || r.getStatus() == ReservationVoyageStatus.CONFIRMED)
                .sorted(Comparator.comparing(ReservationVoyage::getCreatedAt).reversed())
                .limit(10)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reservationId", r.getId());
                    m.put("voyageId", r.getVoyageId());
                    m.put("passengerName", r.isManualBooking() ? r.getManualPassengerName() : r.getPassengerId());
                    m.put("amount", r.getTotalPrice());
                    m.put("date", r.getCreatedAt());
                    m.put("status", r.getStatus().name());
                    m.put("invoiceUrl", r.getInvoiceUrl());
                    m.put("bookingSource", r.getBookingSource().name());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("organizerId", organizerId);
        result.put("revenueTotal", round2(revenueTotal));
        result.put("paid", round2(paid));
        result.put("depositsReceived", round2(depositsReceived));
        result.put("notCollected", round2(notCollected));
        result.put("paidPercentage", revenueTotal > 0 ? round2((paid / revenueTotal) * 100) : 0);
        result.put("depositsPercentage", revenueTotal > 0 ? round2((depositsReceived / revenueTotal) * 100) : 0);
        result.put("notCollectedPercentage", revenueTotal > 0 ? round2((notCollected / revenueTotal) * 100) : 0);
        result.put("monthlyRevenue", monthlyRevenue);
        result.put("recentInvoices", recentInvoices);
        return result;
    }

    // ─────────── Clients ───────────
    // Maquette: "Réservations — 8 semaines" weekly bar chart + "Top destinations" bars
    @Transactional(readOnly = true)
    public Map<String, Object> clients(String organizerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eightWeeksAgo = now.minusWeeks(8);
        List<ReservationVoyage> recent =
                reservationRepository.findByOrganizerInPeriod(organizerId, eightWeeksAgo, now);

        // Weekly buckets — last 8 weeks
        List<Map<String, Object>> weekly = new ArrayList<>();
        for (int w = 7; w >= 0; w--) {
            LocalDateTime weekStart = now.minusWeeks(w + 1L);
            LocalDateTime weekEnd = now.minusWeeks(w);
            long count = recent.stream()
                    .filter(r -> !r.getCreatedAt().isBefore(weekStart) && r.getCreatedAt().isBefore(weekEnd))
                    .filter(r -> r.getStatus() != ReservationVoyageStatus.CANCELLED)
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("weekLabel", "S-" + w);
            m.put("reservations", count);
            weekly.add(m);
        }

        // Top destinations — aggregate arrivalCity from the voyage
        Set<String> voyageIds = recent.stream().map(ReservationVoyage::getVoyageId).collect(Collectors.toSet());
        Map<String, Long> destinationCounts = new HashMap<>();
        if (!voyageIds.isEmpty()) {
            List<Voyage> voyages = voyageRepository.findAllById(voyageIds);
            Map<String, String> voyageToCity = voyages.stream()
                    .collect(Collectors.toMap(Voyage::getId, Voyage::getArrivalCity, (a, b) -> a));
            for (ReservationVoyage r : recent) {
                String city = voyageToCity.get(r.getVoyageId());
                if (city != null && r.getStatus() != ReservationVoyageStatus.CANCELLED) {
                    destinationCounts.merge(city, (long) r.getSeatsReserved(), Long::sum);
                }
            }
        }
        List<Map<String, Object>> topDestinations = destinationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("destination", e.getKey());
                    m.put("seatsSold", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        // Total clients (unique passengers or unique phone numbers for manual)
        Set<String> uniqueClients = new HashSet<>();
        for (ReservationVoyage r : recent) {
            if (r.isManualBooking() && r.getManualPassengerPhone() != null) {
                uniqueClients.add("m:" + r.getManualPassengerPhone());
            } else if (r.getPassengerId() != null) {
                uniqueClients.add("p:" + r.getPassengerId());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("organizerId", organizerId);
        result.put("uniqueClientsLast8Weeks", uniqueClients.size());
        result.put("totalReservationsLast8Weeks", recent.size());
        result.put("weeklyReservations", weekly);
        result.put("topDestinations", topDestinations);
        return result;
    }

    // ─────────── Statistiques ───────────
    @Transactional(readOnly = true)
    public Map<String, Object> statistics(String organizerId) {
        LocalDateTime yearStart = LocalDate.now().withDayOfYear(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<ReservationVoyage> reservations =
                reservationRepository.findByOrganizerInPeriod(organizerId, yearStart, now);

        long total = reservations.size();
        long confirmed = reservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CONFIRMED
                          || r.getStatus() == ReservationVoyageStatus.CONFIRMED).count();
        long cancelled = reservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CANCELLED).count();
        long refused = reservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CANCELLED).count();

        // Source breakdown
        Map<String, Long> bySource = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getBookingSource().name(), Collectors.counting()));

        // Conversion rate
        double conversionRate = total > 0 ? (confirmed * 100.0) / total : 0;

        // Average revenue per reservation
        double avgRevenue = reservations.stream()
                .filter(r -> r.getStatus() == ReservationVoyageStatus.CONFIRMED)
                .mapToDouble(r -> r.getTotalPrice() != null ? r.getTotalPrice() : 0.0)
                .average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("organizerId", organizerId);
        result.put("totalReservationsThisYear", total);
        result.put("confirmed", confirmed);
        result.put("cancelled", cancelled);
        result.put("refused", refused);
        result.put("conversionRate", round2(conversionRate));
        result.put("averageRevenue", round2(avgRevenue));
        result.put("bookingsBySource", bySource);
        return result;
    }

    // ─────────── Manual booking "Hors Moussefer" ───────────
    @Transactional
    public ReservationVoyage createManualBooking(String organizerId, OrganizerManualBookingRequest req) {
        Voyage voyage = voyageRepository.findById(req.getVoyageId())
                .orElseThrow(() -> new RuntimeException("Voyage not found: " + req.getVoyageId()));
        if (!organizerId.equals(voyage.getOrganizerId())) {
            throw new RuntimeException("You can only add bookings to your own voyages");
        }
        if (voyage.getStatus() != VoyageStatus.OPEN && voyage.getStatus() != VoyageStatus.OPEN) {
            throw new RuntimeException("Voyage is not open for booking (status: " + voyage.getStatus() + ")");
        }
        if (req.getBookingSource() == BookingSource.PLATFORM) {
            throw new RuntimeException("Manual bookings cannot use source=PLATFORM. Use /reserve instead.");
        }
        if (req.getSeatsReserved() > voyage.getAvailableSeats()) {
            throw new RuntimeException(
                    "Not enough available seats. Requested: " + req.getSeatsReserved()
                    + ", available: " + voyage.getAvailableSeats());
        }

        double total = voyage.getPricePerSeat() * req.getSeatsReserved();
        double deposit = req.getDepositAmount() != null ? req.getDepositAmount() : 0.0;
        String paymentState = "UNPAID";
        if (deposit >= total) paymentState = "PAID";
        else if (deposit > 0) paymentState = "DEPOSIT";

        ReservationVoyage r = ReservationVoyage.builder()
                .voyageId(req.getVoyageId())
                .passengerId("manual_" + UUID.randomUUID().toString().substring(0, 8))
                .seatsReserved(req.getSeatsReserved())
                .totalPrice(total)
                .status(ReservationVoyageStatus.CONFIRMED)
                .bookingSource(req.getBookingSource())
                .manualBooking(true)
                .manualPassengerName(req.getPassengerName())
                .manualPassengerPhone(req.getPassengerPhone())
                .paymentState(paymentState)
                .depositAmount(deposit > 0 ? deposit : null)
                .confirmedAt(LocalDateTime.now())
                .paidAt("PAID".equals(paymentState) ? LocalDateTime.now() : null)
                .build();
        r = reservationRepository.save(r);

        // Decrement voyage seats
        voyage.setAvailableSeats(voyage.getAvailableSeats() - req.getSeatsReserved());
        voyageRepository.save(voyage);

        log.info("Organizer {} created manual booking: voyageId={}, passenger={}, source={}, state={}",
                organizerId, req.getVoyageId(), req.getPassengerName(),
                req.getBookingSource(), paymentState);
        return r;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
