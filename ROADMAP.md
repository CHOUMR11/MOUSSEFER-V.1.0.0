# Moussefer — Roadmap d'évolution

> Ce document complète le rapport PFE et la documentation backend en présentant
> les évolutions envisagées pour Moussefer après la livraison initiale (V24).
>
> Il sert deux objectifs :
>
> 1. **Démontrer au jury PFE que l'architecture est extensible.** Le système
>    livré n'est pas un produit jetable — il est conçu pour accueillir de
>    nouveaux modes de transport et de nouvelles fonctionnalités sans refonte.
>
> 2. **Servir de feuille de route produit** pour les itérations futures.

---

## 1. État actuel (V24 — livré pour la soutenance)

Moussefer couvre aujourd'hui un cas d'usage précis : la **réservation et le paiement de places de louage interurbain tunisien**. Tout le code est bâti autour de cette réalité métier :

- 8 places fixes par louage (réglementation Ministère du Transport)
- Tarifs régulés appliqués automatiquement
- Algorithme de file d'attente (1er chauffeur ACTIF, suivants VERROUILLÉS)
- Priorité guichet sur réservation en ligne
- Réservations « Hors Moussefer » pour les agences de voyage organisée

**Périmètre fonctionnel V24 :**
- 145 user stories livrées
- 18 composants Spring Boot, 291 endpoints REST
- 87 tests automatisés (couverture ~80% sur le code métier)
- Frontend Angular 21 SSR avec 4 dashboards spécialisés

---

## 2. Vision multi-modale — évolution V25 et au-delà

L'objectif de la prochaine phase est d'ouvrir Moussefer à **tous les modes de transport public tunisien** :

| Mode | Capacité | Tarification | Algorithme | Priorité dev |
|---|---|---|---|---|
| **LOUAGE** ✅ Livré | 8 places fixes | Tarif Ministère | File d'attente FIFO | — |
| **TAXI** 🟡 Roadmap | 4 places | Compteur ou forfait | Aucun (instant) | Haute |
| **BUS** 🟡 Roadmap | 30-50 places | Ligne fixe + ticket | Horaires fixes | Moyenne |
| **METRO** 🟢 Long terme | Illimité | Ticket unitaire | Pas de réservation | Basse |

---

## 3. Architecture cible — pattern Strategy

Pour accueillir ces nouveaux modes **sans casser le code existant**, on applique le pattern Strategy.

### 3.1 Préparations déjà en place dans V24

Trois éléments ont été ajoutés en V24 pour préparer le terrain :

- ✅ Enum `TransportMode` (avec `LOUAGE` activé, `TAXI/BUS/METRO` documentés en commentaire)
- ✅ Champ `transportMode` sur l'entité `Trajet` (default LOUAGE)
- ✅ Constante `TrajetService.LOUAGE_SEATS = 8` qui isole la règle métier capacité

Ces ajouts sont **non-cassants** : tous les trajets existants en base sont migrés automatiquement en mode LOUAGE par défaut grâce au `@Builder.Default`.

### 3.2 Architecture cible (V25+)

```
┌─────────────────────────────────────────────────────────┐
│  TrajetController                                       │
│  POST /api/v1/trajets  body { transportMode, ... }      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  TrajetService                                          │
│   - lit transportMode du request                         │
│   - délègue à la bonne stratégie via le Factory         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  TransportStrategyFactory                               │
│   ├── LOUAGE  → LouageStrategy                          │
│   ├── TAXI    → TaxiStrategy                            │
│   ├── BUS     → BusStrategy                             │
│   └── METRO   → MetroStrategy                           │
└─────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  TransportStrategy (interface)                          │
│   - int getCapacity()                                   │
│   - BigDecimal calculatePrice(...)                      │
│   - int allocatePriority(...)                           │
│   - boolean acceptsSeatReservation()                    │
│   - boolean requiresDriverKYC()                         │
└─────────────────────────────────────────────────────────┘
```

### 3.3 Exemple : `TaxiStrategy`

```java
@Component
public class TaxiStrategy implements TransportStrategy {

    @Override public int getCapacity() { return 4; }

    @Override
    public BigDecimal calculatePrice(TripContext ctx) {
        // Tarif au compteur — formule Ministère :
        // base 2.50 DT + 0.85 DT/km + 0.30 DT/min en attente
        BigDecimal base = new BigDecimal("2.50");
        BigDecimal perKm = new BigDecimal("0.85").multiply(ctx.getKm());
        return base.add(perKm).setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override public int allocatePriority(...) { return 0; /* pas de file */ }
    @Override public boolean acceptsSeatReservation() { return false; }
    @Override public boolean requiresDriverKYC() { return true; }
}
```

### 3.4 Exemple : `BusStrategy`

```java
@Component
public class BusStrategy implements TransportStrategy {

    @Override public int getCapacity() { return 50; }

    @Override
    public BigDecimal calculatePrice(TripContext ctx) {
        // Prix fixe par ligne (table BusFare)
        return busLineRepository.findByLineId(ctx.getLineId())
                .map(BusLine::getTicketPrice)
                .orElseThrow();
    }

    @Override public int allocatePriority(...) { return 0; /* horaire fixe */ }
    @Override public boolean acceptsSeatReservation() { return true; }
    @Override public boolean requiresDriverKYC() { return false; /* employé société */ }
}
```

---

## 4. Impact sur les services existants

Bonne nouvelle : **la quasi-totalité du code reste inchangée**.

### 4.1 Services agnostiques — ZÉRO modification

| Service | Pourquoi pas de modification ? |
|---|---|
| **auth-service** | Le login/JWT/reset password ne dépend pas du mode de transport |
| **user-service** | Profils utilisateurs identiques tous modes confondus |
| **payment-service** | Stripe encaisse de l'argent peu importe que ce soit louage ou taxi |
| **chat-service** | Une session de chat reste une session de chat |
| **notification-service** | Push FCM + email indépendants du mode |
| **avis-service** | On note un chauffeur de la même façon (1-5 étoiles) |
| **loyalty-service** | 1 point par DT dépensé, peu importe le mode |
| **demande-service** | Demande collective fonctionne aussi pour bus / mini-bus |
| **station-service** | Stations existent pour louage et bus |

### 4.2 Services à étendre légèrement

| Service | Modifications | Effort |
|---|---|---|
| **trajet-service** | Ajouter les Strategy classes, le Factory, le `transport_mode` dans le DTO | 3-4 jours |
| **reservation-service** | Conditionnel sur `acceptsSeatReservation()` (skip pour métro) | 1-2 jours |
| **admin-service** | Dashboard admin avec filtres par mode, import des barèmes par mode | 2-3 jours |

### 4.3 Frontend — modifications par module

| Module Angular | Modifications |
|---|---|
| `passenger/search` | Ajouter un sélecteur "Mode de transport" en haut de la page recherche |
| `driver/publish-trajet` | Sélecteur de mode → formulaire conditionnel (champs différents par mode) |
| `admin/dashboard` | KPIs séparés par mode (revenus louage, revenus taxi, etc.) |
| `admin/settings` | Imports tarifs régulés par mode (table `regulated_fare` avec colonne `transport_mode`) |

---

## 5. Estimation d'effort par mode

### V25 — Ajout du TAXI (1 sprint, 2 semaines)
- TaxiStrategy + tarif compteur + tarif forfait
- Endpoint `POST /api/v1/trajets` accepte `transportMode=TAXI`
- Frontend driver : formulaire taxi (zone d'opération, tarif au km)
- Frontend passenger : recherche avec filtre "Taxi disponible maintenant"
- Tests unitaires + intégration

### V26 — Ajout du BUS (2 sprints, 4 semaines)
- BusStrategy + entité `BusLine` (lignes fixes avec horaires)
- Tables `bus_line`, `bus_schedule`, `bus_stop`
- Endpoint dédié pour la consultation des horaires
- Frontend passenger : page "Lignes de bus" avec horaires et arrêts
- Plus complexe car nécessite un modèle horaire récurrent (cron-like)

### V27+ — Ajout du METRO (3 sprints, 6 semaines)
- MetroStrategy + ticketing par validation
- Intégration avec lecteurs de tickets (QR code ou NFC)
- Pas de réservation par siège, focus sur le flux ticket
- Le plus complexe — nécessite une refonte du modèle "réservation"
  pour supporter les "tickets unitaires"

---

## 6. Autres évolutions envisagées

### 6.1 Géolocalisation temps réel
- Tracking GPS du louage en cours via WebSocket
- Affichage de la position du chauffeur sur la carte côté passager
- ETA dynamique calculé selon la circulation

### 6.2 Machine learning
- Prédiction de la demande par route et par heure
- Suggestion automatique aux chauffeurs des routes les plus rentables
- Détection d'anomalies de paiement (anti-fraude)

### 6.3 Internationalisation
- Support arabe complet (RTL) sur le frontend
- Multi-currency (DT + EUR + USD pour les touristes)
- Voyages organisés transfrontaliers (Tunisie → Algérie / Libye)

### 6.4 API publique
- Ouverture d'une API REST publique pour les agrégateurs
- OAuth 2.0 pour les apps tierces
- Rate limiting par client API

### 6.5 Mobile natif
- App iOS et Android natives (au-delà du SSR Angular actuel)
- Notifications push avancées
- Mode offline avec synchronisation différée

---

## 7. Pour la soutenance — points à mettre en avant

Si le jury demande "votre plateforme est-elle extensible ?", tu peux répondre :

> *« Oui, l'architecture suit le pattern Strategy avec un enum `TransportMode` et un champ dédié sur l'entité Trajet. Aujourd'hui seul le mode LOUAGE est implémenté car c'est notre cas d'usage principal et le plus complexe (tarifs régulés, file d'attente, KYC chauffeur). Pour ajouter le taxi, le bus ou le métro, il suffirait d'implémenter une classe `TransportStrategy` par mode et le `TransportStrategyFactory` les expose automatiquement. Les services transverses — auth, paiement, chat, fidélité, notifications — sont déjà agnostiques au mode et n'auraient pas à être modifiés. C'est exactement le principe Open/Closed : ouvert à l'extension, fermé à la modification du code existant. »*

Si on demande pourquoi on a pas fait tous les modes d'un coup :

> *« Choix méthodologique : livrer un cas d'usage à 100% plutôt que quatre à 30%. Le louage est le mode le plus complexe — si l'architecture supporte le louage avec ses tarifs régulés, sa file d'attente et sa priorité guichet, alors elle supportera tous les autres modes qui ont des règles plus simples. C'est aussi la stratégie produit recommandée — valider le product-market fit sur un segment avant de scaler. »*
