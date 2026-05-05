package com.moussefer.voyage.entity;

/**
 * How a voyage reservation came into the system.
 *
 * PLATFORM — the passenger booked via the Moussefer app/website (default)
 * PHONE    — "Hors Moussefer" — the passenger called the organizer and the
 *            organizer manually registered them from the dashboard
 * AGENCY   — "Hors Moussefer" — booked in person at the organizer's agency
 * DIRECT   — "Hors Moussefer" — other direct channel (walk-in, referral, …)
 *
 * The maquette ("Réservation" view) shows a status pill next to each row
 * indicating the source: "Moussefer" vs "Hors Moussefer" (Tél/Agence/Direct).
 */
public enum BookingSource {
    PLATFORM,
    PHONE,
    AGENCY,
    DIRECT
}
