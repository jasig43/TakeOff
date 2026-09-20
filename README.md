# TakeOFF: Driver Onboarding Platform

TakeOFF is a driver onboarding platform for courier and logistics companies. Applicants register, verify their phone with a one-time code, and sign in; logistics administrators sign in to a separate, role-protected portal.

> **Status: MVP Phase 1.** Landing page, registration, OTP verification, JWT authentication and role-based access control. Later onboarding steps (personal details, licence, vehicle, documents, review) are planned; see [Planned next phases](#22-planned-next-phases).

## 1. Project overview

| | |
|---|---|
| **Frontend** | React + Vite + TypeScript + Tailwind CSS (`takeoff-frontend/`) |
| **Backend** | Java 17, Spring Boot 4, Maven (`takeoff-backend/`) |
| **Data** | MySQL (Flyway-managed schema) |
| **Messaging** | RabbitMQ (OTP generation workflow) |
| **Local infra** | Docker Compose (MySQL + RabbitMQ) |

## 2. MVP Phase 1 features

- Animated, responsive landing page with dark and light glassmorphism themes.
- Applicant registration with a strict password policy enforced on **both** client and server.
- OTP workflow over RabbitMQ: register, event published, listener issues a 6-digit code, verify.
- Six-digit OTP input (auto-advance, paste, countdown ring, resend) with confetti on success.
- Fixed OTP for the evaluator test phone `+15550199` (development only).
- Stateless JWT authentication with `ROLE_APPLICANT_DRIVER` / `ROLE_LOGISTICS_ADMIN` authorities.
- RBAC enforced by the backend; the frontend adds route guards as a convenience only.
- Flyway migrations, consistent JSON error responses, accessibility and reduced-motion support.
- Mock "Sign in with Google / Apple" buttons, clearly labelled **Coming soon**. They are not OAuth integrations.

## 3. Architecture overview

```
 Browser (React SPA :5173)
      │  REST + Bearer JWT
      ▼
 Spring Boot API (:8080) ──► MySQL   (users, otp_tokens; Flyway)
      │  ▲
      │  │ @RabbitListener
      ▼  │
  RabbitMQ  takeoff.exchange ──(otp.routing.key)──► otp.queue
```

**Registration → verification flow**

1. `POST /api/v1/auth/register` validates input, rejects duplicates (409), stores a BCrypt hash, and creates the user as `APPLICANT_DRIVER` with `phoneVerified=false`.
2. After the DB transaction **commits**, the API publishes an `OTP_GENERATE` event (`eventType`, `userId`, `phoneNumber`, `requestedAt`). No password, token or code is ever in the message.
3. `OtpConsumerListener` consumes the event, invalidates earlier codes for the user, generates a CSPRNG 6-digit code (or `123456` for the test phone in dev/test), stores it with an expiry, and logs it in dev/test only.
4. `POST /api/v1/auth/verify-otp` checks the code (expired / used / wrong are distinct errors), marks the phone verified and returns a signed JWT.
5. The SPA stores the session and routes to the dashboard for the user's role.

If RabbitMQ is down at registration, the account is still created and the response says `otpDispatched: false`; the UI offers **Resend code** (`POST /api/v1/auth/resend-otp`). This is the documented recovery path.

## 4. Technology stack

**Frontend:** React 19, Vite 8, TypeScript, Tailwind CSS 4, React Router 7, Axios, Framer Motion, Lucide React, canvas-confetti, Vitest + Testing Library, oxlint.
**Backend:** Java 17, Spring Boot 4.0 (Web MVC, Security, Validation, Data JPA, AMQP), Flyway, MySQL Connector/J, Spring Security's Nimbus JOSE for JWT, JUnit 5, Mockito, H2 (tests only).

## 5. Repository structure

```
TakeOff/
├── takeoff-frontend/      React SPA
├── takeoff-backend/       Spring Boot API
├── documentation/         Project plan and flow-state diagram (PDF)
├── docker-compose.yml     MySQL + RabbitMQ for local development
├── .env.example           Variables for docker-compose
├── .gitignore
└── README.md
```

See [`takeoff-backend/README.md`](takeoff-backend/README.md) and [`takeoff-frontend/README.md`](takeoff-frontend/README.md) for the internals of each app.

## 6. Prerequisites

- **Java 17** (JDK). The Maven Wrapper downloads Maven itself.
- **Node.js** (developed and verified on Node 24; Vite 8 requires a current LTS, 20.19+ or 22.12+) and npm.
- **Docker Desktop** (or your own MySQL 8 + RabbitMQ 3.13/4.x).

## 7. Environment configuration

Nothing secret is committed. Copy the templates and adjust:

| File | Purpose |
|---|---|
| [`.env.example`](.env.example) | docker-compose variables (DB/RabbitMQ credentials, host ports) |
| [`takeoff-backend/.env.example`](takeoff-backend/.env.example) | every backend environment variable, with notes |
| [`takeoff-frontend/.env.example`](takeoff-frontend/.env.example) | `VITE_API_BASE_URL` and `VITE_SHOW_DEV_HINTS` |

Spring Boot does not read `.env` files itself; export the variables in your shell or IDE run configuration. The `dev` profile ships throw-away local defaults that match `docker-compose.yml`, so **no exports are needed for local development**. The `prod` profile requires everything explicitly (notably `JWT_SECRET` and `OTP_PEPPER`).

## 8. MySQL setup

`docker compose up -d` creates database `takeoff` and user `takeoff`. The schema is created by Flyway on backend start (`V1__init_security_and_driver_schema.sql`); Hibernate only validates it (`ddl-auto: validate`). To use your own MySQL 8: create an empty `takeoff` database and user, then set `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`.

## 9. RabbitMQ setup

The backend declares `takeoff.exchange` (direct), `otp.queue` (durable) and the `otp.routing.key` binding on first connect. Names are configurable via `OTP_EXCHANGE`, `OTP_QUEUE`, `OTP_ROUTING_KEY`. Management UI: http://localhost:15672 (dev credentials in `docker-compose.yml`).

## 10. Start local infrastructure

```bash
docker compose up -d
docker compose ps        # both services should report "healthy"
```

| Service | Host port | Notes |
|---|---|---|
| MySQL 8.4 | **3307** → 3306 | 3307 (not 3306) avoids clashing with a locally installed MySQL |
| RabbitMQ AMQP | 5672 | |
| RabbitMQ management UI | 15672 | |

Ports are bound to `127.0.0.1` only. Stop with `docker compose down` (add `-v` to delete data).

## 11. Run the backend

```bash
cd takeoff-backend
# macOS / Linux
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
cd takeoff-backend
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

The API listens on http://localhost:8080. Running **without** a profile is intentionally not a dev environment: the app refuses to start until `JWT_SECRET` is set, and says so before it touches the database.

**IntelliJ IDEA:** open the run configuration for `TakeoffBackendApplication` → *Modify options* → *Active profiles* → enter `dev` (or add the environment variable `SPRING_PROFILES_ACTIVE=dev`). Starting it with no profile is the usual cause of `Unknown database 'takeoff'` (see Troubleshooting).

**Using a local MySQL instead of Docker** (the dev profile expects the Docker MySQL on port 3307): create the database and a user, then set these environment variables in your run configuration.

```sql
CREATE DATABASE takeoff CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'takeoff'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON takeoff.* TO 'takeoff'@'localhost';
```

```
MYSQL_URL=jdbc:mysql://localhost:3306/takeoff?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
MYSQL_USER=takeoff
MYSQL_PASSWORD=choose-a-password
```

RabbitMQ is still required for the OTP step. Without it, registration succeeds but reports `otpDispatched: false`, and Resend returns 503.

## 12. Run the frontend

```bash
cd takeoff-frontend
npm install
cp .env.example .env.local   # optional; defaults already point at http://localhost:8080/api/v1
npm run dev                  # http://localhost:5173
```

## 13. API endpoint summary

Base path `/api/v1`. All errors share one JSON shape:
`{ timestamp, status, error, code, message, path, fieldErrors: [{ field, message }] }`.

| Method & path | Auth | Purpose | Success | Notable errors |
|---|---|---|---|---|
| `POST /auth/register` | public | Create applicant, publish `OTP_GENERATE` | 201 | 400 validation, 409 duplicate email/phone |
| `POST /auth/verify-otp` | public | Verify code, get JWT | 200 | 400 `OTP_INVALID` / `OTP_EXPIRED` / `OTP_ALREADY_USED` / `OTP_TOO_MANY_ATTEMPTS` |
| `POST /auth/resend-otp` | public | Request a new code | 200 | 429 `OTP_RESEND_TOO_SOON`, 503 broker down |
| `POST /auth/login` | public | Email + password, get JWT | 200 | 401 `INVALID_CREDENTIALS`, 403 `PHONE_NOT_VERIFIED` |
| `GET /drivers/profile` | `ROLE_APPLICANT_DRIVER` | Own profile | 200 | 401, 403 |
| `GET /admin/health` | `ROLE_LOGISTICS_ADMIN` | RBAC demonstration | 200 | 401, 403 |

## 14. Authentication and OTP flow

- JWT: HS256, claims `sub` (user id), `email`, `authorities` (e.g. `["ROLE_APPLICANT_DRIVER"]`), `iat`, `exp`, `iss`. Lifetime `JWT_EXPIRATION_MINUTES` (default 60).
- The auth filter also confirms the user still exists, is enabled, and matches the token subject, so disabling an account takes effect immediately.
- OTPs: 6 digits, valid `OTP_EXPIRATION_SECONDS` (default 300), max `OTP_MAX_ATTEMPTS` wrong guesses (default 5), only the newest code works, resend cooldown 30 s. Outside dev/test they are stored as HMAC-SHA256 hashes (`OTP_PEPPER`).
- Applicants cannot log in until their phone is verified.

## 15. Password rules

Enforced by `@CompliantPassword` on the backend (authoritative) and mirrored live in the UI:

- at least **15** characters
- at least one uppercase letter (A–Z)
- at least one special character from `!@#$%^&*()_+-=[]{}|;:,.<>?`
- (technical limit) at most 72 bytes, the BCrypt input limit, rejected explicitly rather than truncated

## 16. RBAC

| Path | Requirement |
|---|---|
| `/api/v1/auth/**` | public |
| `/api/v1/drivers/**` | `ROLE_APPLICANT_DRIVER` |
| `/api/v1/admin/**` | `ROLE_LOGISTICS_ADMIN` |
| everything else | authenticated |

Public registration always yields `APPLICANT_DRIVER`; a `role` field in the request body is ignored. Admins are created only through the config-driven seeder (`ADMIN_SEED_*`). Missing/invalid tokens get a JSON `401`, insufficient roles a JSON `403`. The React route guards (`ProtectedRoute`) are UX only.

## 17. Test account and OTP instructions

> **Development and evaluator testing only. The fixed OTP is disabled outside the `dev`/`test` profiles and the backend refuses to start if it is enabled elsewhere. Never enable it in production.**

| | |
|---|---|
| Test phone | `+15550199` |
| Development OTP | `123456` |

1. Start infra, backend (`dev` profile) and frontend.
2. Register at http://localhost:5173/register using phone **`+15550199`** (any email; password must follow the rules above).
3. On the OTP screen enter **`123456`**.
4. For any other phone number, the generated code is printed in the backend console: `[DEV ONLY] Verification code for user …`.

**Dev admin (seeded only under the `dev` profile):** `admin@takeoff.local` / `Dev-Admin-Password#2026`, signed in at http://localhost:5173/admin/login. This is a throw-away local credential; production admins must be seeded from your own secrets.

## 18. Build and test commands

```bash
# Frontend
cd takeoff-frontend
npm install
npm run build          # type-check + production build
npm run lint
npm test -- --run

# Backend (no MySQL/RabbitMQ needed: tests use H2 and a mocked RabbitTemplate)
cd takeoff-backend
./mvnw test            # Windows: .\mvnw.cmd test
./mvnw clean package   # Windows: .\mvnw.cmd clean package
```

## 19. Troubleshooting

| Symptom | Fix |
|---|---|
| `JWT_SECRET is not configured` on start | Run with `SPRING_PROFILES_ACTIVE=dev`, or set `JWT_SECRET` to 32+ random characters. |
| `Unknown database 'takeoff'` (log says `No active profile set`) | You started without the `dev` profile, so the base config (`localhost:3306`) was used. Set the `dev` profile, and make sure the database exists: `docker compose up -d`, or create it yourself as shown under *Run the backend*. |
| `Access denied` / connection refused to MySQL | Is `docker compose ps` healthy? Dev profile expects MySQL on **3307**. A locally installed MySQL owns 3306. |
| Registration works but no code arrives | RabbitMQ unreachable: the API returns `otpDispatched:false`; fix RabbitMQ and press **Resend code**. In dev, read the code in the backend log. |
| Browser shows CORS errors | `FRONTEND_ORIGIN` must exactly match the SPA origin (scheme + host + port). |
| `Unsupported class file major version` / wrong Java | Use JDK 17 (`java -version`). |
| Port 5173/8080 already in use | Stop the other process; the Vite config uses a strict port so it never silently switches. |

## 20. Security notes

- Passwords: BCrypt; never logged, stored or returned. Login gives one generic message for unknown email vs wrong password and does equal work for both.
- No secrets in the repo. The base config has **no** default DB/RabbitMQ password, JWT secret or OTP pepper; `dev` adds labelled throw-away defaults. The built-in dev JWT secret is rejected outside dev/test.
- JWT secret must be ≥ 32 bytes or startup fails.
- CORS allows only the configured origin; no cookies, so no CSRF surface.
- OTP: CSPRNG, expiry, attempt limit, single active code, constant-time comparison, hashed at rest outside dev/test.
- Error responses never include stack traces or framework messages.

## 21. Known MVP limitations

- **No SMS gateway.** The code is only logged to the console in dev/test. In production nothing delivers it yet; wire an SMS provider into `OtpConsumerListener` before real use.
- A 6-digit OTP is inherently low-entropy; expiry and the attempt limit are the real protection (see `OtpCodec`).
- The JWT is kept in `localStorage` (XSS trade-off; no refresh token). Planned: short-lived access token + httpOnly refresh cookie.
- No dead-letter queue: a message that fails processing is dropped and the user can resend.
- No general rate limiting on login/registration beyond the OTP controls.
- Google/Apple buttons are UI mocks; no OAuth exists.
- Not exercised in the build environment: real MySQL and RabbitMQ (no Docker available there). Backend tests run on H2 (MySQL mode) with the real Flyway migration and a mocked `RabbitTemplate`. See the backend README.

## 22. Planned next phases

Personal details → identity and licence → vehicle details → document uploads → summary and submit (`POST /drivers/submit`, status `PENDING_REVIEW`) → admin review queue with approve/reject and RabbitMQ notifications. See `documentation/TakeOFF_Flow_State_Diagram.pdf`.
