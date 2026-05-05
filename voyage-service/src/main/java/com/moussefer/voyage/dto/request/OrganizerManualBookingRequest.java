package com.moussefer.voyage.dto.request;

import com.moussefer.voyage.entity.BookingSource;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for organizer-initiated manual reservation ("Hors Moussefer").
 *
 * The organizer uses this when a client booked via phone, at the agency,
 * or through any direct channel. The passenger may not have a Moussefer
 * account — the organizer records the contact info manually.
 */
@Data
public class OrganizerManualBookingRequest {

    @NotBlank(message = "voyageId is required")
    private String voyageId;

    @Min(value = 1, message = "At least 1 seat required")
    @Max(value = 20, message = "Cannot book more than 20 seats in one manual booking")
    private int seatsReserved;

    @NotBlank
    @Size(min = 2, max = 100)
    private String passengerName;

    @NotBlank
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Invalid phone number format")
    private String passengerPhone;

    /**
     * Source of the booking — PHONE / AGENCY / DIRECT.
     * PLATFORM is not accepted here (use normal /reserve endpoint instead).
     */
    @NotNull(message = "Booking source is required (PHONE, AGENCY, or DIRECT)")
    private BookingSource bookingSource;

    /**
     * Optional deposit already collected by the organizer. If null or 0,
     * the booking is recorded as UNPAID. If equal to total, marked PAID.
     * Otherwise marked DEPOSIT.
     */
    private Double depositAmount;
}
