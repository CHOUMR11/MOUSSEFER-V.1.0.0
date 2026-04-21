# Moussefer — Microservices Architecture

> Plateforme de réservation de louages interurbains  
> Stack: **Java 21 · Spring Boot 3.3 · Spring Cloud · Kafka · MySQL · Redis · Docker**

---

## Architecture Overview

```
Client (Mobile / Web)
        │
        ▼
  ┌──────────────┐
  │  API Gateway  │  :8080  — JWT validation, routing, rate limiting
  └──────┬───────┘
         │  (lb://)
  ┌──────▼──────────────────────────────────────────────┐
  │                  Eureka Service Registry  :8761       │
  └──────────────────────────────────────────────────────┘

  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐
  │ auth-service│  │ user-service│  │  trajet-service  │
  │    :8081    │  │    :8082    │  │      :8083       │
  └─────────────┘  └─────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐
  │reservation-service│  │ payment-service  │
  │      :8084        │  │     :8085        │
  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │notification-svc  │  │  chat-service    │  │  voyage-service  │
  │     :8086        │  │     :8087        │  │     :8088        │
  └──────────────────┘  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐
  │  demande-service │  │   avis-service   │
  │     :8089        │  │     :8090        │
  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │ station-service  │  │analytics-service │  │  admin-service   │
  │     :8091        │  │     :8092        │  │     :8093        │
  └──────────────────┘  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐
  │ loyalty-service  │  │ banner-service   │
  │     :8094        │  │     :8095        │
  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │ station-service  │  │analytics-service │  │  admin-service   │
  │     :8091        │  │     :8092        │  │     :8093        │
  └──────────────────┘  └──────────────────┘  └──────────────────┘

  ┌──────────────────┐  ┌──────────────────┐
  │ loyalty-service  │  │  banner-service  │
  │     :8094        │  │     :8095        │
  └──────────────────┘  └──────────────────┘

  Infrastructure:
  ├── Kafka + Zookeeper  (async event bus)
  ├── Redis              (session cache, rate limiting)
  ├── MinIO              (file storage: invoices, documents)
  └── MySQL x15          (one DB per service)
```

---

## Services & Responsabilités

| Service | Port | DB | Description |
|---|---|---|---|
| `service-registry` | 8761 | — | Eureka Server — découverte de services |
| `api-gateway` | 8080 | Redis | Routing, JWT filter, CORS, rate limiting |
| `auth-service` | 8081 | `moussefer_auth` | Register, login, JWT access + refresh tokens |
| `user-service` | 8082 | `moussefer_users` | Profils Chauffeur/Passager/Organisateur |
| `trajet-service` | 8083 | `moussefer_trajet` | Publication de départs, système de priorité |
| `reservation-service` | 8084 | `moussefer_reservation` | Cycle réservation + scheduler timeout 15min |
| `payment-service` | 8085 | `moussefer_payment` | Stripe, promo codes, PDF invoice (iText7) |
| `notification-service` | 8086 | `moussefer_notification` | Consumer Kafka pur — Push, Email, In-app |
| `chat-service` | 8087 | `moussefer_chat` | WebSocket STOMP — activé post-paiement |
| `voyage-service` | 8088 | `moussefer_voyage` | Voyages organisés |
| `demande-service` | 8089 | `moussefer_demande` | Demandes collectives + seuil de déclenchement |
| `avis-service` | 8090 | `moussefer_avis` | Ratings + recompute driver score via Kafka |
| `station-service` | 8091 | `moussefer_station` | Interurban louage station management (GPS, active/inactive) |
| `analytics-service` | 8092 | `moussefer_analytics` | Dashboard KPIs: bookings, revenue, cancellations, user growth |
| `admin-service` | 8093 | `moussefer_admin` | Centralized audit log for all admin actions |
| `loyalty-service` | 8094 | `moussefer_loyalty` | Points fidélité: earn on payment, redeem for discounts |
| `banner-service` | 8095 | `moussefer_banner` | Advertising banners with scheduling and audience targeting |
| `station-service` | 8091 | `moussefer_station` | Interurban louage station & GPS management |
| `analytics-service` | 8092 | `moussefer_analytics` | Dashboard KPIs — Kafka event consumer |
| `admin-service` | 8093 | `moussefer_admin` | Centralized audit log for admin actions |
| `loyalty-service` | 8094 | `moussefer_loyalty` | Points, tiers (Bronze→Platinum), redemption |
| `banner-service` | 8095 | `moussefer_banner` | Advertising banners with scheduling & targeting |

---

## Kafka Topics

| Topic | Producer | Consumer(s) |
|---|---|---|
| `user.registered` | auth-service | user-service |
| `trajet.published` | trajet-service | notification-service |
| `trajet.activated` | trajet-service | notification-service |
| `reservation.created` | reservation-service | notification-service |
| `reservation.accepted` | reservation-service | notification-service |
| `reservation.refused` | reservation-service | notification-service |
| `reservation.confirmed` | reservation-service | notification-service, chat-service |
| `reservation.escalated` | reservation-service | notification-service, trajet-service |
| `reservation.driver.reminder` | reservation-service | notification-service |
| `payment.confirmed` | payment-service | reservation-service, notification-service, chat-service |
| `payment.failed` | payment-service | notification-service |
| `demande.threshold.reached` | demande-service | notification-service |
| `avis.driver.rating.updated` | avis-service | user-service |
| `voyage.payment.confirmed` | voyage-service | notification-service |
| `voyage.reservation.confirmed` | voyage-service | loyalty-service |
| `payment.confirmed` | payment-service | reservation-service, notification-service, chat-service, analytics-service, loyalty-service |
| `reservation.created` | reservation-service | notification-service, analytics-service |
| `reservation.cancelled` | reservation-service | notification-service, analytics-service |
| `reservation.escalated` | reservation-service | notification-service, trajet-service, analytics-service |
| `trajet.published` | trajet-service | notification-service, analytics-service |
| `user.registered` | auth-service | user-service, analytics-service |
| `voyage.reservation.confirmed` | voyage-service | loyalty-service |

---

## Security Model

```
JWT issued by auth-service
        │
        ▼
API Gateway validates token
        │
        ├── Valid  → injects X-User-Id, X-User-Email, X-User-Role headers
        │            downstream services trust headers (no re-validation)
        │
        └── Invalid → 401 UNAUTHORIZED (never reaches downstream)
```

**Token claims:**
```json
{
  "sub": "user@email.com",
  "userId": "uuid",
  "role": "PASSENGER | DRIVER | ORGANIZER | ADMIN",
  "iat": 1700000000,
  "exp": 1700086400
}
```

---

## Driver Timeout Flow (15 min rule)

```
Passenger books → reservation.created (status: PENDING_DRIVER)
                │
                ├── t+5min  → reminder sent to driver (Kafka → notification-service)
                ├── t+15min → reservation ESCALATED
                │            → next trajet in queue ACTIVATED
                │            → admin notification sent
                └── Driver responds before deadline → ACCEPTED or REFUSED
```

---

## Quick Start

**👉 New to the project? Start here:** [QUICK_START.md](QUICK_START.md) (5 minutes to running)

### Prerequisites
- Docker & Docker Compose v2
- JDK 21 (for local build)
- Maven 3.9+

### Fastest Setup (Full Docker)
```bash
cp .env.example .env  # Already configured for local dev
docker-compose up --build
```

### Development Setup (IDE)
```bash
docker-compose -f docker-compose-infra.yml up -d
# Then run services from your IDE (IntelliJ/VS Code)
```

### Production Deployment
See [DEPLOYMENT.md](DEPLOYMENT.md) for complete guide:
- Production configuration
- Docker image building
- Kubernetes deployment
- Database backups
- Monitoring & logging
- Security checklist

### Access Points
| Interface | URL | Credentials |
|---|---|---|
| **API Gateway** | http://localhost:8080 | — |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Eureka Dashboard** | http://localhost:8761 | eureka / eureka123 |
| **Kafka UI** | http://localhost:9090 | — |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin |

---

## API Endpoints Summary

### Auth
```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh       Header: X-Refresh-Token
POST /api/v1/auth/logout        Header: Authorization
```

### Trajets
```
POST   /api/v1/trajets                    Driver: publish departure
GET    /api/v1/trajets/search?dep=&arr=&seats=&date=
GET    /api/v1/trajets/{id}
POST   /api/v1/trajets/{id}/depart        Driver: mark departed
PATCH  /api/v1/trajets/{id}/reduce-seats  Driver: offline booking
```

### Reservations
```
POST   /api/v1/reservations               Passenger: book
POST   /api/v1/reservations/{id}/accept   Driver
POST   /api/v1/reservations/{id}/refuse   Driver
DELETE /api/v1/reservations/{id}          Passenger: cancel
GET    /api/v1/reservations/my
GET    /api/v1/reservations/driver/pending
```

### Payments
```
POST /api/v1/payments/initiate
GET  /api/v1/payments/reservation/{reservationId}
POST /api/v1/payments/webhook              Stripe webhook
```

### Chat (WebSocket)
```
CONNECT  ws://host/ws  (STOMP)
SEND     /app/chat/{reservationId}/send
SUBSCRIBE /topic/chat/{reservationId}
GET      /api/v1/chat/{reservationId}/history
```

### Voyages
```
GET  /api/v1/voyages
POST /api/v1/voyages/{id}/reserve?seats=
POST /api/v1/voyages/reservations/{resaId}/accept
```

### Demandes Collectives
```
POST /api/v1/demandes/join?departureCity=&arrivalCity=&date=&seats=
```

### Avis
```
POST /api/v1/avis?driverId=&trajetId=&rating=&comment=
GET  /api/v1/avis/driver/{driverId}
```

### Stations
```
GET    /api/v1/stations                   Active stations
GET    /api/v1/stations/{id}
GET    /api/v1/stations/city/{city}
POST   /api/v1/stations                   Admin: create station
PUT    /api/v1/stations/{id}              Admin: update station
DELETE /api/v1/stations/{id}              Admin: deactivate station
```

### Analytics (Admin)
```
GET /api/v1/analytics/dashboard           KPIs: bookings, revenue, escalations
```

### Loyalty
```
GET  /api/v1/loyalty/me                   My points & tier
POST /api/v1/loyalty/redeem               Redeem points
GET  /api/v1/loyalty/history              Points transaction history
```

### Banners (Admin)
```
GET    /api/v1/banners?audience=          Active banners by audience
GET    /api/v1/banners/all                Admin: all banners
POST   /api/v1/banners                    Admin: create banner
PUT    /api/v1/banners/{id}               Admin: update banner
DELETE /api/v1/banners/{id}               Admin: delete banner
```

---

## Project Structure

```
moussefer/
├── pom.xml                    (parent — Java 21, Spring Cloud 2023.0.3)
├── docker-compose.yml
├── .env.example
├── service-registry/
├── api-gateway/
├── auth-service/
├── user-service/
├── trajet-service/
├── reservation-service/
├── payment-service/
├── notification-service/
├── chat-service/
├── voyage-service/
├── demande-service/
├── avis-service/
├── station-service/
├── analytics-service/
├── admin-service/
├── loyalty-service/
└── banner-service/
```

Each service follows the same internal structure:
```
{service}/
├── Dockerfile
├── pom.xml
└── src/main/
    ├── java/com/moussefer/{name}/
    │   ├── {Service}Application.java
    │   ├── config/
    │   ├── controller/
    │   ├── dto/{request,response}/
    │   ├── entity/
    │   ├── exception/
    │   ├── kafka/
    │   ├── repository/
    │   └── service/
    └── resources/
        └── application.yml
```

---

## Tech Decisions

| Decision | Rationale |
|---|---|
| **Database per service** | Loose coupling — no shared schema, each service owns its data |
| **JWT validated at gateway only** | Eliminates N crypto operations per request; headers are trusted internally |
| **Kafka for inter-service events** | Decouples producers from consumers; notification-service is a pure consumer |
| **Schedulers in reservation-service** | Timeout logic lives where the domain is — not in a generic job service |
| **iText7 for PDF** | Production-grade PDF library — handles fonts, tables, unicode |
| **Stripe PaymentIntents** | Industry standard; client-side confirmation pattern avoids storing card data |
| **WebSocket STOMP** | Built into Spring; scales naturally with a message broker (Redis/RabbitMQ for prod) |
| **Redis for trajet cache** | Priority queue reads are hot — 5min TTL reduces DB load significantly |

---

*Generated architecture — Moussefer Platform v1.0.0*
