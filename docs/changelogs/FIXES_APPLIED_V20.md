# MOUSSEFER V20 — On-site Priority Booking (Guichet > Online)

**Date:** April 2026
**Base:** V19 (regulated fares) + V18 (driver manual booking + dashboards) + V17 (role restrictions) + V16 FIXED

---

## The real-world problem

A louage has 7 seats. 6 are already booked. **One seat remains.** Two things happen at the same time:
1. Passenger A walks up to the driver at the station and asks to buy that last seat.
2. Passenger B is on their phone and clicks "Reserve" in the app.

The platform must not sell the same seat twice. And because louages in Tunisia are first-come-first-served at the station, **physical presence beats pending online reservation** — the person at the station wins.

V20 implements this precisely.

---

## Seat model recap

Each trajet has three seat counters:
- `totalSeats` — vehicle capacity (e.g. 8)
- `availableSeats` — seats still displayed as "free" in the app
- `reservedSeats` — seats currently on a 15-minute temporary hold for online passengers

Available to book RIGHT NOW = `availableSeats - reservedSeats`.

The 15-minute hold exists because online reservations go through `PENDING_DRIVER → ACCEPTED → PAYMENT_PENDING → CONFIRMED`. During that window, the seat is soft-locked but not yet sold.

---

## Three new driver tools

### 1. On-site seat sale (quickest path)

When the passenger pays cash on the spot and the seat is truly free (`availableSeats > 0`), the driver taps a single button in their app:

```
POST /api/v1/trajets/{id}/driver/onsite-sale?seats=1
```

Behind the scenes this runs an atomic SQL decrement:
```sql
UPDATE trajets SET available_seats = available_seats - 1
WHERE id = ? AND available_seats >= 1
```

Returns 200 OK with the updated trajet. If no seat exists (already sold out), returns 400 with a clear message.

**This is pure race protection.** The SQL condition `available_seats >= 1` guarantees only one caller wins when two concurrent sales arrive — online or on-site.

### 2. Priority override (sacrifice a pending online reservation)

Sometimes `availableSeats = 0` but `reservedSeats > 0` — meaning online passengers are in the 15-min hold window. The driver wants to seat the walk-in anyway. They call the enhanced manual booking endpoint:

```
POST /api/v1/reservations/driver/manual-booking
{
  "trajetId": "trj_abc",
  "seatsReserved": 1,
  "passengerName": "Ahmed Ben Salah",
  "passengerPhone": "+21698765432",
  "paymentMethod": "CASH",
  "overridePendingOnline": true          ← V20 flag
}
```

When this flag is **true**, the backend:
1. Finds pending reservations on the same trajet (statuses `PENDING_DRIVER`, `ACCEPTED`, `PAYMENT_PENDING`), ordered **most recent first** (youngest in-flight hold gets sacrificed first).
2. Cancels as many as needed to free up the requested seats.
3. Each cancelled passenger:
   - status → `CANCELLED`
   - `refusalReason` = "Cancelled by driver — on-site passenger took priority at the station"
   - temp seat hold released
   - Kafka event `reservation.cancelled` with `priority_override: true` → notification-service alerts them (SMS / email / push), and if they already paid, a refund is triggered automatically via the existing refund flow.
4. Creates the walk-in booking at `CONFIRMED`.

When the flag is **false** (default), the booking fails cleanly with:
> "Not enough seats available. To give priority to the walk-in passenger over pending online reservations, retry with `overridePendingOnline=true`."

This forces the driver UI to show an explicit confirmation dialog before overriding someone else's reservation — no accidental cancellations.

### 3. Manual seat count correction

Sometimes reality diverges from the app (a passenger slipped in without paying, a seat broke, the driver gave a freebie to a relative). The driver can re-sync:

```
PATCH /api/v1/trajets/{id}/driver/update-seats?availableSeats=3
```

Clamps to `[0, totalSeats − reservedSeats]` server-side so the driver can never over-inflate or go below what's already held.

---

## Why this solves the double-booking problem

| Scenario | Outcome |
|---|---|
| 1 seat free, online clicks first | Online wins the temp hold. If driver tries onsite-sale → fails with 400 "already held". Driver sees the walk-in must wait. |
| 1 seat free, driver clicks first | onsite-sale succeeds atomically. Online user sees "seat already taken" when they submit. No double booking. |
| 0 seats free, 1 in temp hold, driver wants walk-in in | Driver sets `overridePendingOnline=true`. Online user loses their hold, gets notified + refunded if they paid. Walk-in gets the seat. |
| 2 drivers try same action concurrently | SQL condition `available_seats >= N` means exactly one UPDATE succeeds. The other gets 0 rows modified and returns a clear error. |

---

## Files changed

### trajet-service

**Repository:** `TrajetRepository.java`
- `decrementForOnsiteSale(id, seats)` — atomic seat decrement for on-site sales
- `setAvailableSeats(id, newAvailable)` — clamped manual seat count correction

**Controller:** `TrajetController.java`
- `POST /{id}/driver/onsite-sale` — on-site atomic sale (DRIVER only)
- `PATCH /{id}/driver/update-seats` — manual seat count sync (DRIVER only)
- `PATCH /internal/{id}/onsite-sale` — called by reservation-service for priority override flow

**Service:** `TrajetService.java`
- `driverOnsiteSeatSale(driverId, trajetId, seats)` — verify ownership + atomic decrement + auto-promote next in queue if full
- `driverUpdateAvailableSeats(driverId, trajetId, newAvailable)` — with clamping
- `internalOnsiteSeatSale(trajetId, seats)` — called by reservation-service

### reservation-service

**Repository:** `ReservationRepository.java`
- `findCancellablePendingByTrajet(trajetId)` — fetches cancellable pending reservations ordered by most-recent first

**DTO:** `DriverManualBookingRequest.java`
- New `Boolean overridePendingOnline` field (default false)

**Service:** `ReservationService.java`
- `driverManualBooking(...)` — enhanced with V20 priority override logic
- `cancelPendingForPriorityOverride(reservation, driverId)` — new helper that cancels a pending online reservation, releases seat hold, publishes Kafka event with `priority_override: true` flag, auto-refunds if already paid

---

## Example end-to-end flow

**Initial state:** trajet trj_12 has totalSeats=7, availableSeats=1, reservedSeats=0.

**Step 1 —** Passenger B opens the app and clicks "Reserve 1 seat" at 09:01:15. Backend creates `res_B` with status `PENDING_DRIVER` and calls `POST /internal/{id}/reserve-temp?seats=1`. Now trajet has availableSeats=1, reservedSeats=1. Net free = 0.

**Step 2 —** Passenger A walks up to the driver at 09:01:20. Driver opens their app, sees 0 seats truly free but 1 in hold. Tries manual booking without override:
```json
{ "trajetId": "trj_12", "seatsReserved": 1, "passengerName": "Ahmed", "passengerPhone": "+216..." }
```
→ Backend rejects with clear message: "Retry with overridePendingOnline=true to give priority to the walk-in passenger".

**Step 3 —** Driver taps "Yes, prioritize walk-in" in the UI. Backend receives:
```json
{ ..., "overridePendingOnline": true }
```
→ `cancelPendingForPriorityOverride(res_B)`:
- `res_B.status = CANCELLED`
- temp hold released (reservedSeats: 1 → 0, availableSeats still 1)
- Kafka `reservation.cancelled` with `priority_override: true` → Passenger B receives a push notification: "Your reservation was cancelled because a walk-in passenger took the seat at the station."
→ Create walk-in reservation `res_A`:
- status = CONFIRMED, paymentMethod = CASH
- `reserveSeatsTemporarily + confirmSeats` atomically → availableSeats: 1 → 0
→ Returns 201 to driver with the new reservation.

**Final state:** trajet full. res_A confirmed. res_B cancelled with explanation + push notification.

---

## Security

- All `/driver/*` endpoints require `X-User-Role=DRIVER` header set by the gateway.
- Driver ownership of the trajet is verified server-side on every call (can't manage another driver's seats).
- Seat count bounds are enforced in SQL — no way to get negative or over-inflated values.
- Priority override is an explicit opt-in flag — no silent override.

---

## Summary

V20 closes the last real-world gap in the seat management model: the collision between online reservations in the 15-min hold window and walk-in passengers at the station. Drivers now have three precise tools (onsite-sale, priority override, manual count correction) instead of being forced to wait for online holds to expire or call support to unstick a trajet.

The implementation is surgical: 6 files touched, all changes concentrated in trajet-service and reservation-service, zero impact on any other service. The existing test suite passes unchanged.
