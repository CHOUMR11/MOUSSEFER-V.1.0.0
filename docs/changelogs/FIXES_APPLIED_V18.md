# MOUSSEFER V18 — Driver Manual Booking + Specialized Admin Dashboards

**Date:** April 2026
**Base:** V17 (role restrictions) + V16 FIXED (16 bug fixes, 51 tests passed)

---

## Components count — clarification

Moussefer is built around **17 Spring Boot deployable components** plus 1 shared library:

- **15 business microservices**: auth, user, trajet, reservation, payment, notification, chat, voyage, demande, avis, station, analytics, admin, loyalty, banner
- **2 infrastructure components**: api-gateway (Spring Cloud Gateway), service-registry (Netflix Eureka)
- **1 shared library**: moussefer-common (not deployed — imported as a Maven dependency)

So when presenting, say: **"15 microservices métier + API Gateway + Service Registry Eureka = 17 services Spring Boot, plus 1 librairie partagée moussefer-common"**.

---

## Feature 1 — Driver manual booking (on-site at louage station)

### The use case

A passenger shows up at the louage station without having reserved online. The driver wants to sit them in the louage and record the booking in the system so that:
- The seat is no longer listed as available on the app
- The booking appears in the driver's history
- Analytics capture the revenue (even if paid in cash)
- Loyalty points and receipts can be generated later if needed

### What was added

**`reservation-service/entity/PaymentMethod.java`** (new)
- `CASH` (default for manual bookings) — driver collects on-site
- `ONLINE` — standard Stripe flow

**`reservation-service/entity/Reservation.java`** (modified)
Added fields for walk-in bookings:
- `manualBooking` (boolean) — flags the reservation as driver-created
- `manualPassengerName` (String) — required for walk-in bookings
- `manualPassengerPhone` (String) — required so the driver can contact the passenger
- `paymentMethod` (enum) — CASH or ONLINE

**`reservation-service/dto/request/DriverManualBookingRequest.java`** (new)
Validated request body: trajetId, seatsReserved (1-9), passengerName, passengerPhone, paymentMethod (optional, defaults to CASH).

**`reservation-service/service/ReservationService.driverManualBooking()`** (new method)

Flow:
1. Fetch trajet via WebClient and verify the driver owns it.
2. Check trajet is ACTIVE.
3. Check enough seats available.
4. Compute total price server-side from the trajet's actual price (no client-side price manipulation).
5. Create a reservation with a synthetic `passengerId` (`walkin_<uuid>`) since walk-in passengers may not have a platform account. The `manual_passenger_name` and `manual_passenger_phone` columns store the real contact info.
6. Skip the usual PENDING_DRIVER → ACCEPTED → PAYMENT_PENDING dance. Go straight to CONFIRMED.
7. Reserve seats + confirm seats atomically (one transaction).
8. Emit `reservation.confirmed` Kafka event so downstream (analytics, notification, loyalty) react identically to online bookings.

**`reservation-service/controller/ReservationController.driverManualBooking()`** (new endpoint)

`POST /api/v1/reservations/driver/manual-booking` — requires `X-User-Role=DRIVER`, returns 201 CREATED with the reservation details.

### Example usage

```bash
curl -X POST http://gateway:8080/api/v1/reservations/driver/manual-booking \
  -H "Authorization: Bearer $DRIVER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "trajetId": "trj_abc123",
    "seatsReserved": 2,
    "passengerName": "Ahmed Ben Salah",
    "passengerPhone": "+21698765432",
    "paymentMethod": "CASH"
  }'
```

Response `201`:
```json
{
  "id": "res_xyz789",
  "trajetId": "trj_abc123",
  "passengerId": "walkin_abc12345",
  "driverId": "drv_theDriverId",
  "seatsReserved": 2,
  "totalPrice": 30.00,
  "status": "CONFIRMED",
  "manualBooking": true,
  "paymentMethod": "CASH",
  "confirmedAt": "2026-04-23T14:32:00"
}
```

### Security guarantees

- Driver ownership of the trajet is verified server-side (cannot book seats on another driver's trajet).
- The total price is computed from the trajet's real price (client cannot undercharge).
- Seats availability is checked atomically — no over-booking even with concurrent manual + online bookings.

---

## Feature 2 — Specialized admin dashboards

### Motivation

All 6 admin roles currently hit the same `/api/v1/admin/statistics` endpoint that returns a flat aggregate. A financial admin doesn't care about pending KYC verifications, and a moderator doesn't need driver payout data. The frontend needs role-specialized payloads so each admin gets a focused UI without noise.

### What was added

**`admin-service/controller/AdminDashboardController.java`** (new)

7 new endpoints under `/api/v1/admin/dashboard/*`:

| Endpoint | For role | Returns |
|---|---|---|
| `/super-admin` | SUPER_ADMIN | Everything — users, reservations, payments, analytics, quick actions to manage roles / create organizers / review audit |
| `/operational` | OPERATIONAL_ADMIN + SUPER_ADMIN | Reservation stats + pending list + escalated list + disputes + trajets. Quick actions: force-cancel, assign driver, resolve dispute |
| `/financial` | FINANCIAL_ADMIN + SUPER_ADMIN | Payment stats + commissions + driver payouts + promo codes. Quick actions: refund, export CSV, manage promos |
| `/moderator` | MODERATOR + SUPER_ADMIN | User stats + pending verifications + suspended users. Quick actions: review KYC, delete reviews, moderate chats |
| `/reporter` | REPORTER + SUPER_ADMIN | Analytics + top routes + alerts. Quick actions: view reports, export CSV (read-only) |
| `/auditor` | AUDITEUR + SUPER_ADMIN | Audit-focused overview, pointers to audit-logs endpoint |
| `/me` | Any admin | Returns the dashboard matching the caller's X-Admin-Role automatically |

Each endpoint includes:
- A `scope` string describing what this dashboard covers
- Domain KPIs fetched in parallel via WebClient (fail-safe: if one subservice is down, its slot shows `{"error": "service unavailable"}` but the rest of the dashboard still renders)
- A `quickActions` array — strings the frontend can render as action buttons

### Frontend integration (Angular/React)

The simplest pattern: on admin login, read the `adminRole` claim from the JWT and call `/api/v1/admin/dashboard/me`. The backend returns the right dashboard, the frontend renders it as a series of cards + chart widgets.

Or, for a more granular UX where the same admin can switch "views" (e.g. a SUPER_ADMIN who wants to drill into the financial view), call the role-specific endpoint directly.

### Security

- Every endpoint except `/me` validates the caller's `X-Admin-Role` matches the dashboard's scope.
- `SUPER_ADMIN` can access any dashboard (they already see everything).
- AdminRoleGuard filter still applies globally — it rejects requests without a valid admin role at the filter layer.

---

## Summary of V18 changes

**New files (3):**
```
reservation-service/entity/PaymentMethod.java
reservation-service/dto/request/DriverManualBookingRequest.java
admin-service/controller/AdminDashboardController.java
```

**Modified files (3):**
```
reservation-service/entity/Reservation.java               (+4 fields)
reservation-service/service/ReservationService.java       (+driverManualBooking method, ~90 lines)
reservation-service/controller/ReservationController.java (+1 endpoint)
```

**Inherited from V17** (role restrictions):
- Self-registration blocks ORGANIZER/ADMIN
- Admin-only `POST /api/v1/admin/users` for ORGANIZER creation
- `InternalAuthFilter` in auth-service
- AdminRoleGuard enforces SUPER_ADMIN for user creation

**Inherited from V16** (bug fixes):
- All 16 production bugs fixed
- 51 tests passing

---

## Endpoint map — full admin surface

After V18, the admin-service exposes the following frontend-facing endpoints:

```
# V12 base
GET  /api/v1/admin/users                        (list)
GET  /api/v1/admin/users/{id}
POST /api/v1/admin/users/{id}/deactivate
POST /api/v1/admin/users/{id}/reactivate
POST /api/v1/admin/users/{id}/suspend
POST /api/v1/admin/users/{id}/lift-suspension
POST /api/v1/admin/users/{id}/verify
POST /api/v1/admin/users/{id}/admin-role
GET  /api/v1/admin/users/{id}/admin-role

# V12 proxies
GET/POST/PATCH/DELETE /api/v1/admin/banners/**
GET/POST/PATCH/DELETE /api/v1/admin/promocodes/**
GET  /api/v1/admin/statistics
GET  /api/v1/admin/statistics/charts
GET  /api/v1/admin/statistics/recent-activity
GET/POST/PUT /api/v1/admin/reservations/**
GET/POST/PUT /api/v1/admin/payments/**
GET  /api/v1/admin/roles
GET  /api/v1/admin/roles/{code}
GET/PUT/DELETE /api/v1/admin/trajets/**
GET  /api/v1/admin/activity-logs
GET  /api/v1/admin/audit-logs

# V14 notifications
POST /api/v1/admin/notifications/send
POST /api/v1/admin/notifications/broadcast
GET  /api/v1/admin/notifications/templates

# V17 user creation
POST /api/v1/admin/users                         (SUPER_ADMIN only — creates ORGANIZER/ADMIN)

# V18 specialized dashboards
GET  /api/v1/admin/dashboard/super-admin
GET  /api/v1/admin/dashboard/operational
GET  /api/v1/admin/dashboard/financial
GET  /api/v1/admin/dashboard/moderator
GET  /api/v1/admin/dashboard/reporter
GET  /api/v1/admin/dashboard/auditor
GET  /api/v1/admin/dashboard/me
```

---

## Presenting the architecture in soutenance

**Architecture stack:**
- 15 Spring Boot microservices (domain-driven design)
- API Gateway (Spring Cloud Gateway) — JWT validation, rate limiting, circuit breakers, CORS
- Service Registry (Netflix Eureka) — dynamic service discovery
- Apache Kafka — event-driven async communication (19 topics)
- MySQL per service + Redis (cache + rate limiter) + MinIO (PDF invoices)
- Stripe for payments
- WebSocket STOMP for chat

**Role separation:**
- PASSENGER & DRIVER: self-registration
- ORGANIZER: created by SUPER_ADMIN (premium vetting required)
- ADMIN: 6 granular roles with path-based authorization

**6 admin dashboards:** each role has a tailored KPI view — the platform isn't a "one dashboard fits all".

**Driver flexibility:** manual booking for walk-in passengers at louage stations, because Moussefer must work in the physical world too, not just online.
