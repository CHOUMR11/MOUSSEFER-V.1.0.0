# Moussefer — Changelog

All notable changes to the Moussefer backend, organized by version.
For granular details on each version, see the individual `FIXES_APPLIED_V*.md` files.

---

## V25 — Suppression du guichet, dashboard 2-sources clean (Apr 2026)

**Production code changes:**
- ❌ Suppression complète de la logique guichet (vente onsite + manual booking + priorité override)
- ❌ Endpoints retirés : `POST /trajets/{id}/driver/onsite-sale`, `PATCH /trajets/internal/{id}/onsite-sale`, `POST /reservations/driver/manual-booking`
- ❌ DTO supprimé : `DriverManualBookingRequest`
- ❌ Fields entité retirés : `Reservation.manualBooking`, `manualPassengerName`, `manualPassengerPhone`
- ❌ Code mort éliminé : `ApplyPromoCodeRequest`, `DateUtils`, `PagedResponse`, `ApiResponse`, et l'entièreté du module `moussefer-common` (vide après cleanup)
- ✅ `PATCH /trajets/{id}/driver/update-seats?availableSeats=N` conservé et amélioré (seul endpoint pour gérer le compteur de places)
- ✅ Ajout de `reservedSeats` dans `TrajetResponse` (permet au frontend d'afficher la décomposition 2-sources)
- ✅ Logique du consumer Kafka simplifiée : suppression du flag `priority_override`, refund uniforme sur cancellation

**Architecture rationale:**
- Moussefer ne gère pas la vente au guichet (système séparé avec ses propres agents)
- Le chauffeur peut corriger le compteur pour refléter les ventes hors-plateforme
- Anti-double-booking garanti par le SQL atomique : `UPDATE WHERE :newAvailable BETWEEN 0 AND (totalSeats - reservedSeats)`

**Cleanup metrics:**
- Module supprimé : `moussefer-common` (était vide, jamais référencé)
- Java files: 370 → 365 (−5)
- Maven modules: 18 → 17 (−1)
- Endpoints REST: 291 → 287 (−4 endpoints guichet)
- Tests: 88 → 87 (−1 test priority_override)

---

## V24 — Suite de tests + correction louage 8 places + roadmap multi-modal (Apr 2026)

**Production code changes:**
- Capacity louage fixed at 8 seats (constant `TrajetService.LOUAGE_SEATS`, removed from request DTO)
- `transportMode` field on `Trajet` entity with default `LOUAGE` (preparation for V25+ multi-modal)
- New `TransportMode` enum

**Test additions (88 total `@Test` methods, +55 vs V23):**
- `PasswordResetServiceTest` — 14 tests covering 5 V22 security guarantees
- `PaymentServiceTest` — 6 tests on refund business rules
- `ReservationEventConsumerTest` — 11 tests on V22 auto-refund Kafka consumer
- `VoyageServiceTest` — 8 tests on voyage organisé lifecycle
- `DemandeServiceTest` — 6 tests on collective demand rules
- `TrajetConcurrencyTest` — 3 critical concurrency tests (100 threads / 8 seats)

**Documentation:**
- `ROADMAP.md` — multi-modal evolution plan (taxi, bus, métro)

---

## V23 — Frontend compatibility fixes (Apr 2026)

- `AdminStationsProxyController` created (10 admin endpoints for stations)
- `POST /api/v1/users/me/profile-picture` endpoint with MinIO storage
- `POST /api/v1/voyages/{id}/image` endpoint with dedicated MinIO bucket
- API gateway: `/internal/admin/**` accessible via admin JWT (gateway injects secret)
- `/auth/logout` made robust against expired tokens (gateway public route + body fallback)

---

## V22 — Conformity fixes (Apr 2026)

- Reservation uniqueness check (passenger cannot re-reserve same trajet)
- Auto-refund Kafka consumer (escalated/refused/cancelled triggers Stripe refund)
- Password reset by email flow (forgot-password + reset-password endpoints, 256-bit tokens, anti-enumeration, single-use)

---

## V21 — Frontend-ready backend

- Admin proxy controllers for cross-service operations
- Driver typed KYC documents (CIN, license front/back, vehicle photo, insurance, technical visit, louage authorization)
- Organizer dashboard with Hors Moussefer bookings (PHONE/AGENCY/DIRECT) + deposit tracking
- 23 feature toggles seeded with admin UI

---

## V20 — On-site priority (walk-in passengers)

- Driver onsite seat sale endpoint (atomic SQL decrement)
- Manual booking with `overridePendingOnline` flag (priority over waiting online reservations)
- Auto-refund triggered when online reservation is overridden

---

## V19 — Government-regulated fares

- `RegulatedFare` entity for Ministry of Transport tariffs
- Import endpoints (JSON/CSV) with line-by-line error reporting
- 30 Tunisia routes pre-seeded
- Enforcement at trajet publication (driver price overridden by official tariff)

---

## V18 — Driver manual booking + specialized admin dashboards

- 6 admin role dashboards (Super-Admin, Operational, Financial, Moderator, Reporter, Auditor)
- Driver-side manual booking with cash payment

---

## V17 — Self-registration restrictions

- Role `ORGANIZER` can no longer self-register (admin-only creation)
- Account creation security tightened

---

## V16 — Initial bug fixes (16 bugs)

Base stabilization release with 51 passing tests on critical services.

---

## Pre-V16 (V15) — Initial release

127 user stories, 12 microservices, baseline architecture.
