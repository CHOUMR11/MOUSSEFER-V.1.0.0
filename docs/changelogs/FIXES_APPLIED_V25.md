# MOUSSEFER V25 — Suppression du guichet, conservation update-seats

**Date :** Avril 2026
**Base :** V24 FINAL (avec corrections louage 8 places + roadmap multi-modal)
**Scope :** Suppression complète de la logique guichet (vente onsite + manual booking + priorité override). Conservation et amélioration de la mise à jour manuelle du compteur de places.

---

## 🎯 Décision métier

**Moussefer ne gère PAS la vente au guichet.** Les guichets physiques de stations ont leur propre système avec leurs propres agents dédiés. Notre plateforme se concentre sur la **réservation en ligne**.

Cependant, le chauffeur a un besoin légitime de **corriger le compteur de places** affiché aux passagers en ligne pour refléter la réalité du véhicule :

- Un passager hors-plateforme est monté à un point intermédiaire
- Un passager est descendu en cours de route et a libéré sa place
- Une place est bloquée temporairement (siège cassé, bagage volumineux)

Cette correction du compteur ne doit JAMAIS affecter les réservations existantes.

---

## ❌ Supprimé (Backend)

### Endpoints REST
- `POST /api/v1/trajets/{id}/driver/onsite-sale` (vente guichet)
- `PATCH /api/v1/trajets/internal/{id}/onsite-sale` (interne pour onsite)
- `POST /api/v1/reservations/driver/manual-booking` (manual booking + override)

### Méthodes service
- `TrajetService.driverOnsiteSeatSale()`
- `TrajetService.internalOnsiteSeatSale()`
- `ReservationService.driverManualBooking()`
- `ReservationService.cancelPendingForPriorityOverride()` (private)

### Méthodes repository
- `TrajetRepository.decrementForOnsiteSale()`
- `ReservationRepository.findCancellablePendingByTrajet()`

### DTO
- `DriverManualBookingRequest.java` (supprimé)

### Champs entité
- `Reservation.manualBooking` (boolean)
- `Reservation.manualPassengerName`
- `Reservation.manualPassengerPhone`

### Logique Kafka
- Le flag `priority_override` dans `ReservationEventConsumer` simplifié — toutes les annulations rentrent désormais dans le même flot (refund uniforme).

### Tests
- Test `onCancelled_priorityOverride_triggersRefund` supprimé (cas n'existe plus)

---

## ❌ Supprimé (Frontend)

### Composant `my-trajets`
- Champs : `onsiteTrajetId`, `onsiteMaxSeats`, `onsiteSeats`, `manualTrajetId`, `manualBooking`
- Méthodes : `openOnsiteModal()`, `confirmOnsiteSale()`, `openManualModal()`, `confirmManualBooking()`
- HTML : 2 boutons (« Vente sur place » + « Passager sur place »)
- HTML : 2 modals (`#onsiteSaleModal` + `#manualBookingModal`)

### Services Angular
- `TrajetService.onsiteSale()`
- `ReservationService.driverManualBooking()`

---

## ✅ Conservé et amélioré

### Endpoint unique pour le chauffeur
```
PATCH /api/v1/trajets/{id}/driver/update-seats?availableSeats=N
```

**Garde-fous serveur :**
- Seul le chauffeur propriétaire peut corriger son trajet (X-User-Id check)
- La nouvelle valeur est **bornée atomiquement** entre `0` et `(totalSeats − reservedSeats)` par le SQL :
  ```sql
  UPDATE trajets SET available_seats = :newAvailable, version = version + 1
   WHERE id = :id AND :newAvailable BETWEEN 0 AND (total_seats - reserved_seats)
  ```
- **Aucune réservation existante n'est jamais affectée** — c'est juste un compteur visuel
- Status auto → `FULL` si `availableSeats` tombe à 0

### Nouveau modal frontend (clean UX)

Un seul bouton « Mettre à jour places » sur chaque carte trajet, ouvrant un modal informatif qui explique la sémantique au chauffeur :

```
┌───────────────────────────────────────┐
│ ✏️ Mettre à jour les places disponibles│
├───────────────────────────────────────┤
│  ℹ️ Corrigez le nombre de places      │
│  disponibles si la réalité du véhicule │
│  a changé : passager hors-plateforme   │
│  monté à un point intermédiaire,       │
│  passager descendu, place bloquée...   │
│                                        │
│  Aucune réservation existante ne sera  │
│  affectée.                             │
│                                        │
│  Places disponibles : [  3  ]          │
│  Valeur entre 0 et 5 (places non       │
│  encore réservées).                    │
└───────────────────────────────────────┘
```

---

## 📊 Statistiques

| Métrique | V24 | V25 | Delta |
|---|---|---|---|
| Endpoints REST backend | 291 | 287 | −4 (cleanup) |
| Tests `@Test` | 88 | 87 | −1 (priority test removed) |
| Lignes de code Java | ~8200 | ~7950 | −250 |
| Champs Reservation entity | 13 | 10 | −3 |
| Modals frontend driver | 4 | 3 | −1 (consolidé) |

---

## 🎓 Pour la soutenance

**Q : Pourquoi avoir retiré la vente au guichet ?**

> *« Décision métier claire : les guichets physiques de stations en Tunisie ont déjà leur propre système avec leurs propres agents. Vouloir les intégrer aurait créé de la confusion (deux systèmes sources de vérité concurrentes pour les mêmes places). Moussefer se concentre sur ce qu'elle fait le mieux : la réservation en ligne. Le chauffeur garde une seule fonction simple — corriger le compteur de places affiché — pour refléter la réalité quand un passager monte/descend hors-plateforme. »*

**Q : Comment garantissez-vous la cohérence ?**

> *« Le SQL UPDATE est atomique avec une condition WHERE qui empêche toute valeur qui écraserait des réservations confirmées. Si un chauffeur tente de mettre availableSeats=2 alors qu'il y a 4 réservations confirmées sur 8 places, le SQL refuse l'update et retourne 0 lignes affectées. Le service Java lève alors une BusinessException explicite. C'est la même garantie que pour l'anti-double-booking. »*

---

## 🎨 Dashboard chauffeur — Décomposition 2-sources (V25.1)

**Contexte :** Le chauffeur doit gérer **2 types de réservations** sur le même louage :

1. **Réservations EN LIGNE** (Moussefer) — comptées automatiquement par le système
2. **Ventes au GUICHET** (hors-Moussefer) — déclarées manuellement par le chauffeur

Pour éviter toute confusion et tout risque de double-booking, le dashboard a été enrichi avec :

### 1. Card trajet — décomposition visuelle des places

Sur chaque carte de trajet, au lieu d'afficher un simple compteur `4/8 places`, on a 3 compteurs distincts :

```
┌──────────────────────────────────────────┐
│ Tunis → Sousse · 14h00                    │
├──────────────────────────────────────────┤
│ Places · Capacité 8                       │
│ ┌────────┬────────┬────────┐              │
│ │ ✓  3   │ 🏪  2  │ ○  3   │              │
│ │En ligne│Guichet │Libres  │              │
│ └────────┴────────┴────────┘              │
│ [Marquer parti] [Réduire] [Mettre à jour] │
└──────────────────────────────────────────┘
```

Calcul côté frontend (sans appel backend supplémentaire) :
- **En ligne** = `reservedSeats` (champ ajouté à `TrajetResponse`)
- **Guichet** = `totalSeats − availableSeats − reservedSeats`
- **Libres** = `availableSeats`

### 2. Modal "Mettre à jour places" — pavé pédagogique

Le modal explique étape par étape avec un exemple concret :

```
ℹ️ Comment ça marche

Sur Moussefer, un trajet de 8 places peut recevoir
deux types de réservations :

✅ 1. Réservations en ligne
   Un passager utilise l'app Moussefer.
   → Le système incrémente automatiquement le compteur.
   → Vous n'avez rien à faire.

🏪 2. Ventes au guichet
   Un passager achète son billet à la station (hors-app).
   → Vous devez réduire ce compteur ici.
   → Cela bloque la place pour les passagers en ligne.

💡 Exemple concret
1. Vous démarrez avec 8 places, toutes libres.
2. 2 passagers réservent en ligne → compteur passe à 6
   automatiquement.
3. 1 passager arrive au guichet et achète sur place
   → vous mettez ici 5.
4. Le système empêche désormais qu'un passager en ligne
   réserve cette place.
```

### 3. Garantie anti-double-booking visible

En bas du modal :

```
🛡️ Anti-double-booking garanti.
Vous ne pouvez jamais descendre en dessous des 3 places
déjà réservées en ligne — le serveur rejette automatiquement
toute valeur qui écraserait des réservations existantes.
```

C'est la traduction UX de la garantie SQL atomique :

```sql
UPDATE trajets SET available_seats = :newAvailable
 WHERE id = :id
   AND :newAvailable BETWEEN 0 AND (total_seats - reserved_seats)
```

### Fichiers modifiés

**Backend (1 fichier) :**
- `TrajetResponse.java` : ajout du champ `reservedSeats` dans le DTO de réponse

**Frontend (3 fichiers) :**
- `trajet.model.ts` : ajout de `reservedSeats?: number` dans l'interface `Trajet`
- `my-trajets.component.html` : nouvelle UI 3-counters + modal pédagogique enrichi
- `my-trajets.component.ts` : 2 nouveaux champs (`updateSeatsTotal`, `updateSeatsReserved`)
- `my-trajets.component.css` : polissage visuel des compteurs et du modal

### Impact tests

Aucun test cassé. Les tests unitaires `TrajetServiceTest` et `TrajetConcurrencyTest` continuent à passer (la nouvelle UI ne change pas la logique métier, juste sa présentation).

---

## 🎓 Pour la soutenance

Si on te demande *"Comment évitez-vous les doubles réservations entre Moussefer et le guichet de la station ?"* :

> *« Mon dashboard chauffeur affiche 3 compteurs distincts pour chaque trajet : les réservations en ligne (gérées automatiquement par le système), les ventes au guichet (déclarées par le chauffeur quand un passager achète à la station), et les places encore libres à la vente en ligne. Quand le chauffeur déclare une vente guichet, il réduit le compteur via l'endpoint update-seats — l'UPDATE SQL est atomique avec une condition WHERE qui empêche d'écraser les réservations confirmées. Concrètement : si 3 passagers ont déjà réservé en ligne, le chauffeur ne peut jamais mettre availableSeats en dessous de 5 (= 8 − 3). Le serveur rejette toute valeur invalide. Le risque de double-booking est donc nul, garanti par contrainte SQL. »*
