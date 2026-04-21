package com.moussefer.voyage.entity;

public enum ReservationVoyageStatus {
    PENDING_ORGANIZER,
    PENDING_PAYMENT,   // ✅ new status
    CONFIRMED,
    CANCELLED
}