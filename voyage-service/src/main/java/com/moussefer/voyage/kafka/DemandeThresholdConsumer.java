package com.moussefer.voyage.kafka;

import com.moussefer.voyage.dto.request.CreateVoyageRequest;
import com.moussefer.voyage.service.VoyageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandeThresholdConsumer {

    private final VoyageService voyageService;

    @KafkaListener(topics = "demande.threshold.reached", groupId = "voyage-service-group")
    public void onDemandeThresholdReached(Map<String, Object> event) {
        String organisateurId = (String) event.get("organisateurId");
        String departureCity = (String) event.get("departureCity");
        String arrivalCity = (String) event.get("arrivalCity");
        Object dateObj = event.get("requestedDate");
        Object seatsObj = event.get("totalSeatsReserved");

        if (organisateurId == null || departureCity == null || arrivalCity == null) {
            log.warn("Invalid demande.threshold.reached event: missing fields");
            return;
        }

        LocalDateTime departureDate = dateObj instanceof String ? LocalDateTime.parse((String) dateObj) : LocalDateTime.now().plusDays(7);
        int totalSeats = seatsObj instanceof Number ? ((Number) seatsObj).intValue() : 8;

        CreateVoyageRequest request = new CreateVoyageRequest();
        request.setDepartureCity(departureCity);
        request.setArrivalCity(arrivalCity);
        request.setDepartureDate(departureDate);
        request.setTotalSeats(totalSeats);
        request.setPricePerSeat(0.0); // prix à définir par l'organisateur

        voyageService.createVoyage(organisateurId, request);
        log.info("Voyage automatically created from demande.threshold.reached: {}", departureCity);
    }
}