# Moussefer — Backend

> Plateforme de réservation de louages interurbains tunisiens
> **Stack** : Java 21 · Spring Boot 3.3 · Spring Cloud 2023.0 · Kafka · MySQL · Redis · MinIO · Docker

---

## 🚀 Démarrage rapide

### Prérequis

- **Java 21** (OpenJDK ou Oracle JDK)
- **Maven 3.9+**
- **Docker + Docker Compose** (pour l'infra : MySQL, Redis, Kafka, MinIO)

### Lancement complet (Docker)

```bash
git clone <repo-url> moussefer && cd moussefer
cp .env.example .env       # éditer si besoin
./build.sh --docker
```

Tout démarre en ~2 minutes :
- **API Gateway** : http://localhost:8080
- **Eureka** : http://localhost:8761
- **Swagger** : http://localhost:8080/swagger-ui.html
- **Kafka UI** : http://localhost:9090
- **MinIO Console** : http://localhost:9001 (admin/admin)

### Mode développement (IDE)

```bash
./build.sh --infra                              # démarre infra (MySQL/Redis/Kafka/MinIO)
cd auth-service && mvn spring-boot:run          # puis lancer chaque service
```

---

## 🏗️ Architecture

```
                 ┌──────────────┐
                 │ API Gateway  │  :8080  — JWT, routing, rate-limit
                 └──────┬───────┘
                        │
                 ┌──────▼─────────┐
                 │ Eureka Registry │  :8761
                 └─────────────────┘

15 microservices métier, chacun avec sa base MySQL :
  auth (8081)         user (8082)         trajet (8083)
  reservation (8084)  payment (8085)      notification (8086)
  chat (8087)         voyage (8088)       demande (8089)
  avis (8090)         station (8091)      analytics (8092)
  admin (8093)        loyalty (8094)      banner (8095)

Infrastructure transverse :
  • Kafka (19 topics)         • Redis (cache + rate limiter)
  • MinIO (factures + KYC)    • Stripe (paiement)
```

---

## 📁 Structure du projet

```
.
├── README.md, CHANGELOG.md, ROADMAP.md
├── pom.xml                                   ← parent POM (Java 21, Spring Boot 3.3.7)
├── build.sh                                  ← build & deploy script
├── docker-compose.yml                        ← stack complète
├── docker-compose-infra.yml                  ← infra uniquement (mode IDE)
├── docker-compose.prod.yml                   ← production
│
├── service-registry/                         ← Eureka
├── api-gateway/                              ← Spring Cloud Gateway
│
├── auth-service/                             ← JWT, login, register, reset password
├── user-service/                             ← profils, KYC chauffeur typé
├── trajet-service/                           ← trajets, file d'attente, tarifs régulés
├── reservation-service/                      ← cycle réservation, schedulers ShedLock
├── payment-service/                          ← Stripe, refunds, factures PDF
├── chat-service/                             ← WebSocket STOMP post-paiement
├── voyage-service/                           ← voyages organisés + Hors Moussefer
├── demande-service/                          ← demandes collectives
├── avis-service/                             ← notation 1-5 étoiles
├── station-service/                          ← stations + points secondaires
├── analytics-service/                        ← KPIs, dashboard global
├── admin-service/                            ← 6 dashboards spécialisés, RBAC
├── notification-service/                     ← FCM + emails
├── loyalty-service/                          ← 1 point par DT
├── banner-service/                           ← bannières publicitaires
│
└── docs/
    ├── DEPLOYMENT.md                         ← guide déploiement
    ├── QUICK_START.md                        ← démarrage 5 min
    ├── SCREEN_TO_ENDPOINT_MAP.md             ← traçabilité frontend ↔ backend
    ├── SETUP_SUMMARY.md                      ← variables d'environnement
    └── changelogs/                           ← détails V16 → V24
```

---

## 🧪 Tests

**88 tests unitaires** couvrant les scénarios métier critiques :

```bash
mvn test                           # tous les services
cd auth-service && mvn test        # un service spécifique
```

Couverture des invariants critiques :
- Anti-double-booking (concurrence 100 threads / 8 places)
- Auto-refund Stripe (V22)
- Sécurité reset password (anti-enumeration, single-use, TTL 60min)
- Voyages organisés (cycle de vie, ownership)
- Demandes collectives (seuil, anti-doublon)
- Capacité louage = 8 places (invariant réglementaire)

---

## 🔌 Endpoints principaux

| Domaine     | Endpoint                                | Description                |
|-------------|-----------------------------------------|----------------------------|
| Auth        | `POST /api/v1/auth/login`               | Connexion (JWT)            |
| Auth        | `POST /api/v1/auth/forgot-password`     | Reset password (V22)       |
| Trajet      | `GET  /api/v1/trajets/search`           | Recherche trajets          |
| Trajet      | `POST /api/v1/trajets`                  | Publier trajet (chauffeur) |
| Réservation | `POST /api/v1/reservations`             | Créer réservation          |
| Paiement    | `POST /api/v1/payments/initiate`        | Initier paiement Stripe    |
| Voyage      | `POST /api/v1/voyages`                  | Publier voyage organisé    |
| Chat        | `WS  /ws`                               | WebSocket STOMP            |

**Documentation Swagger complète** : http://localhost:8080/swagger-ui.html

### Tester rapidement avec curl

```bash
# Connexion
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@moussefer.tn","password":"Test1234"}'

# Recherche trajets
curl "http://localhost:8080/api/v1/trajets/search?departureCity=Tunis&arrivalCity=Sousse"
```

---

## 🛠️ Variables d'environnement

Voir `.env.example`. Les principales :

```bash
# Base de données
DB_USERNAME=moussefer
DB_PASSWORD=moussefer

# JWT (256-bit secret minimum)
JWT_SECRET=<256-bit-secret>

# Stripe
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# MinIO
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin

# Inter-services (X-Internal-Secret)
INTERNAL_API_KEY=<random-secret>
```

---

## 📚 Documentation

- **[CHANGELOG.md](CHANGELOG.md)** — historique versions V16 → V24
- **[ROADMAP.md](ROADMAP.md)** — évolutions futures (taxi, bus, métro)
- **[docs/QUICK_START.md](docs/QUICK_START.md)** — guide démarrage 5 min
- **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** — déploiement production
- **[docs/SCREEN_TO_ENDPOINT_MAP.md](docs/SCREEN_TO_ENDPOINT_MAP.md)** — mapping frontend/backend

---

## 📝 Licence

Projet réalisé dans le cadre d'un Projet de Fin d'Études (PFE).
© 2026.
