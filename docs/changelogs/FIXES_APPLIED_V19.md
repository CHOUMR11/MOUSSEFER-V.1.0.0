# MOUSSEFER V19 — Government-Regulated Louage Fares

**Date:** April 2026
**Base:** V18 (driver manual booking + specialized dashboards) + V17 (role restrictions) + V16 FIXED (16 bugs)

---

## What's new

In Tunisia, inter-city shared taxi (louage) fares are set by the **Ministry of Transport** and the **Chambre Syndicale des Louages** — not by individual drivers. V19 enforces this: drivers can no longer choose their own prices. Instead, a SUPER_ADMIN (or FINANCIAL_ADMIN) imports the official fare schedule, and the system enforces those prices at trajet publish time.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Ministry of Transport / Chambre Syndicale           │
│  publishes fare schedule (JSON/CSV)                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  SUPER_ADMIN / FINANCIAL_ADMIN                       │
│  POST /api/v1/admin/fares/import  (uploads file)    │
└──────────────────────┬──────────────────────────────┘
                       │ proxies via admin-service
                       ▼
┌─────────────────────────────────────────────────────┐
│  trajet-service                                      │
│   ├── RegulatedFare entity (regulated_fares table)  │
│   ├── RegulatedFareService (bulk import + CRUD)     │
│   └── RegulatedFareController                        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│  DRIVER publishes trajet                             │
│   → TrajetService.publishTrajet() looks up the      │
│     regulated fare for the route                    │
│   → overrides driver-supplied price with official   │
│   → rejects publish if no fare exists for route     │
└─────────────────────────────────────────────────────┘
```

---

## Files added (7)

```
trajet-service/
  ├── entity/RegulatedFare.java
  ├── repository/RegulatedFareRepository.java
  ├── service/RegulatedFareService.java
  ├── controller/RegulatedFareController.java
  ├── dto/request/RegulatedFareDto.java
  ├── dto/response/FareImportReport.java
  └── resources/sample-tunisia-fares.json   (30 real Tunisian routes)

admin-service/
  └── controller/AdminFaresProxyController.java
```

## Files modified (3)

```
trajet-service/service/TrajetService.java         (+ enforcement in publishTrajet & updateTrajet)
admin-service/security/AdminRoleGuard.java        (+ SUPER_ADMIN/FINANCIAL_ADMIN gate on fares)
admin-service/config/WebClientConfig.java         (trajetServiceWebClient already exists)
```

---

## How enforcement works

### At publish time (`POST /api/v1/trajets`)

```java
// trajet-service/TrajetService.publishTrajet(...)
var fare = regulatedFareRepository.findActiveFare(dep, arr);
if (fare.isPresent()) {
    officialPrice = fare.get().getPricePerSeat();   // override
} else {
    throw new BusinessException("No regulated fare exists for route...");
}
```

- **If a regulated fare exists**: the driver's submitted price is **overridden** with the official one. A warning is logged if the driver tried to send a different price (for audit purposes), but the publish succeeds with the correct price.
- **If no fare exists**: the publish is **rejected** with a clear error. Admin must register the fare first.
- This behavior is controlled by `trajet.enforce-regulated-fare` (default `true`). Set to `false` in dev/tests if you want drivers to set their own prices.

### At update time (`PUT /api/v1/trajets/{id}`)

If the route has a regulated fare, the driver **cannot modify `pricePerSeat`**. Other fields (notes, vehicle description) remain editable.

### Non-regulated routes

Some niche routes may never appear in the official schedule. For those, drivers can set their own price as before — as long as enforcement is off for that route (no fare row exists → admin hasn't declared one). You can keep enforcement on platform-wide and accept that routes without a regulated fare simply can't be published until admin adds them; or flip `enforce-regulated-fare` to `false` for a more permissive mode.

---

## Admin workflow

### Importing fares (bulk)

**Option 1 — Upload a file**
```bash
curl -X POST http://localhost:8080/api/v1/admin/fares/import \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -F "file=@tunisia-fares-2026.json" \
  -F "format=JSON"
```

**Response:**
```json
{
  "format": "JSON",
  "total": 120,
  "created": 45,
  "updated": 72,
  "skipped": 3,
  "errors": [
    "Row 17 (Tunis → Tunis): departureCity and arrivalCity must be different",
    "Row 89 (? → Béja): departureCity is required"
  ],
  "importedBy": "admin_7f3c"
}
```

**Option 2 — Paste JSON inline**
```bash
curl -X POST http://localhost:8080/api/v1/admin/fares/import-json \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d @tunisia-fares-2026.json
```

### JSON schema

```json
[
  {
    "departureCity": "Tunis",
    "arrivalCity":   "Sousse",
    "pricePerSeat":  13.50,
    "distanceKm":    140,
    "effectiveDate": "2026-01-01",
    "source":        "MINISTERE_TRANSPORT_CIRC_2026_01"
  }
]
```

Required fields: `departureCity`, `arrivalCity`, `pricePerSeat`.
Optional: `distanceKm`, `effectiveDate`, `source`, `active`.

### CSV schema

```csv
departureCity,arrivalCity,pricePerSeat,distanceKm,effectiveDate,source
Tunis,Sousse,13.50,140,2026-01-01,MINISTERE_TRANSPORT_CIRC_2026_01
Tunis,Sfax,22.00,270,2026-01-01,MINISTERE_TRANSPORT_CIRC_2026_01
```

### Single-row management

```bash
# Create or update one route
POST /api/v1/admin/fares    {departureCity, arrivalCity, pricePerSeat, ...}

# Toggle active/inactive (soft-disable without deleting)
PATCH /api/v1/admin/fares/{id}/active?active=false

# Delete
DELETE /api/v1/admin/fares/{id}

# List (with filters)
GET /api/v1/admin/fares?city=Tunis&active=true
```

### Public lookup (driver/passenger UI)

The driver's trip creation form can show "Tarif officiel : 13.50 DT" before the driver confirms. The form simply calls:

```
GET /api/v1/fares/lookup?departureCity=Tunis&arrivalCity=Sousse
```

Returns the fare or 404 if none exists.

---

## Security

| Action | Who can perform it |
|---|---|
| `GET /api/v1/fares` (public list) | Anyone (PASSENGER, DRIVER, anonymous) |
| `GET /api/v1/fares/lookup` (route lookup) | Anyone |
| `POST /api/v1/admin/fares` (create/update) | SUPER_ADMIN, FINANCIAL_ADMIN |
| `POST /api/v1/admin/fares/import` | SUPER_ADMIN, FINANCIAL_ADMIN |
| `PATCH /api/v1/admin/fares/{id}/active` | SUPER_ADMIN, FINANCIAL_ADMIN |
| `DELETE /api/v1/admin/fares/{id}` | SUPER_ADMIN, FINANCIAL_ADMIN |

Enforced by `AdminRoleGuard` in admin-service. Direct calls to `/api/v1/fares/internal/admin/**` in trajet-service also require the `X-Internal-Secret` header via `InternalAuthFilter`.

---

## Sample seed file

The repository includes `trajet-service/src/main/resources/sample-tunisia-fares.json` with **30 real Tunisian routes** at approximate Ministry prices — useful for local dev or demo. Import it on first startup of a fresh database:

```bash
curl -X POST http://localhost:8080/api/v1/admin/fares/import-json \
  -H "Authorization: Bearer $SUPER_ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d @trajet-service/src/main/resources/sample-tunisia-fares.json
```

---

## Migration note

The new `regulated_fares` table is created automatically by Hibernate `ddl-auto: update`. For production, add a Flyway migration:

```sql
CREATE TABLE regulated_fares (
  id VARCHAR(36) PRIMARY KEY,
  departure_city VARCHAR(80) NOT NULL,
  arrival_city   VARCHAR(80) NOT NULL,
  price_per_seat DECIMAL(10,2) NOT NULL,
  distance_km    DECIMAL(6,1),
  effective_date DATE,
  source         VARCHAR(100),
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  imported_by    VARCHAR(100),
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_route (departure_city, arrival_city),
  INDEX idx_departure_city (departure_city),
  INDEX idx_arrival_city   (arrival_city)
);
```

---

## Summary

- Drivers no longer set louage prices — the Ministry of Transport does, and the platform enforces it.
- Admins import fare schedules from JSON or CSV in bulk.
- The 6 admin-role hierarchy is preserved: only SUPER_ADMIN and FINANCIAL_ADMIN can touch fare data.
- Passengers see the correct official prices, always.
- 30-route sample seed file included for immediate testing.
