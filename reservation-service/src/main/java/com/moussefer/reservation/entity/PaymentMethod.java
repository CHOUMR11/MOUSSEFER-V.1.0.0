package com.moussefer.reservation.entity;

/**
 * Payment method for a reservation.
 *
 * - ONLINE: standard Stripe payment flow (default for passenger self-bookings)
 * - CASH: driver collects payment on-site at the station — used when the driver
 *   manually adds a passenger to their trajet (e.g. a walk-in passenger at the
 *   louage station). The reservation is confirmed immediately without Stripe.
 */
public enum PaymentMethod {
    ONLINE,
    CASH
}
