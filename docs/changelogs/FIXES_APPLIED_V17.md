# MOUSSEFER V17 — Role Restrictions & Organizer Account Flow

**Date:** April 2026
**Base:** V16 FIXED (16 bug fixes + 51 tests passed)
**New policy:** Self-registration restricted to PASSENGER and DRIVER only. ORGANIZER accounts are created manually by a SUPER_ADMIN.

---

## Why this change

The Moussefer business model treats organizers as trusted third parties who publish paid tourism voyages. Allowing self-registration for ORGANIZER would let anyone bypass vetting and list unverified group tours. V17 enforces the rule that was documented in the scenarios (`scenarios_de_mon_projet.md`) but not previously enforced in code.

---

## Role matrix — who creates whom

| Role       | Created by                             | Can publish voyages | Can publish trajets |
|------------|----------------------------------------|---------------------|---------------------|
| PASSENGER  | Self-registration `/api/v1/auth/register` | No                  | No                  |
| DRIVER     | Self-registration `/api/v1/auth/register` | No                  | Yes                 |
| ORGANIZER  | **SUPER_ADMIN only** `POST /api/v1/admin/users` | **Yes**   | No                  |
| ADMIN      | SUPER_ADMIN only (via role assignment) | No                  | No                  |

Voyage creation (`POST /api/v1/voyages`) already enforces `X-User-Role=ORGANIZER` at the controller level. No change needed there.

---

## What was changed

### Self-registration blocks ORGANIZER/ADMIN

**File:** `auth-service/.../dto/request/RegisterRequest.java`

Added `isRoleSelfRegistrable()` method returning `true` only for `PASSENGER` and `DRIVER`.

**File:** `auth-service/.../service/AuthService.java` — `register()`

Added early check:
```java
if (!request.isRoleSelfRegistrable()) {
    throw new AuthException(
        "Self-registration is only allowed for PASSENGER and DRIVER roles. " +
        "ORGANIZER accounts must be created by a platform administrator.");
}
```

Any API client posting `{"role": "ORGANIZER"}` to `/api/v1/auth/register` now receives a clear 400 error.

### New admin-only account creation method

**File:** `auth-service/.../service/AuthService.java` — `createUserAsAdmin()`

Accepts any role, does not return tokens, emits the same `user.registered` Kafka event so downstream profile creation works identically.

### New internal auth endpoint

**File:** `auth-service/.../controller/AuthController.java`

`POST /api/v1/auth/internal/admin/create-user` — accepts email, password, phoneNumber, role. Defaults role to `ORGANIZER` if omitted. Returns the new user's ID plus a warning if a password was auto-generated.

### Internal auth filter for auth-service

**File (new):** `auth-service/.../security/InternalAuthFilter.java`

Same pattern as the 6 other services with internal endpoints — rejects requests to `/internal/*` without the shared `X-Internal-Secret` header.

### Public admin endpoint for user creation

**File (new):** `admin-service/.../controller/AdminUserCreationController.java`

`POST /api/v1/admin/users` — proxies to auth-service with the internal secret attached. If no password is provided in the body, generates a 12-character secure password (upper + lower + digit + symbol, with I/O/0/1 excluded for readability) and returns it in the response with a "show once" warning.

Example request body:
```json
{
  "email": "organizer@tunisietour.tn",
  "phoneNumber": "+21698765432",
  "role": "ORGANIZER"
}
```

Response:
```json
{
  "userId": "usr_abc123",
  "email": "organizer@tunisietour.tn",
  "role": "ORGANIZER",
  "generatedPassword": "K3m$Rp8n2A#q",
  "warning": "This password is shown ONCE. Share it with the user securely.",
  "createdBy": "admin_7f3c"
}
```

### SUPER_ADMIN guard

**File:** `admin-service/.../security/AdminRoleGuard.java`

New rule: `POST /api/v1/admin/users` requires `X-Admin-Role: SUPER_ADMIN`. Lower admin roles (OPERATIONAL_ADMIN, MODERATOR, etc.) receive 403. GET/PATCH/DELETE on user sub-paths remain accessible to the roles allowed in V16.

### Config wiring

- `admin-service/WebClientConfig` → `authServiceWebClient` bean added
- `admin-service/application.yml` → `auth.service.url` configured
- `docker-compose.yml` → `AUTH_SERVICE_URL` env var added to admin-service

### Restored 2 DTOs from V15

V16 was missing these two DTOs, causing a compile error in user-service:
- `SuspendUserRequest.java`
- `VerifyUserRequest.java`

Restored from V15. They are referenced by `InternalAdminProxyController.java`.

---

## End-to-end flow — creating an organizer

1. A SUPER_ADMIN logs in and obtains a JWT with `adminRole=SUPER_ADMIN`.
2. They call `POST /api/v1/admin/users` with the organizer's email and optional phone number.
3. AdminRoleGuard validates `X-Admin-Role=SUPER_ADMIN` (otherwise 403).
4. AdminUserCreationController generates a secure password if none provided, then forwards to auth-service via WebClient with `X-Internal-Secret`.
5. auth-service's InternalAuthFilter validates the secret, then AuthController calls `AuthService.createUserAsAdmin(...)`.
6. AuthService persists the user, emits `user.registered` Kafka event, and returns the user ID.
7. user-service receives the Kafka event and creates the corresponding profile row.
8. The admin receives the generated password and forwards it to the organizer through a secure channel.
9. The organizer logs in via `POST /api/v1/auth/login` with the provided credentials.
10. From then on, the organizer can publish voyages via `POST /api/v1/voyages`.

---

## What was NOT changed

- All 16 V16 fixes remain intact.
- Driver publishing trajets (WF-01) — unchanged.
- Passenger reservation cycle (WF-02) — unchanged.
- Stripe payment flow (WF-03) — unchanged.
- The 6-role admin hierarchy (SUPER_ADMIN, OPERATIONAL_ADMIN, FINANCIAL_ADMIN, MODERATOR, REPORTER, AUDITEUR) — unchanged.

---

## Files modified / added

```
auth-service/
  ├── dto/request/RegisterRequest.java                      [modified]
  ├── service/AuthService.java                              [modified]
  ├── controller/AuthController.java                        [modified]
  └── security/InternalAuthFilter.java                      [NEW]

admin-service/
  ├── config/WebClientConfig.java                           [modified]
  ├── security/AdminRoleGuard.java                          [modified]
  └── controller/AdminUserCreationController.java           [NEW]

user-service/
  ├── dto/request/SuspendUserRequest.java                   [restored from V15]
  └── dto/request/VerifyUserRequest.java                    [restored from V15]

infra/
  ├── admin-service/src/main/resources/application.yml      [modified]
  └── docker-compose.yml                                    [modified]
```

Total: 4 new files, 6 modified files, 2 restored files = **12 touched files**.
