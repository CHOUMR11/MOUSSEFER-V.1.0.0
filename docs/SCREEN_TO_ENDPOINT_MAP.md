# MOUSSEFER — Screen → Endpoint Mapping (V21)

**Purpose:** This is the definitive frontend integration reference. Every maquette screen maps to exact backend endpoints here, with HTTP methods, request shapes, and response shapes. The frontend team can read this document top-to-bottom and implement the Angular/React services without guessing.

**Gateway base URL:** `http://localhost:8080` (dev) — all endpoints below are proxied through the API Gateway. The gateway validates JWT tokens and forwards `X-User-Id`, `X-User-Role`, and `X-Admin-Role` (when applicable) headers to downstream services.

**Auth headers on every protected call:**
```
Authorization: Bearer <JWT>
```

---

## 1. Public / Passenger Web App

### 1.1 Landing page (`Frame.png`)

| UI element | Endpoint |
|---|---|
| Top search bar (Départ / Destination / Date) | Client-side form; on submit → `/trajets` page |
| "Trajets disponibles maintenant" cards (3 cards shown) | `GET /api/v1/trajets/search?limit=3` |
| "Voyages organisés" cards (Djerba, Douz, Kairouan) | `GET /api/v1/voyages/search?size=3` |
| Review carousel "Ce que disent nos voyageurs" | `GET /api/v1/avis?featured=true&limit=3` (enhance existing) |
| Newsletter signup | `POST /api/v1/notifications/alerts/subscribe` |

### 1.2 Passenger registration (`Inscription passager.png`)

```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "passager@example.com",
  "password": "Aa1!example",
  "phoneNumber": "+21655444888",
  "role": "PASSENGER"
}
```

**Response 201:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "userId": "usr_abc",
  "role": "PASSENGER"
}
```

**Errors:**
- `400 "Self-registration is only allowed for PASSENGER and DRIVER roles"` — if role=ORGANIZER
- `400 "Email already registered"` — duplicate email

### 1.3 Login (`Inscription passager-1.png`)

```
POST /api/v1/auth/login
{
  "email": "...",
  "password": "..."
}
```

### 1.4 Search trajet page (`Recherche trajet.png`, `Desktop - 23/24/50/51.png`)

**Left sidebar filters** map to query params:

```
GET /api/v1/trajets/search
  ?departureCity=Tunis
  &arrivalCity=Sousse
  &departureDate=2026-03-24
  &departureTimeBucket=MORNING|AFTERNOON|EVENING      # Matin/Après-midi/Soir
  &airConditioned=true
  &largeLuggageAllowed=true
  &petsAllowed=true
  &directTripOnly=true
  &minSeatsAvailable=2
  &page=0&size=20
```

**Each trajet card** renders from:
```json
{
  "id": "trj_abc",
  "departureCity": "Tunis : Bab saadoun",
  "arrivalCity": "Tunis : Bab saadoun",
  "departureDate": "2026-03-24T08:00:00",
  "pricePerSeat": 12.5,
  "driver": { "id": "drv_1", "fullName": "Ahmed Ben Ali", "rating": 4.8, "reviewCount": 156 },
  "vehicle": { "brand": "Louage Peugeot 508", "airConditioned": true, "wifi": true },
  "totalSeats": 8, "reservedSeats": 3, "availableSeats": 5,
  "priorityStatus": "PRIORITAIRE|FILE_D_ATTENTE|NORMAL"
}
```

Driver card data: `GET /api/v1/drivers/{driverId}/info` (already exists).

### 1.5 Trajet detail (`Desktop - 24.png`)

**Main trajet payload:**
```
GET /api/v1/trajets/{id}
```

**Driver card details (sidebar "Votre chauffeur"):**
```
GET /api/v1/drivers/{driverId}/info
GET /api/v1/avis/driver/{driverId}?page=0&size=3   # latest 3 reviews
```

**Reservation sidebar button "Réserver maintenant":**
```
POST /api/v1/reservations
{
  "trajetId": "trj_abc",
  "seatsReserved": 2
}
```

**Regulated fare lookup (if your UI wants to show "Tarif officiel"):**
```
GET /api/v1/fares/lookup?departureCity=Tunis&arrivalCity=Sousse
```

### 1.6 Passenger reservations list

```
GET /api/v1/reservations/my?page=0&size=20&status=CONFIRMED
```

### 1.7 Empty search state (`Desktop - 23.png`)

Frontend handles empty; CTA "Proposer ce trajet" → demande collective:
```
POST /api/v1/demandes/join
{
  "departureCity": "Tunis",
  "arrivalCity": "Sousse",
  "desiredDate": "2026-03-24",
  "seatsNeeded": 2
}
```

---

## 2. Driver Dashboard

### 2.1 Driver registration (`driver UI-UX/Inscription chauffeur.png`)

Step 1 — basic account:
```
POST /api/v1/auth/register
{ "email": "...", "password": "...", "phoneNumber": "...", "role": "DRIVER" }
```

### 2.2 Driver login (`connexion chauffeur.png`)

Same endpoint as passenger: `POST /api/v1/auth/login`.

### 2.3 Driver KYC step 1 — Informations personnelles (`Inscription des information chauffeur 1.png`)

Profile update:
```
PUT /api/v1/users/me
{
  "fullName": "Mohamed Ben Ali",
  "dateOfBirth": "1985-05-10",
  "cinNumber": "12345678",
  "gouvernorat": "Tunis",
  "trajetPrincipal": "Tunis-Sousse"
}
```

CIN scan upload:
```
POST /api/v1/drivers/documents
Content-Type: multipart/form-data

type=CIN
file=<file>
```

Driving license (recto + verso separately):
```
POST /api/v1/drivers/documents   type=DRIVING_LICENSE_FRONT   file=<file>
POST /api/v1/drivers/documents   type=DRIVING_LICENSE_BACK    file=<file>
```

### 2.4 Driver KYC step 2 — Documents véhicule (`Inscription des informations chauffeur 2.png`)

Vehicle info via profile update, documents via the typed uploader:
```
POST /api/v1/drivers/documents   type=VEHICLE_PHOTO            file=<file>
POST /api/v1/drivers/documents   type=LOUAGE_AUTHORIZATION     file=<file>
POST /api/v1/drivers/documents   type=INSURANCE                file=<file>   expiryDate=2026-12-31
POST /api/v1/drivers/documents   type=TECHNICAL_VISIT          file=<file>   expiryDate=2026-05-24
```

Progress bar ("Progression finale : 85%"):
```
GET /api/v1/drivers/documents/me/kyc-status
```
**Returns:**
```json
{
  "complete": false,
  "percentage": 85,
  "approvedCount": 6,
  "totalRequired": 7,
  "missing": [],
  "pending": ["LOUAGE_AUTHORIZATION"],
  "expired": [],
  "rejected": []
}
```

"Vérification Manuelle — Vos documents seront examinés sous 24h" — this is informational; once admin approves all docs, `VerificationStatus` flips to `VERIFIED` automatically.

### 2.5 Driver dashboard — Tableau de bord

KPI cards at the top:
```
GET /api/v1/reservations/driver/dashboard
```
**Returns:**
```json
{
  "trajetsThisMonth": 24,
  "grossRevenueThisMonth": 1355.50,
  "netRevenueThisMonth": 1220.00,
  "pendingDemandes": 3,
  "ratingEndpoint": "/api/v1/avis/driver/drv_abc"
}
```

Driver rating (fetch separately):
```
GET /api/v1/avis/driver/{driverId}
```

"Trajet actif" card + "Places 3/5 confirmés":
```
GET /api/v1/reservations/driver/active-trip
```

"Demandes de réservation" list (with Accepter/Refuser buttons):
```
GET /api/v1/reservations/driver/pending
POST /api/v1/reservations/{id}/accept
POST /api/v1/reservations/{id}/refuse   Body: { "reason": "..." }
```

### 2.6 Driver — Mes trajets

```
GET /api/v1/trajets/my?status=ACTIVE&page=0&size=20     # en cours
GET /api/v1/trajets/my?status=SCHEDULED                 # programmés
GET /api/v1/trajets/my?status=COMPLETED                 # récemment terminés
```

"Publier un trajet" button:
```
POST /api/v1/trajets
{
  "departureCity": "Tunis",
  "arrivalCity":   "Sousse",
  "departureDate": "2026-03-24T08:00:00",
  "totalSeats": 8,
  "airConditioned": true,
  "largeLuggageAllowed": false,
  "petsAllowed": true,
  "directTrip": true,
  "notes": "..."
}
```
The price is **enforced server-side** from the regulated fare table. If the driver sends `pricePerSeat`, it is ignored and the official fare is used instead.

### 2.7 Driver — Passagers

```
GET /api/v1/reservations/driver/active-passengers
```
**Returns:**
```json
{
  "activeCount": 3,
  "totalThisMonth": 58,
  "passengers": [
    {
      "reservationId": "res_abc",
      "passengerId": "usr_xyz",
      "registeredUser": true,
      "seatsReserved": 1,
      "totalPrice": 12.50,
      "paymentMethod": "ONLINE",
      "status": "CONFIRMED"
    },
    {
      "reservationId": "res_walkin_1",
      "passengerName": "Ahmed Ben Salah",
      "passengerPhone": "+21698765432",
      "registeredUser": false,
      "seatsReserved": 2,
      "paymentMethod": "CASH",
      "status": "CONFIRMED"
    }
  ]
}
```

For each registered passenger, frontend can enrich with `GET /api/v1/users/driver/{passengerId}` to get full name + avatar.

### 2.8 Driver — Manual booking (walk-in at station)

The maquette shows this as a button. Behind it:
```
POST /api/v1/reservations/driver/manual-booking
{
  "trajetId": "trj_abc",
  "seatsReserved": 2,
  "passengerName": "Ahmed Ben Salah",
  "passengerPhone": "+21698765432",
  "paymentMethod": "CASH",
  "overridePendingOnline": false
}
```

If the system refuses ("Not enough seats…"), UI shows confirmation dialog → retry with `overridePendingOnline=true` to force cancel a pending online reservation.

### 2.9 Driver — On-site seat sale (single click)

When driver sells one seat directly at the station:
```
POST /api/v1/trajets/{id}/driver/onsite-sale?seats=1
```

Manual seat count correction:
```
PATCH /api/v1/trajets/{id}/driver/update-seats?availableSeats=3
```

### 2.10 Driver — Historiques

```
GET /api/v1/reservations/driver/history
```

### 2.11 Driver — Avis

```
GET /api/v1/avis/driver/{driverId}?page=0&size=20
```

### 2.12 Driver — Demandes collectives

```
GET /api/v1/demandes/search?departureCity=Tunis&arrivalCity=Sousse
POST /api/v1/demandes/{id}/convert   # convert a demande into a trajet
```

### 2.13 Driver — Messages (chat)

```
GET /api/v1/chat/{sessionId}/history
WebSocket: ws://gateway/ws/chat   (STOMP)
```

### 2.14 Driver — Profil

```
GET /api/v1/users/me
PUT /api/v1/users/me
GET /api/v1/drivers/documents/me                 # list uploaded docs
GET /api/v1/drivers/documents/me/kyc-status      # overall status
```

### 2.15 Driver — Paramètres, Déconnexion

```
POST /api/v1/auth/logout
POST /api/v1/users/me/deactivate
PUT /api/v1/users/me/fcm-token
```

---

## 3. Organizer Dashboard (Tunisia Tours style)

### 3.1 Vue d'ensemble

```
GET /api/v1/voyages/organizer/overview
```
**Returns:**
```json
{
  "totalVoyages": 12,
  "activeVoyages": 7,
  "confirmedReservationsThisMonth": 38,
  "revenueThisMonth": 6840.00,
  "seatsSoldThisMonth": 94,
  "quickActions": ["Publish new voyage", "Add manual booking (Hors Moussefer)", "View recent invoices", "Open client list"]
}
```

### 3.2 Réservation

**Tabs in the maquette:** Tous / Moussefer / Hors Moussefer / En attente / Confirmés

```
GET /api/v1/voyages/organizer/reservations                        # tous
GET /api/v1/voyages/organizer/reservations?bookingSource=PLATFORM # "Moussefer"
GET /api/v1/voyages/organizer/reservations?bookingSource=PHONE    # Hors Moussefer — Tél
GET /api/v1/voyages/organizer/reservations?bookingSource=AGENCY   # Hors Moussefer — Agence
GET /api/v1/voyages/organizer/reservations?bookingSource=DIRECT   # Hors Moussefer — Direct
```

**KPI cards** ("Total réservations 38", "Moussefer 21", "Hors Moussefer 17", "En attente 8"):
Frontend computes from `GET /statistics` + filtered counts.

"Ajouter une réservation" button (modal):
```
POST /api/v1/voyages/organizer/manual-booking
{
  "voyageId": "voy_abc",
  "seatsReserved": 2,
  "passengerName": "Mohamed Ali",
  "passengerPhone": "+21612345678",
  "bookingSource": "PHONE",
  "depositAmount": 200.00
}
```

### 3.3 Voyages organisés

```
GET /api/v1/voyages/my                  # organizer's own voyages
POST /api/v1/voyages                    # create new voyage
PUT /api/v1/voyages/{id}
DELETE /api/v1/voyages/{id}
GET /api/v1/voyages/organizer/reservations/{voyageId}   # reservations on one voyage
```

### 3.4 Messages

```
GET /api/v1/chat/{sessionId}/history
```

### 3.5 Finances et factures

```
GET /api/v1/voyages/organizer/finances
```
**Returns:**
```json
{
  "revenueTotal": 6840.00,
  "paid":         4360.00,
  "depositsReceived": 1440.00,
  "notCollected":  1800.00,
  "paidPercentage": 63.74,
  "depositsPercentage": 21.05,
  "notCollectedPercentage": 26.31,
  "monthlyRevenue": [
    { "month": "mai.",  "year": 2025, "revenue": 420.00 },
    { "month": "juin.", "year": 2025, "revenue": 1250.00 },
    ... 12 entries total
  ],
  "recentInvoices": [
    {
      "reservationId": "res_1",
      "voyageId":      "voy_djerba",
      "passengerName": "Sana Ben Ramdhane",
      "amount":        290.00,
      "date":          "2026-03-20T14:32",
      "status":        "PAID",
      "invoiceUrl":    "https://minio.../invoice.pdf",
      "bookingSource": "PLATFORM"
    }
  ]
}
```

The `monthlyRevenue` array feeds the bar chart in the maquette. `recentInvoices` feeds the right-side list.

### 3.6 Codes promo

```
GET  /api/v1/admin/promocodes                    # (if organizer allowed)
POST /api/v1/admin/promocodes
PATCH /api/v1/admin/promocodes/{id}
```
Or for organizers their own: a future `/organizer/promocodes` would be ideal — currently handled through admin proxy.

### 3.7 Clients

```
GET /api/v1/voyages/organizer/clients
```
**Returns:**
```json
{
  "uniqueClientsLast8Weeks": 42,
  "totalReservationsLast8Weeks": 58,
  "weeklyReservations": [
    { "weekLabel": "S-7", "reservations": 4 },
    { "weekLabel": "S-6", "reservations": 7 },
    ... 8 weeks
  ],
  "topDestinations": [
    { "destination": "Djerba",   "seatsSold": 34 },
    { "destination": "Carthage", "seatsSold": 18 }
  ]
}
```

### 3.8 Statistiques

```
GET /api/v1/voyages/organizer/statistics
```
**Returns:**
```json
{
  "totalReservationsThisYear": 245,
  "confirmed": 198,
  "cancelled": 35,
  "refused":   12,
  "conversionRate": 80.81,
  "averageRevenue": 215.80,
  "bookingsBySource": {
    "PLATFORM": 145,
    "PHONE":    70,
    "AGENCY":   25,
    "DIRECT":   5
  }
}
```

### 3.9 Paramètres, Déconnexion

Same as driver: `GET /api/v1/users/me`, `PUT /api/v1/users/me`, `POST /api/v1/auth/logout`.

---

## 4. Super-Admin Dashboard

### 4.1 Dashboard selection

When admin logs in, front-end reads `adminRole` from JWT and calls:
```
GET /api/v1/admin/dashboard/me
```
The backend auto-routes to the correct dashboard (super-admin / operational / financial / moderator / reporter / auditor).

Alternative — explicit dashboards:
```
GET /api/v1/admin/dashboard/super-admin
GET /api/v1/admin/dashboard/operational
GET /api/v1/admin/dashboard/financial
GET /api/v1/admin/dashboard/moderator
GET /api/v1/admin/dashboard/reporter
GET /api/v1/admin/dashboard/auditor
```

### 4.2 Utilisateurs / Chauffeurs

```
GET    /api/v1/admin/users?role=DRIVER&status=ACTIVE&page=0&size=20
GET    /api/v1/admin/users/{userId}
POST   /api/v1/admin/users                      # create ORGANIZER (SUPER_ADMIN only)
POST   /api/v1/admin/users/{userId}/suspend
POST   /api/v1/admin/users/{userId}/lift-suspension
POST   /api/v1/admin/users/{userId}/deactivate
POST   /api/v1/admin/users/{userId}/reactivate
POST   /api/v1/admin/users/{userId}/verify
```

### 4.3 Driver KYC review (pending documents queue)

```
GET  /api/v1/drivers/documents/internal/admin/pending?page=0&size=20
GET  /api/v1/drivers/documents/internal/admin/{id}/preview   # 1h presigned URL
POST /api/v1/drivers/documents/internal/admin/{id}/approve
POST /api/v1/drivers/documents/internal/admin/{id}/reject    Body: { "reason": "..." }
GET  /api/v1/drivers/documents/internal/admin/user/{userId}/kyc-status
```

### 4.4 Notifications & avis

```
POST /api/v1/admin/notifications/send        Body: { userId, title, body }
POST /api/v1/admin/notifications/broadcast   Body: { segment: ALL|DRIVERS|PASSENGERS, title, body }
GET  /api/v1/admin/notifications/templates
DELETE /api/v1/avis/internal/admin/{avisId}  # moderation: delete a review
```

### 4.5 Journal d'activité (audit logs)

The maquette shows columns: **Type / Acteur / Action / Cible / Date / Détails**

```
GET /api/v1/admin/audit-logs?page=0&size=20
GET /api/v1/admin/audit-logs/admin/{adminId}
GET /api/v1/admin/audit-logs/target/{type}/{id}
```

Each row returns:
```json
{
  "id": "al_abc",
  "adminId": "adm_123",           # Acteur
  "action": "USER_SUSPENDED",      # Action
  "targetType": "USER",            # Type
  "targetId": "usr_xyz",           # Cible
  "details": "Suspended due to policy violation",  # Détails
  "ipAddress": "...",
  "createdAt": "2026-04-23T14:30"  # Date
}
```

### 4.6 Trajets

```
GET    /api/v1/admin/trajets/internal/admin/all?page=0&size=20
PUT    /api/v1/admin/trajets/{id}/status         Body: { "status": "ACTIVE|LOCKED|CANCELLED" }
DELETE /api/v1/admin/trajets/{id}
PATCH  /api/v1/trajets/internal/admin/{id}/assign-driver?newDriverId=...
```

### 4.7 Réservations

```
GET  /api/v1/admin/reservations/stats
GET  /api/v1/admin/reservations/{id}
PUT  /api/v1/admin/reservations/{id}/status
POST /api/v1/admin/reservations/{id}/refund
POST /api/v1/reservations/internal/admin/{id}/force-cancel
POST /api/v1/reservations/internal/admin/{id}/force-confirm
POST /api/v1/reservations/internal/admin/{id}/force-escalate
POST /api/v1/reservations/internal/admin/{id}/refund-and-cancel
```

Disputes:
```
GET  /api/v1/reservations/internal/admin/disputes/all
POST /api/v1/reservations/internal/admin/disputes/{id}/assign
POST /api/v1/reservations/internal/admin/disputes/{id}/resolve
GET  /api/v1/reservations/internal/admin/disputes/stats
```

### 4.8 Paiements

```
GET  /api/v1/admin/payments/stats
GET  /api/v1/admin/payments/{paymentId}
GET  /api/v1/admin/payments/commissions
GET  /api/v1/admin/payments/driver-payouts
GET  /api/v1/admin/payments/export
POST /api/v1/admin/payments/{paymentId}/refund
PUT  /api/v1/admin/payments/{paymentId}/status
```

### 4.9 Tarifs régulés (Ministère du Transport)

**SUPER_ADMIN + FINANCIAL_ADMIN only** for mutations:
```
GET    /api/v1/admin/fares?city=Tunis&active=true
POST   /api/v1/admin/fares                          # create or update single
PATCH  /api/v1/admin/fares/{id}/active?active=false
DELETE /api/v1/admin/fares/{id}
POST   /api/v1/admin/fares/import                   # multipart file upload
POST   /api/v1/admin/fares/import-json              # inline JSON
```

### 4.10 Bannières publicitaires

```
GET    /api/v1/admin/banners/performance
GET    /api/v1/admin/banners/{id}
GET    /api/v1/admin/banners/{id}/stats
PUT    /api/v1/admin/banners/{id}
DELETE /api/v1/admin/banners/{id}
```

### 4.11 Codes promotionnels

```
GET    /api/v1/admin/promocodes
GET    /api/v1/admin/promocodes/stats
GET    /api/v1/admin/promocodes/{id}
PUT    /api/v1/admin/promocodes/{id}
POST   /api/v1/admin/promocodes/{id}/activate
POST   /api/v1/admin/promocodes/{id}/deactivate
DELETE /api/v1/admin/promocodes/{id}
```

### 4.12 Fonctionnalités (feature toggles) — NEW V21

```
GET    /api/v1/admin/features                     # list grouped by category
GET    /api/v1/admin/features/{key}
POST   /api/v1/admin/features                     # create or update
PATCH  /api/v1/admin/features/{key}/toggle        # flip on/off
DELETE /api/v1/admin/features/{id}
```

**SUPER_ADMIN only for mutations.** 23 default toggles seeded (reservations, payments, trajets, notifications, chat, loyalty, maintenance).

### 4.13 Paramètres admin

```
GET  /api/v1/admin/roles
GET  /api/v1/admin/users/{userId}/admin-role
POST /api/v1/admin/users/{userId}/admin-role  # assign SUPER_ADMIN/OPERATIONAL_ADMIN/etc
```

### 4.14 Simulation de rôle (SUPER_ADMIN only)

```
GET /api/v1/admin/simulate-role/{targetRole}
```
Returns a JWT scoped to the target role — lets the super-admin preview the UI as that role.

### 4.15 Statistiques globales

```
GET /api/v1/admin/statistics
GET /api/v1/admin/statistics/charts
GET /api/v1/admin/statistics/recent-activity
GET /api/v1/analytics/internal/admin/dashboard
GET /api/v1/analytics/internal/admin/top-routes?limit=10
GET /api/v1/analytics/internal/admin/alerts
GET /api/v1/analytics/internal/admin/export?format=csv&from=2026-01-01&to=2026-12-31
```

---

## 5. Shared: Notifications, Loyalty, Chat

### Notifications

```
GET    /api/v1/notifications/my                         # bell icon list
POST   /api/v1/notifications/read-all
POST   /api/v1/notifications/{id}/read
DELETE /api/v1/notifications/{id}
DELETE /api/v1/notifications/all
POST   /api/v1/notifications/alerts/subscribe           # route alerts
GET    /api/v1/notifications/alerts/my
DELETE /api/v1/notifications/alerts/{alertId}
```

### Loyalty

```
GET  /api/v1/loyalty/me
POST /api/v1/loyalty/redeem           Body: { points, usage }
GET  /api/v1/loyalty/history
```

### Chat (post-payment)

```
GET  /api/v1/chat/{sessionId}/history
WS   /ws/chat                         # STOMP subscribe to /topic/chat.{sessionId}
```

### Avis (reviews)

```
POST /api/v1/avis                     Body: { reservationId, rating, comment }
GET  /api/v1/avis/{avisId}
PUT  /api/v1/avis/{avisId}
GET  /api/v1/avis/driver/{driverId}
GET  /api/v1/avis/me
GET  /api/v1/avis/reservation/{reservationId}
```

### Payment flow

```
POST /api/v1/payments/initiate        Body: { reservationId, promoCode }  → Stripe clientSecret
GET  /api/v1/payments/validate-promo?code=WELCOME10&amount=45.00
GET  /api/v1/payments/reservation/{id}
GET  /api/v1/payments/invoice/{reservationId}   # signed URL to PDF
GET  /api/v1/payments/my
```

### Stations

```
GET /api/v1/stations/{id}
GET /api/v1/stations/city/{city}
GET /api/v1/stations/{stationId}/secondary-points
```

---

## 6. Auth headers summary (what the gateway adds)

After JWT validation, the gateway forwards:
- `X-User-Id` — all authenticated calls
- `X-User-Role` — PASSENGER | DRIVER | ORGANIZER | ADMIN
- `X-Admin-Role` — when role=ADMIN: SUPER_ADMIN | OPERATIONAL_ADMIN | FINANCIAL_ADMIN | MODERATOR | REPORTER | AUDITEUR
- `X-Admin-Id` — same as `X-User-Id` but explicit for admin-service
- `X-Internal-Secret` — for service-to-service calls (not present on public traffic)

---

## 7. Error envelope

All errors return this shape (except 404 HTML):

```json
{
  "error":   "BUSINESS_EXCEPTION|FORBIDDEN|VALIDATION_ERROR|...",
  "message": "Human-readable error description",
  "path":    "/api/v1/trajets",
  "timestamp": "2026-04-24T14:32:00"
}
```

HTTP codes used consistently:
- `200` OK
- `201` Created — for POST that creates a resource
- `204` No Content — for successful PATCH/DELETE without body
- `400` Bad Request — validation / business rule violation
- `401` Unauthorized — missing or invalid JWT
- `403` Forbidden — authenticated but role insufficient
- `404` Not Found
- `409` Conflict — e.g. race condition on seat booking
- `500` Internal Server Error — bubbled up from downstream

---

## 8. Pagination — standard Spring Page response

All list endpoints return the Spring Page shape:
```json
{
  "content": [ ... ],
  "pageable": { "pageNumber": 0, "pageSize": 20, ... },
  "totalElements": 245,
  "totalPages": 13,
  "last": false,
  "first": true,
  "numberOfElements": 20,
  "empty": false
}
```

Query params: `?page=0&size=20&sort=createdAt,desc`.

---

## 9. File upload convention

All document uploads use `multipart/form-data` with the file field name `file`. Drivers upload KYC documents via the typed endpoint:

```
POST /api/v1/drivers/documents
Content-Type: multipart/form-data

type         DocumentType (required — one of: CIN, DRIVING_LICENSE_FRONT, DRIVING_LICENSE_BACK, VEHICLE_PHOTO, INSURANCE, TECHNICAL_VISIT, LOUAGE_AUTHORIZATION, OTHER)
expiryDate   ISO date (required for INSURANCE and TECHNICAL_VISIT)
file         the binary
```

Accepted formats: JPEG, PNG, WebP, PDF. Max size: 10MB per file.

---

## 10. Real-time streams

### WebSocket chat (post-payment)
Connect: `ws://gateway/ws/chat?access_token=<JWT>`
Subscribe: `/topic/chat.{sessionId}`
Send: `/app/chat.{sessionId}.send`

### Firebase Cloud Messaging
Register device:
```
PUT /api/v1/users/me/fcm-token   Body: { "fcmToken": "..." }
```

Server pushes:
- `reservation.pending_driver` → driver sees new request
- `reservation.accepted` → passenger proceeds to payment
- `reservation.refused` → passenger sees refusal
- `reservation.cancelled` with `priority_override: true` → passenger lost seat at station
- `reservation.confirmed` → booking locked in
- `payment.refunded` → refund processed
- `demande.threshold_met` → drivers near route notified

---

## 11. Frontend TypeScript interfaces (generated from endpoints)

A starter set the frontend can use directly (Angular `.service.ts` or React `api.ts`):

```typescript
// ── Auth ──
export interface RegisterRequest {
  email: string;
  password: string;
  phoneNumber: string;
  role: 'PASSENGER' | 'DRIVER';
}
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  role: string;
}

// ── Trajet ──
export interface Trajet {
  id: string;
  driverId: string;
  departureCity: string;
  arrivalCity: string;
  departureDate: string;
  totalSeats: number;
  availableSeats: number;
  reservedSeats: number;
  pricePerSeat: number;
  status: 'ACTIVE'|'LOCKED'|'DEPARTED'|'COMPLETED'|'CANCELLED';
  priorityOrder: number;
  airConditioned?: boolean;
  largeLuggageAllowed?: boolean;
  petsAllowed?: boolean;
  directTrip?: boolean;
}

// ── Reservation ──
export interface Reservation {
  id: string;
  trajetId: string;
  passengerId: string;
  driverId: string;
  seatsReserved: number;
  totalPrice: number;
  status: 'PENDING_DRIVER'|'ACCEPTED'|'REFUSED'|'PAYMENT_PENDING'|'CONFIRMED'|'CANCELLED'|'ESCALATED';
  manualBooking?: boolean;
  manualPassengerName?: string;
  manualPassengerPhone?: string;
  paymentMethod?: 'ONLINE'|'CASH';
  createdAt: string;
}

// ── Driver KYC ──
export type DocumentType = 'CIN'|'DRIVING_LICENSE_FRONT'|'DRIVING_LICENSE_BACK'|'VEHICLE_PHOTO'|'INSURANCE'|'TECHNICAL_VISIT'|'LOUAGE_AUTHORIZATION'|'OTHER';
export interface DriverDocument {
  id: string;
  userId: string;
  documentType: DocumentType;
  fileUrl: string;
  status: 'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'EXPIRED';
  expiryDate?: string;
  rejectionReason?: string;
  uploadedAt: string;
}
export interface KycStatus {
  complete: boolean;
  percentage: number;
  approvedCount: number;
  totalRequired: number;
  missing: DocumentType[];
  pending: DocumentType[];
  expired: DocumentType[];
  rejected: DocumentType[];
}

// ── Organizer dashboard ──
export type BookingSource = 'PLATFORM'|'PHONE'|'AGENCY'|'DIRECT';
export interface OrganizerFinances {
  revenueTotal: number;
  paid: number;
  depositsReceived: number;
  notCollected: number;
  paidPercentage: number;
  monthlyRevenue: Array<{ month: string; year: number; revenue: number }>;
  recentInvoices: Array<{
    reservationId: string;
    voyageId: string;
    passengerName: string;
    amount: number;
    date: string;
    status: string;
    invoiceUrl: string;
    bookingSource: BookingSource;
  }>;
}

// ── Feature toggles ──
export interface FeatureToggle {
  id: string;
  featureKey: string;
  displayName: string;
  description: string;
  enabled: boolean;
  category: string;
  updatedAt: string;
  updatedBy: string;
}
```

---

## End of mapping

Every screen in the maquettes is covered. If the frontend needs a payload shape not explicitly listed above, the pattern is:
1. Look at the section for that screen
2. Check the service's Swagger UI at `http://localhost:8080/api/v1/{service}/swagger-ui.html`
3. Fall back to the JavaDoc on the controller method

This document is versioned with the backend — when V22 adds new endpoints, they appear here too.
