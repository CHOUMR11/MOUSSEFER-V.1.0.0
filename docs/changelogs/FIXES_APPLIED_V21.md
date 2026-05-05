# MOUSSEFER V21 — Frontend-Ready Backend

**Date:** April 2026
**Base:** V20 (on-site priority) + V19 (regulated fares) + V18 (dashboards + manual booking) + V17 (role restrictions) + V16 FIXED

This version completes the backend surface the frontend needs to render every screen in the maquettes. After V21, the frontend team can start building without waiting for additional backend work.

---

## What's new

### 1. Driver KYC document system

**Problem:** Driver registration maquettes show CIN scan upload, driving license recto/verso, vehicle photo, insurance + expiry date, technical visit + expiry date, louage authorization (رخصة اللواج). The existing backend had only a generic `/documents/upload` endpoint with no type system, no verification workflow, no expiry tracking.

**Solution:**

Typed document entity with 8 document types and 4 lifecycle states:
- `CIN`, `DRIVING_LICENSE_FRONT`, `DRIVING_LICENSE_BACK`, `VEHICLE_PHOTO`, `INSURANCE`, `TECHNICAL_VISIT`, `LOUAGE_AUTHORIZATION`, `OTHER`
- Status: `PENDING_REVIEW` → `APPROVED | REJECTED | EXPIRED`
- INSURANCE and TECHNICAL_VISIT require `expiryDate` at upload
- Re-uploading a document of the same type supersedes the previous one (audit trail kept via `supersededById`)
- When all 7 required documents are APPROVED, the driver's `UserProfile.verificationStatus` flips to `VERIFIED` automatically

**Driver endpoints:**
```
POST   /api/v1/drivers/documents              # upload (multipart)
GET    /api/v1/drivers/documents/me           # list my current docs
GET    /api/v1/drivers/documents/me/kyc-status  # progress bar data
```

**Admin endpoints:**
```
GET    /api/v1/drivers/documents/internal/admin/pending
POST   /api/v1/drivers/documents/internal/admin/{id}/approve
POST   /api/v1/drivers/documents/internal/admin/{id}/reject    Body: { reason }
GET    /api/v1/drivers/documents/internal/admin/{id}/preview   # 1h presigned URL
GET    /api/v1/drivers/documents/internal/admin/user/{userId}/kyc-status
```

**Files:** 6 new in user-service.

### 2. Organizer dashboard

**Problem:** The organizer maquette (Tunisia Tours) has 8 sidebar screens — Vue d'ensemble, Réservation, Voyages organisés, Messages, Finances et factures, Codes promo, Clients, Statistiques, Paramètres. Backend had only the core voyage CRUD. No dashboard KPIs, no finances breakdown, no "Hors Moussefer" manual booking, no client analytics.

**Solution:**

New `OrganizerDashboardController` at `/api/v1/voyages/organizer` with 7 endpoints:

- `GET /overview` — top KPI cards
- `GET /finances` — Revenus total / Payés / Acomptes reçus / Non encaissé + 12-month revenue chart + 10 most recent invoices (matches maquette layout precisely)
- `GET /clients` — 8-week reservations bar chart + top-5 destinations
- `GET /statistics` — conversion rate, average revenue, source breakdown
- `GET /reservations?bookingSource=...` — paginated feed matching the "Réservation" tabs (Tous / Moussefer / Hors Moussefer / En attente / Confirmés)
- `POST /manual-booking` — **"Hors Moussefer"** flow

**"Hors Moussefer" tracking:**
- New `BookingSource` enum (PLATFORM / PHONE / AGENCY / DIRECT)
- `ReservationVoyage` entity gained 5 new fields: `bookingSource`, `manualBooking`, `manualPassengerName`, `manualPassengerPhone`, `paymentState` (UNPAID/DEPOSIT/PAID), `depositAmount`
- Organizer's "Ajouter une réservation" button calls `POST /organizer/manual-booking` with source, passenger contact, optional deposit

The finance computation correctly distinguishes platform reservations (go through Stripe) from manual bookings (organizer tracks payments directly with UNPAID/DEPOSIT/PAID states).

**Files:** 4 new + 1 entity modified in voyage-service.

### 3. Feature toggles

**Problem:** Maquette shows "Fonctionnalités" admin page. No backend support for platform-wide feature switches.

**Solution:**

- `FeatureToggle` entity with `featureKey`, `displayName`, `description`, `enabled`, `category`
- CRUD endpoints at `/api/v1/admin/features`
- SUPER_ADMIN-only for mutations via `AdminRoleGuard`
- Seed SQL with **23 default toggles** grouped by category: Réservations, Paiements, Trajets, Notifications, Communication, Fidélité, Marketing, Voyages, Maintenance

Endpoints:
```
GET    /api/v1/admin/features
GET    /api/v1/admin/features/{key}
POST   /api/v1/admin/features
PATCH  /api/v1/admin/features/{key}/toggle
DELETE /api/v1/admin/features/{id}
```

Frontend use: on app startup, call `GET /features` and cache the result. Render conditional UI based on the `enabled` flag.

**Files:** 3 new in admin-service + 1 SQL seed file.

### 4. Driver dashboard

**Problem:** Driver sidebar shows Tableau de bord, Mes trajets, Demandes, Passagers, Historiques — each with specific KPIs and list data. Backend returned raw reservations but no aggregated dashboard data.

**Solution:**

New `DriverDashboardController` at `/api/v1/reservations/driver`:

- `GET /dashboard` — KPIs (trajets this month, gross + net revenue with 90% driver share, pending demandes count)
- `GET /active-trip` — "Trajet actif · Places 3/5 confirmés" card with the list of confirmed passengers
- `GET /active-passengers` — Passagers page showing all current + upcoming passenger bookings, registered + walk-in
- `GET /history` — completed/cancelled/refused past reservations

Walk-in passengers appear in the list with `registeredUser: false`, their name/phone inline, while registered passengers show a `passengerId` the frontend can enrich via `GET /api/v1/users/driver/{id}`.

**Files:** 1 new in reservation-service.

### 5. Complete endpoint mapping document

**SCREEN_TO_ENDPOINT_MAP.md** — 900+ lines mapping every maquette screen to exact backend endpoints with request/response shapes, error envelopes, pagination, auth headers, and TypeScript interfaces the frontend can copy-paste into their Angular/React project.

This is the reference document the frontend team reads top-to-bottom to build their service layer.

---

## Files summary

### New (15 files)

**user-service — Driver KYC:**
- `entity/DocumentType.java`
- `entity/DocumentStatus.java`
- `entity/DriverDocument.java`
- `repository/DriverDocumentRepository.java`
- `service/DriverDocumentService.java`
- `controller/DriverDocumentController.java`

**voyage-service — Organizer dashboard + Hors Moussefer:**
- `entity/BookingSource.java`
- `dto/request/OrganizerManualBookingRequest.java`
- `service/OrganizerDashboardService.java`
- `controller/OrganizerDashboardController.java`

**admin-service — Feature toggles:**
- `entity/FeatureToggle.java`
- `repository/FeatureToggleRepository.java`
- `controller/FeatureToggleController.java`
- `resources/feature-toggles-seed.sql`

**reservation-service — Driver dashboard:**
- `controller/DriverDashboardController.java`

### Modified (3 files)

- `voyage-service/entity/ReservationVoyage.java` — added 5 Hors Moussefer fields
- `voyage-service/repository/ReservationVoyageRepository.java` — added 3 organizer queries
- `admin-service/security/AdminRoleGuard.java` — SUPER_ADMIN gate for feature toggles

### Root documentation

- `SCREEN_TO_ENDPOINT_MAP.md` — NEW, comprehensive integration guide
- `FIXES_APPLIED_V21.md` — this file

---

## Endpoint count

| Service | V20 | V21 | Delta |
|---|---|---|---|
| auth | 6 | 6 | — |
| user | 17 | 17 | — |
| user (driver docs) | 1 | 8 | **+7** |
| trajet | 25 | 25 | — |
| reservation | 28 | 28 | — |
| reservation (driver dashboard) | 0 | 4 | **+4** |
| payment | 17 | 17 | — |
| notification | 10 | 10 | — |
| chat | 5 | 5 | — |
| voyage | 15 | 15 | — |
| voyage (organizer) | 0 | 7 | **+7** |
| demande | 12 | 12 | — |
| avis | 7 | 7 | — |
| station | 12 | 12 | — |
| analytics | 4 | 4 | — |
| admin | 78 | 78 | — |
| admin (features) | 0 | 5 | **+5** |
| loyalty | 5 | 5 | — |
| banner | 11 | 11 | — |
| **TOTAL** | **253** | **276** | **+23** |

---

## What the frontend can now build without waiting on backend

1. **Passenger web app** — search, trajet detail, reservation, payment, reviews
2. **Driver mobile/web app** — registration with full KYC document flow, dashboard with all KPIs, reservation management, walk-in manual booking, on-site seat sales, priority override, history
3. **Organizer dashboard** — all 9 sidebar screens fully wired, finances breakdown with charts, Hors Moussefer manual booking, client analytics
4. **Super-admin dashboard** — role-specialized dashboards, user management, KYC review queue, audit logs with rich columns, regulated fares CRUD + bulk import, feature toggles, statistics

---

## What's still backend-side TODO (for V22+)

These weren't in the maquettes so they're not blocking the frontend, but worth noting for production:

- **Document expiry scheduler** — the `DriverDocumentRepository.markExpired()` query exists but needs a `@Scheduled` bean to run nightly. Adding 10 lines of Java fixes this.
- **Kafka consumer for document approval events** — when a driver uploads a document, notification-service should send a "Document reçu" push. Small addition.
- **Organizer promo code endpoints** — currently handled through admin proxy. A dedicated `/api/v1/voyages/organizer/promocodes` would be cleaner.
- **Flyway migrations** — all V16-V21 schema changes use Hibernate `ddl-auto: update`. For prod, write Flyway V1 through Vn migrations. Not urgent for PFE/soutenance but needed for production deployments.

---

## For the soutenance

**What to say when explaining the architecture:**

"Le backend Moussefer se compose de 15 microservices métier, une API Gateway Spring Cloud Gateway, un Service Registry Eureka, et une librairie partagée moussefer-common. Total : 17 composants Spring Boot déployables.

L'architecture suit un modèle piloté par événements avec Kafka (19 topics), base de données dédiée par service, Redis pour cache et rate limiting, et MinIO pour le stockage des documents.

Le backend couvre 276 endpoints répartis entre 4 frontends distincts : site public passager, dashboard chauffeur, dashboard organisateur (type agence de voyage), et dashboard super-admin. Chaque rôle admin dispose d'un tableau de bord spécialisé.

Les tarifs des louages sont contrôlés par le Ministère du Transport : notre système importe le tarif officiel et l'applique automatiquement — les chauffeurs ne peuvent pas fixer leurs prix. Nous gérons aussi les réservations hybrides : en ligne via l'app, au guichet pour les walk-ins, et pour les organisateurs les réservations téléphone/agence/direct avec suivi des paiements en dépôt."
