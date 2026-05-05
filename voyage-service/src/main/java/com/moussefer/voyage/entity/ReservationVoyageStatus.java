package com.moussefer.voyage.entity;

public enum ReservationVoyageStatus {
    PENDING_ORGANIZER,
    PENDING_PAYMENT,  // FIX #13: accepted by organizer, awaiting Stripe payment  // en attente de validation par l'organisateur
    CONFIRMED,
    CANCELLED
}