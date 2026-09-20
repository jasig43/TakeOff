# TakeOFF: Driver Onboarding Platform

TakeOFF is a driver onboarding platform for courier and logistics companies. Applicants register, verify their phone with a one-time code, complete a step-by-step application (personal details, identity and licence, vehicle, documents) and submit it for review; logistics administrators sign in to a role-protected portal where they review submissions and approve or reject them. Drivers are notified of the decision.

> **Status: MVP Phase 2.** Everything in Phase 1 (sign-in landing page, registration, OTP verification, JWT authentication, RBAC) plus the full onboarding workflow: driver application wizard, document uploads, submission tracking, the administrator review portal and driver notifications. See [Application workflow](#application-workflow) and [Planned next phases](#22-planned-next-phases).

## 1. Project overview

| | |
|---|---|
| **Frontend** | React + Vite + TypeScript + Tailwind CSS (`takeoff-frontend/`) |
| **Backend** | Java 17, Spring Boot 4, Maven (`takeoff-backend/`) |
| **Data** | MySQL (Flyway-managed schema) |
| **Messaging** | RabbitMQ (OTP generation and application-decision events) |
| **Files** | Uploaded documents stored on local disk, referenced from MySQL |
| **Local infra** | Docker Compose (MySQL + RabbitMQ), or a locally installed MySQL and RabbitMQ |

## 2. Features

### Phase 2: onboarding workflow

- **Role-aligned portals.** After sign-in a driver only ever sees the driver dashboard and driver pages (My application, Notifications); an administrator only sees the admin dashboard and review pages (Applications). The sidebar lists just the current role's destinations, the header bell shows just that role's notifications, the routes are guarded in the SPA, and the backend enforces the same split (`/drivers/**` vs `/admin/**`), so a driver calling an admin endpoint gets `403`.
- **Driver profile and KYC:** date of birth (18+), address, emergency contact, national ID, driver's licence number, class and expiry (must not be expired).
- **Vehicle registration:** type, registration (plate) number, make and model. National ID and plate are unique across drivers.
- **Document management:** upload, replace, view and remove the driver's licence, vehicle registration and insurance certificate (PDF, JPG or PNG, up to 5 MB). The file's real type is checked from its bytes, not its name.
- **Application submission and tracking:** a review step summarises everything, the driver confirms and submits, the API stores the application in MySQL as `PENDING_REVIEW`, and the completion screen shows the **Reference ID** and status. The dashboard tracks progress and status at any time.
- **Administrator portal:** email/password sign-in (JWT, `LOGISTICS_ADMIN`), a dashboard with counts and the review queue, a filterable and searchable application list, and a detail page that shows every field and opens each uploaded document.
- **Admin settings and account management:** the admin sidebar has a Settings page with two tabs. *Account and security* shows the account details and lets the administrator change their own password. *Users and roles* lists every account (searchable, paged), lets an administrator **create an account** for someone with a **temporary password**, **assign a role** (driver or administrator) and **issue a new temporary password**. See [Account management](#account-management-and-temporary-passwords).
- **Approve / reject:** `PENDING_REVIEW` to `APPROVED` or `REJECTED`, persisted through the admin API (`PATCH /admin/applications/{id}/status`). Rejection requires a note; the driver can then correct the application and resubmit.
- **Driver notifications:** a bell in the fixed frosted-glass header shows the unread count and opens a panel with the latest notifications (mark one or all as read, or open the full inbox page); the dashboard and sidebar carry no notifications. An administrator's bell shows how many applications are waiting for review and links to that queue. A notification is created when an application is submitted and when it is approved or rejected. Each decision is also published to RabbitMQ (`notification.queue`), whose consumer texts the driver the outcome through the configured SMS provider.

### Phase 1: authentication

- Clean landing page that is just the sign-in form (with a "Sign up" link), in glassmorphism styling that follows your system's light/dark setting (no toggle). One form serves drivers and administrators; after login each lands on their own dashboard, with a left sidebar for navigation (a slide-in drawer on phones).
- Applicant registration with a strict password policy enforced on **both** client and server.
- OTP workflow over RabbitMQ: register, event published, listener issues a 6-digit code, verify.
- Six-digit OTP input (auto-advance, paste, countdown ring, resend) with confetti on success.
- Real SMS delivery of the one-time code to any phone number through Twilio (opt-in; see [SMS delivery](#sms-delivery)), with a per-number hourly send cap.
- Fixed OTP for the evaluator test phone `+15550199` (development only; nothing about it is shown in the UI).
- Stateless JWT authentication with `ROLE_APPLICANT_DRIVER` / `ROLE_LOGISTICS_ADMIN` authorities.
- RBAC enforced by the backend; the frontend adds route guards as a convenience only.
- Flyway migrations, consistent JSON error responses, accessibility and reduced-motion support.

## 3. Architecture overview

```
 Browser (React SPA :5173)
      │  REST + Bearer JWT
      ▼
 Spring Boot API (:8080) ──► MySQL   (users, otp_tokens, driver_applications,
      │  ▲   │                        application_documents, notifications; Flyway)
      │  │   └──► ./data/uploads      (document files, never served directly)
      │  │ @RabbitListener
      ▼  │
  RabbitMQ  takeoff.exchange ──(otp.routing.key)──────────► otp.queue
                             └─(notification.routing.key)─► notification.queue
```

**Registration → verification flow**

1. `POST /api/v1/auth/register` validates input, rejects duplicates (409), stores a BCrypt hash, and creates the user as `APPLICANT_DRIVER` with `phoneVerified=false`.
2. After the DB transaction **commits**, the API publishes an `OTP_GENERATE` event (`eventType`, `userId`, `phoneNumber`, `requestedAt`). No password, token or code is ever in the message.
3. `OtpConsumerListener` consumes the event, invalidates earlier codes for the user, generates a CSPRNG 6-digit code (or `123456` for the test phone in dev/test), stores it with an expiry, and logs it in dev/test only.
4. `POST /api/v1/auth/verify-otp` checks the code (expired / used / wrong are distinct errors), marks the phone verified and returns a signed JWT.
5. The SPA stores the session and routes to the dashboard for the user's role.

If RabbitMQ is down at registration, the account is still created and the response says `otpDispatched: false`; the UI offers **Resend code** (`POST /api/v1/auth/resend-otp`). This is the documented recovery path.

### Application workflow

```
DRAFT ──submit──► PENDING_REVIEW ──approve──► APPROVED
  ▲                    │
  │                    └──reject (note required)──► REJECTED ──driver edits──► DRAFT
  └───────────────────────────────────────────────────────────────────────────┘
```

1. A driver's application is created automatically the first time they open **My application** (`GET /drivers/application`). Each step is saved on its own (`PUT .../personal`, `.../identity`, `.../vehicle`), so progress survives leaving the page.
2. Documents are uploaded one at a time as `multipart/form-data` (`POST /drivers/application/documents/{type}`); replacing a document overwrites the old file.
3. `POST /drivers/application/submit` is refused with `400 APPLICATION_INCOMPLETE` unless all three sections and all three documents are present. On success the application gets a Reference ID (`TKO-YYYYMMDD-XXXXXX`), status `PENDING_REVIEW`, a "submitted" notification, and is locked for editing (`409 APPLICATION_LOCKED` on any further change).
4. An administrator opens it from the queue and approves or rejects it. Only `PENDING_REVIEW` applications can be decided (`409 INVALID_STATUS_TRANSITION` otherwise), and two admins acting at once cannot both win (optimistic locking, `409 CONCURRENT_MODIFICATION`).
5. The decision, the driver's notification and the status change are written in **one database transaction**. After it commits, an `APPLICATION_DECIDED` event is published to RabbitMQ on a best-effort basis; a broker outage never undoes or blocks a decision.
6. Editing a rejected application (saving any step or changing a document) reopens it as a `DRAFT`. Submitting a rejected application without changing anything is refused (`409 APPLICATION_UNCHANGED`). On resubmission the earlier decision is cleared and the same Reference ID is kept.

Uploaded documents are private: they are stored under generated names (never the driver's file name), and the only way to read one is an authenticated request, either by the owning driver or by an administrator. The SPA fetches them with the bearer token and opens them as a blob, so a document URL cannot be shared or guessed.

## 4. Technology stack

**Frontend:** React 19, Vite 8, TypeScript, Tailwind CSS 4, React Router 7, Axios, Framer Motion, Lucide React, canvas-confetti, Vitest + Testing Library, oxlint.
**Backend:** Java 17, Spring Boot 4.0 (Web MVC, Security, Validation, Data JPA, AMQP), Flyway, MySQL Connector/J, Spring Security's Nimbus JOSE for JWT, JUnit 5, Mockito, H2 (tests only).

## 5. Repository structure

```
TakeOff/
├── takeoff-frontend/      React SPA
├── takeoff-backend/       Spring Boot API
├── documentation/         Project plan and flow-state diagram (PDF), plus fictional TEST_* driver documents
├── DEPLOYMENT.md          Free hosted demo: Vercel + Render (Postgres, Redis), variables and steps
├── docker-compose.yml     MySQL + RabbitMQ for local development
├── .env.example           Variables for docker-compose
├── .gitignore
└── README.md
```

See [`takeoff-backend/README.md`](takeoff-backend/README.md) and [`takeoff-frontend/README.md`](takeoff-frontend/README.md) for the internals of each app.

## 6. Prerequisites

- **Java 17** (JDK). The Maven Wrapper downloads Maven itself.
- **Node.js** (developed and verified on Node 24; Vite 8 requires a current LTS, 20.19+ or 22.12+) and npm.
- **Docker Desktop** (or your own MySQL 8 + RabbitMQ 3.13/4.x, see [RabbitMQ setup](#9-rabbitmq-setup) for installing it natively on Windows).

## 7. Environment configuration

Nothing secret is committed. Copy the templates and adjust:

| File | Purpose |
|---|---|
| [`.env.example`](.env.example) | docker-compose variables (DB/RabbitMQ credentials, host ports) |
| [`takeoff-backend/.env.example`](takeoff-backend/.env.example) | every backend environment variable, with notes |
| [`takeoff-frontend/.env.example`](takeoff-frontend/.env.example) | `VITE_API_BASE_URL` |

Spring Boot does not read `.env` files itself; export the variables in your shell or IDE run configuration. The `dev` profile ships throw-away local defaults that match `docker-compose.yml`, so **no exports are needed for local development**. The `prod` profile requires everything explicitly (notably `JWT_SECRET` and `OTP_PEPPER`).

## 8. MySQL setup

`docker compose up -d` creates database `takeoff` and user `takeoff`. The schema is created by Flyway on backend start (`V1__init_security_and_driver_schema.sql` for accounts and OTPs, `V2__driver_applications.sql` for applications, documents and notifications); Hibernate only validates it (`ddl-auto: validate`). To use your own MySQL 8: create an empty `takeoff` database and user, then set `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`.

## 9. RabbitMQ setup

The backend declares `takeoff.exchange` (direct) and two durable queues with their bindings on first connect: `otp.queue` (`otp.routing.key`, OTP generation) and `notification.queue` (`notification.routing.key`, application decisions). Names are configurable via `OTP_EXCHANGE`, `OTP_QUEUE`, `OTP_ROUTING_KEY`, `NOTIFICATION_QUEUE`, `NOTIFICATION_ROUTING_KEY`. Management UI (Docker image only): http://localhost:15672 (dev credentials in `docker-compose.yml`).

**Installing RabbitMQ natively on Windows (instead of Docker).** RabbitMQ needs Erlang/OTP first. Install [Erlang/OTP](https://www.erlang.org/downloads) and then the [RabbitMQ server installer](https://www.rabbitmq.com/docs/install-windows), both from an administrator prompt (they register a Windows service). Then make sure the service is running:

```powershell
# from an ADMINISTRATOR PowerShell
Start-Service RabbitMQ
Get-Service RabbitMQ                 # Status should be Running; AMQP listens on port 5672
```

A native install ships the built-in `guest` / `guest` account, which the broker only accepts from the same machine. The backend's default RabbitMQ user is `takeoff`, so point it at `guest` in your git-ignored `takeoff-backend/config/application.properties` (never in a committed file):

```properties
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

For anything other than your own laptop, create a dedicated user and set `RABBITMQ_USER` / `RABBITMQ_PASSWORD` instead.

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
| `GET /drivers/application` | driver | Own application (created empty on first call) | 200 | 401, 403 |
| `PUT /drivers/application/personal` | driver | Save personal details | 200 | 400 field errors, 409 `APPLICATION_LOCKED` |
| `PUT /drivers/application/identity` | driver | Save national ID and licence | 200 | 400 field errors, 409 `NATIONAL_ID_IN_USE`, 409 `APPLICATION_LOCKED` |
| `PUT /drivers/application/vehicle` | driver | Save vehicle | 200 | 400 field errors, 409 `PLATE_IN_USE`, 409 `APPLICATION_LOCKED` |
| `POST /drivers/application/documents/{type}` | driver | Upload or replace a document (`multipart`, field `file`; type is `DRIVERS_LICENCE`, `VEHICLE_REGISTRATION` or `INSURANCE`) | 200 | 400 `FILE_REQUIRED`, 413 `FILE_TOO_LARGE`, 415 `UNSUPPORTED_FILE_TYPE`, 409 locked |
| `GET /drivers/application/documents/{type}` | driver | Download own document | 200 | 404 |
| `DELETE /drivers/application/documents/{type}` | driver | Remove a document | 200 | 409 locked |
| `POST /drivers/application/submit` | driver | Submit for review, get Reference ID | 200 | 400 `APPLICATION_INCOMPLETE`, 409 locked / unchanged |
| `GET /drivers/notifications` | driver | Own notifications and unread count | 200 | 401, 403 |
| `POST /drivers/notifications/{id}/read` | driver | Mark one read | 200 | 404 |
| `POST /drivers/notifications/read-all` | driver | Mark all read | 200 | |
| `GET /admin/health` | `ROLE_LOGISTICS_ADMIN` | RBAC demonstration | 200 | 401, 403 |
| `GET /admin/summary` | admin | Counts by status | 200 | 401, 403 |
| `GET /admin/applications?status=&q=&page=&size=` | admin | Submitted applications, filterable and searchable (drafts are never listed) | 200 | 400 `INVALID_STATUS_FILTER` |
| `GET /admin/applications/{id}` | admin | Full application plus the driver's details | 200 | 404 |
| `GET /admin/applications/{id}/documents/{type}` | admin | Open an uploaded document | 200 | 404 |
| `PUT /account/password` | any signed-in user, including one on a temporary password | Change own password (`{ "currentPassword", "newPassword" }`); returns the refreshed account | 200 | 400 field errors (`currentPassword` wrong, `newPassword` weak or unchanged), 401 |
| `GET /admin/users?q=&page=&size=` | admin | Search and page through all accounts | 200 | 401, 403 |
| `POST /admin/users` | admin | Create an account (`{ fullName, email, phoneNumber, role }`); returns the generated temporary password **once** | 201 | 400 field errors, 409 `EMAIL_ALREADY_REGISTERED` / `PHONE_ALREADY_REGISTERED` |
| `PATCH /admin/users/{id}/role` | admin | Assign a role (`{ "role": "APPLICANT_DRIVER" \| "LOGISTICS_ADMIN" }`); effective immediately | 200 | 400, 404, 409 `CANNOT_CHANGE_OWN_ROLE` |
| `POST /admin/users/{id}/temporary-password` | admin | Issue a new temporary password (returned once) | 200 | 404, 409 `CANNOT_RESET_OWN_PASSWORD` |
| `GET /health` | public | Liveness probe for the host | 200 | |
| `PATCH /admin/applications/{id}/status` | admin | Approve or reject (`{ "status": "APPROVED" \| "REJECTED", "note": "..." }`; note required for reject) | 200 | 400, 409 `INVALID_STATUS_TRANSITION` / `CONCURRENT_MODIFICATION` |

## 14. Authentication and OTP flow

- JWT: HS256, claims `sub` (user id), `email`, `authorities` (e.g. `["ROLE_APPLICANT_DRIVER"]`), `iat`, `exp`, `iss`. Lifetime `JWT_EXPIRATION_MINUTES` (default 60).
- The auth filter also confirms the user still exists, is enabled, and matches the token subject, so disabling an account takes effect immediately.
- OTPs: 6 digits, valid `OTP_EXPIRATION_SECONDS` (default 300), max `OTP_MAX_ATTEMPTS` wrong guesses (default 5), only the newest code works, resend cooldown 30 s. Outside dev/test they are stored as HMAC-SHA256 hashes (`OTP_PEPPER`).
- Applicants cannot log in until their phone is verified.
- Phone numbers must be E.164 (`+` and the country code). A Zimbabwean number typed with its local leading 0 (`+2630778...`) has the right shape but no SMS can reach it, so sign-up refuses it with the fix (`+263778...`), on both the client and the server.
- At most `OTP_MAX_SENDS_PER_HOUR` codes (default 5) are issued per phone number per hour (`429 OTP_SEND_LIMIT`), because real SMS costs money and can be abused.

### SMS delivery

The code is texted to the number the user registered with, to **any** international E.164 number, through [Twilio](https://www.twilio.com/sms). The provider is chosen with `SMS_PROVIDER`:

| `SMS_PROVIDER` | Behaviour |
|---|---|
| `none` (default) | Nothing is sent. In the `dev` profile the code is printed to the backend console; in any other profile the code is **not delivered** and a warning is logged at startup. |
| `twilio` | The code is sent as an SMS. Requires `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and a sender: `TWILIO_FROM_NUMBER` (a Twilio number) or `TWILIO_MESSAGING_SERVICE_SID` (wins if both are set). The app refuses to start with a clear message if any are missing. |

Set them **outside the repo**: as environment variables, or for local use in `takeoff-backend/config/application.properties` (git-ignored):

```properties
takeoff.sms.provider=twilio
takeoff.sms.twilio.account-sid=AC...
takeoff.sms.twilio.auth-token=...
takeoff.sms.twilio.from-number=+1...
```

How it behaves:

- The SMS is sent by the RabbitMQ listener **after** the code is stored and its database transaction commits, so a slow provider never holds a transaction open. Timeouts are 5 s to connect and 10 s to read.
- If Twilio rejects the message or is unreachable, the failure is logged (HTTP status and Twilio error code only, never the number, the code or credentials). The stored code stays valid and the user can press **Resend code**. The UI cannot tell the SMS failed, because sending is asynchronous.
- The fixed test-phone code is never texted.
- Twilio account requirements are Twilio's, not this app's: a *trial* account can only text numbers you have verified in the Twilio console, and each destination country must be enabled under *Messaging → Geo permissions*. Sending to arbitrary numbers needs a paid account, and carriers in some countries require a registered sender ID.
- Adding another provider means one class implementing `SmsSender` plus one case in `SmsConfig`.

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
| `GET /api/v1/health` | public |
| `PUT /api/v1/account/password` | any authenticated user (the only thing someone on a temporary password may do) |
| `/api/v1/drivers/**` (profile, application, documents, notifications) | `ROLE_APPLICANT_DRIVER` |
| `/api/v1/admin/**` (summary, applications, documents, decisions, users) | `ROLE_LOGISTICS_ADMIN` |
| everything else | a real role (driver or administrator) |

Public registration always yields `APPLICANT_DRIVER`; a `role` field in the request body is ignored. Administrators come from the config-driven seeder (`ADMIN_SEED_*`) or are created by another administrator in Settings. Missing/invalid tokens get a JSON `401`, insufficient roles a JSON `403`. The role is read from the database on every request, so a role change takes effect immediately, even for a token issued earlier. The React route guards (`ProtectedRoute`) are UX only.

### Account management and temporary passwords

An administrator opens **Settings > Users and roles** and can:

1. **Create an account.** They enter the person's name, email, phone number and role. There is no password field: the server generates a strong temporary password (18 characters, CSPRNG, no easily misread characters, always meeting the sign-up rules) and shows it **once**, with a Copy button. It is stored only as a BCrypt hash and never appears in a list, a log line or a later response. The administrator gives it to the person privately.
2. **Assign a role.** Choose Driver or Administrator, confirm, and it takes effect straight away (a driver promoted to administrator can use the admin area with the same token on their next request, and a demoted administrator loses it). An administrator cannot change their own role, so the last administrator can never be removed.
3. **Issue a new temporary password** for someone who lost theirs. The old password stops working immediately.

What the new person experiences:

1. They sign in at the normal sign-in page with their email and the temporary password.
2. They are sent to **Choose your own password** and can do nothing else: while the temporary password is in force the server gives them no role at all, so every driver and admin route answers `403 PASSWORD_CHANGE_REQUIRED`, and the only call that works is changing their password.
3. After they choose one (it must meet the sign-up rules and differ from the temporary one) the same session carries on to their dashboard, and the temporary password no longer works.

A temporary password expires after `takeoff.accounts.temporary-password-hours` (default 72, set with `TAKEOFF_ACCOUNTS_TEMPORARY_PASSWORD_HOURS`). After that, sign-in answers `401 TEMPORARY_PASSWORD_EXPIRED`, an already-open session on it stops working, and an administrator issues a new one.

## 17. Test account and OTP instructions

> **Development and evaluator testing only. The fixed OTP is disabled outside the `dev`/`test` profiles and the backend refuses to start if it is enabled elsewhere. Never enable it in production. The UI does not mention it; this section is the only place it is documented.**

| | |
|---|---|
| Test phone | `+15550199` |
| Development OTP | `123456` |

1. Start infra, backend (`dev` profile) and frontend.
2. Register at http://localhost:5173/register using phone **`+15550199`** (any email; password must follow the rules above).
3. On the OTP screen enter **`123456`**.
4. For any other phone number, the generated code is printed in the backend console: `[DEV ONLY] Verification code for user …`.

**Dev admin (seeded only under the `dev` profile):** `admin@takeoff.local` / `Dev-Admin-Password#2026`, signed in with the same form at http://localhost:5173 (it opens the admin dashboard). This is a throw-away local credential; production admins must be seeded from your own secrets.

**Test documents for the driver flow.** [`documentation/TEST_Driver_Profiles_and_Documents.md`](documentation/TEST_Driver_Profiles_and_Documents.md) lists two fictional test drivers (values to type into each application step) and the matching files to upload: a driver's licence (PNG), a vehicle registration (PDF) and an insurance certificate (PDF) each, named `TEST_01_...` and `TEST_02_...`. Everything in them is invented and stamped "SPECIMEN - TEST DATA ONLY"; no real identity or document details are used, and `SampleDocumentsTest` checks that the upload validator accepts every file.

**Seeded test driver (optional, `dev`/`test` profiles only):** to sign in as a driver without registering and receiving an OTP, enable a ready-made, phone-verified driver account. It is off by default and never has a built-in password; supply one in your own environment or in the git-ignored `takeoff-backend/config/application.properties`:

```properties
takeoff.driver.seed.enabled=true
takeoff.driver.seed.email=driver@example.com
takeoff.driver.seed.password=<a password that meets the rules in section 15>
takeoff.driver.seed.phone-number=+15550101
takeoff.driver.seed.full-name=TakeOFF Driver
```

(or the `DRIVER_SEED_*` environment variables). The account is created once on startup and **never overwrites** an existing account, so changing the password in config later does not change an account that already exists. The seeder refuses to run outside the `dev` and `test` profiles, and refuses a password that breaks the policy. Use a phone number you control if you want the decision SMS to reach a real phone.

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
| Backend log repeats `Connection refused` / `Failed to check/redeclare auto-delete queue(s)` | RabbitMQ is not running. Native install: `Start-Service RabbitMQ` from an administrator PowerShell, then `Get-Service RabbitMQ`. Docker: `docker compose up -d`. |
| `ACCESS_REFUSED - Login was refused` for RabbitMQ | The backend is using the default `takeoff` user. Set `spring.rabbitmq.username` / `password` (native install: `guest` / `guest`) in `takeoff-backend/config/application.properties`, or the `RABBITMQ_*` variables. |
| Uploads fail with `415` / `413` | Only real PDF, JPG or PNG files up to 5 MB are accepted (the content is checked, not the extension). Raise `MAX_UPLOAD_BYTES` and the multipart limits together if you need more. |
| Uploaded files vanish after a clean | They live in `takeoff-backend/data/uploads` (`UPLOAD_DIR`), outside `target/`. Do not delete that folder without also clearing `application_documents`. |
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

- **SMS delivery is implemented but has not been run against real Twilio** (no account or credentials were available while building it). It is unit-tested against a mock HTTP server that checks the exact request Twilio expects (endpoint, Basic auth, form fields) and the failure handling, so expect to verify it once with your own account and a number you control.
- SMS is opt-in: with `SMS_PROVIDER=none` outside `dev`, codes are not delivered at all.
- A failed SMS is only logged; the user is not told (they can press Resend). There is no delivery-receipt tracking or automatic retry.
- Sending real SMS to arbitrary numbers invites cost-abuse (SMS pumping). Only a per-number hourly cap exists; there is no rate limit per IP or CAPTCHA on sign-up, and no allow-list of destination countries. Add those before a public launch.
- A 6-digit OTP is inherently low-entropy; expiry and the attempt limit are the real protection (see `OtpCodec`).
- The JWT is kept in `localStorage` (XSS trade-off; no refresh token). Planned: short-lived access token + httpOnly refresh cookie.
- No dead-letter queue: a message that fails processing is dropped and the user can resend.
- No general rate limiting on login/registration beyond the OTP controls.
- Sign-in is email and password only; there is no social (OAuth/SSO) login.
- Uploaded documents live on the API server's local disk (`UPLOAD_DIR`). That is fine for one server; run more than one, or on ephemeral hosting, and you need shared storage (an S3-compatible store behind `DocumentStorageService`) and file backups. There is **no antivirus scan** of uploads; only the type, size and signature are checked.
- Notifications are an in-app inbox (the header bell refreshes every minute, on navigation and after the user changes something; it is not pushed) plus an SMS of the decision, sent by the `notification.queue` consumer through the same provider as OTPs. With `SMS_PROVIDER=none` there is no SMS, and as with OTPs a failed SMS is only logged. There is no email channel.
- An administrator's decision is final in this release: an approved or rejected application cannot be moved back to `PENDING_REVIEW` by an admin (a rejected driver can reopen it themselves).
- The first administrator still comes from the config-driven seeder; after that, administrators create and manage accounts in Settings. Changing or resetting a password does not sign out sessions that are already open (JWTs are stateless and there is no revocation list), except that an expired temporary password is refused immediately. A wrong current password is not rate-limited beyond BCrypt's cost. The seeder never overwrites an existing account, so the password in the seed config stops mattering once the admin exists.
- An administrator cannot change their own role or reset their own password from the user list (so the last administrator can never be removed), and there is no way to disable or delete an account in the UI yet.
- Accounts created by an administrator are marked phone-verified (the administrator vouches for the person), so they skip the sign-up OTP. The temporary password is handed over by the administrator, not sent by SMS or email.
- Document review is "open the file in a new tab"; there is no in-page viewer, annotation or per-document accept/reject.
- Not exercised in the build environment: real MySQL and RabbitMQ from the test suite (backend tests run on H2 in MySQL mode with the real Flyway migrations and a mocked `RabbitTemplate`). The full workflow was, however, run against a real local MySQL 8; a locally installed RabbitMQ is documented above. See the backend README.

## 22. Planned next phases

Email notifications, delivery tracking and retries for SMS, an in-page document viewer, object storage for uploads, admin-user management, an audit trail of decisions, and a refresh-token flow for the JWT. See `documentation/TakeOFF_Flow_State_Diagram.pdf` for the original flow.
