# MOUSSEFER V16 — Fixes Applied
**Date:** Avril 2026 | **Tests:** 51/51 PASSED | **Services modifiés:** 9/17

---

## Bugs Critiques (🔴 Production bloquée)

### Fix #1 — TrajetRepository.reserveSeats() — SQL formula toujours 0
**Fichier:** `trajet-service/.../TrajetRepository.java`  
**Problème:** `(totalSeats - reservedSeats - availableSeats) >= seats` → toujours 0 sur trajet frais  
**Correction:** `(availableSeats - reservedSeats) >= seats`  
**Impact:** 100% des réservations lançaient une BusinessException

### Fix #12 — InternalAdminProxyController — méthodes inexistantes
**Fichier:** `user-service/.../InternalAdminProxyController.java`  
**Problème:** Appels à `setSuspension()`, `liftSuspension(userId)`, `updateVerificationStatus()` qui n'existent pas dans UserService  
**Correction:** Utiliser les vraies signatures: `suspendUser(adminId, userId, req)`, `liftSuspension(adminId, userId)`, `verifyUser(adminId, userId, req)`  
**Impact:** Erreur de compilation — user-service ne compilait pas

### Fix #13 — VoyageService.acceptReservation() — CONFIRMED sans paiement
**Fichier:** `voyage-service/.../VoyageService.java`  
**Problème:** Statut passait à CONFIRMED avant le paiement Stripe + signature incorrecte de paymentService.initiatePayment()  
**Correction:** Passer à PENDING_PAYMENT, CONFIRMED déclenché par webhook Stripe  
**Impact:** Points fidélité et places décrémentés avant encaissement

### Fix #10 — AdminStatisticsController — paths analytics 404
**Fichier:** `admin-service/.../AdminStatisticsController.java`  
**Problème:** Appelait `/api/v1/analytics/dashboard` (inexistant), vrai chemin = `/api/v1/analytics/internal/admin/dashboard`  
**Correction:** 3 URLs corrigées (dashboard, top-routes, alerts)  
**Impact:** Toutes les stats analytics retournaient null/erreur

### Fix #11 — AdminBannersProxyController — CRUD bannières 404
**Fichier:** `admin-service/.../AdminBannersProxyController.java`  
**Problème:** POST/PUT/DELETE vers `/api/v1/banners` (public, read-only). Vrai chemin admin = `/api/v1/banners/internal/admin`  
**Correction:** 7 URLs corrigées  
**Impact:** Impossible de créer/modifier/supprimer des bannières

---

## Bugs Hauts (🟠 Comportement incorrect)

### Fix #2 — ReservationService.adminForceConfirm() — seats non décrémentés
**Fichier:** `reservation-service/.../ReservationService.java`  
**Problème:** `confirmSeats(r)` manquant → reservedSeats reste > 0, availableSeats reste gonflé  
**Correction:** Ajout de `confirmSeats(r)` avant changement de statut  

### Fix #5 — AuthService — checks désactivés + URLs incorrectes
**Fichier:** `auth-service/.../AuthService.java`  
**Problème:** Vérification actif/suspendu commentée + URL fetchAdminRole incorrecte  
**Correction:** Checks réactivés avec fail-open; URLs corrigées  

### Fix #6 — @EnableJpaAuditing manquant
**Fichier:** `auth-service/.../AuthServiceApplication.java`  
**Problème:** @CreatedDate / @LastModifiedDate jamais remplis  
**Correction:** `@EnableJpaAuditing` ajouté  

### Fix #7 — TrajetServiceApplication mauvais package + @EnableCaching manquant
**Fichier:** `trajet-service/.../TrajetServiceApplication.java`  
**Problème:** Fichier à `com/moussefer/` mais déclare `com.moussefer.trajet` + cache Redis ignoré  
**Correction:** Déplacé dans le bon package + `@EnableCaching` ajouté  

### Fix #8 — TrajetService.search() seats bruts vs nets
**Fichier:** `trajet-service/.../TrajetService.java`  
**Problème:** Filtre sur `availableSeats` sans soustraire `reservedSeats`  
**Correction:** `netFree = availableSeats - reservedSeats`  

### Fix #9 — AdminRoleGuard ne protège pas /roles CRUD
**Fichier:** `admin-service/.../AdminRoleGuard.java`  
**Problème:** Mutations POST/PUT/DELETE sur `/api/v1/admin/roles` accessibles à tous les admins  
**Correction:** Restriction SUPER_ADMIN étendue aux mutations `/roles`  

---

## Bugs Moyens (🟡 Minor incorrect behaviour)

### Fix #3 — Loyalty rounding: intValue() truncate
**Fichier:** `payment-service/.../StripeWebhookService.java`  
**Correction:** `.setScale(0, HALF_UP).intValue()` — 12.99 → 13 pts  

### Fix #4 — internalCheckConfirmed: driverId non vérifié
**Fichier:** `reservation-service/.../ReservationController.java`  
**Correction:** Vérification `r.getDriverId().equals(driverId)` ajoutée  

---

## Nouvelles fonctionnalités (US-92, US-126)

### Fix #14 — ReservationVoyageStatus.PENDING_PAYMENT ajouté
**Fichier:** `voyage-service/.../ReservationVoyageStatus.java`  

### Fix #15 — VoyageEventProducer.sendVoyagePaymentConfirmed()
**Fichier:** `voyage-service/.../VoyageEventProducer.java`  

### Fix #16 — Consumer demande.merged dans notification-service
**Fichier:** `notification-service/.../DemandeConvertedConsumer.java`  
