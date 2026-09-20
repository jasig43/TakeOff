# takeoff-backend

Spring Boot 4 / Java 17 REST API for TakeOFF (MVP Phase 2): registration, RabbitMQ-based OTP, JWT authentication and RBAC, plus the driver application workflow (KYC, vehicle, document uploads, submission), the administrator review portal and driver notifications.

## Run

```bash
docker compose -f ../docker-compose.yml up -d                # MySQL :3307 + RabbitMQ :5672
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run            # Windows: set $env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run
```

API: http://localhost:8080. Configuration is documented in [`.env.example`](.env.example) and `src/main/resources/application*.yml`.

## Profiles

| Profile | Purpose |
|---|---|
| *(none)* | Not a dev environment. Refuses to start without `JWT_SECRET`. |
| `dev` | Local defaults matching `docker-compose.yml`, OTP printed to the console, evaluator test phone enabled, dev admin seeded. |
| `demo` | The free hosted demo (Render): PostgreSQL migrations, a Redis queue instead of RabbitMQ, documents stored in the database, dev-style OTP conveniences (code in the log, test number `+15550199` / `123456`) but **no built-in secrets**. See [`DEPLOYMENT.md`](../DEPLOYMENT.md). |
| `test` | Used by the automated tests (H2, mocked broker). Lives in `src/test/resources`. |
| `prod` | Strict: everything must come from the environment; OTPs hashed; no dev shortcuts. |

Startup guards: a missing/short `JWT_SECRET` fails with an explicit message; the built-in `dev-only-…` secret is rejected outside dev/test; the fixed-OTP bypass and un-hashed OTPs are rejected outside dev/test.

## Package layout (`com.takeoff.backend`)

```
config/      SecurityConfig, JwtAuthenticationFilter, RabbitMqConfig, TakeoffProperties, AdminAccountSeeder
controller/  AuthController, AccountController, HealthController, DriverController, AdminController, AdminUserController,
             DriverApplicationController, AdminApplicationController, DocumentResponses
dto/         request/response records, the OtpEvent and DecisionEvent message contracts, ApplicationDto, AdminDtos
exception/   ApiException, FieldValidationException, ApiErrorResponse, GlobalExceptionHandler
model/       User, Role, OtpToken, DriverApplication, ApplicationDocument, Notification,
             ApplicationStatus, DocumentType, VehicleType
repository/  UserRepository, OtpTokenRepository, DriverApplicationRepository, ApplicationDocumentRepository,
             NotificationRepository
security/    JwtService, CustomUserDetailsService, TakeoffUserDetails
messaging/   MessageBus (RabbitMessageBus by default; RedisMessageBus + RedisQueueWorker + LettuceListQueue when
             takeoff.messaging.provider=redis)
storage/     FileContentStore (DiskFileContentStore by default; DatabaseFileContentStore when takeoff.storage.type=database)
service/     AuthService, DriverService, AccountService, AdminUserService, TemporaryPasswordGenerator,
             OtpProducerService, OtpConsumerListener, OtpCodec,
             ApplicationService, AdminApplicationService, DocumentStorageService, NotificationService,
             NotificationProducerService, NotificationConsumerListener
sms/         SmsSender, TwilioSmsSender, LoggingSmsSender, SmsDeliveryException (provider chosen in config/SmsConfig)
validation/  @CompliantPassword, PasswordComplianceValidator, PasswordPolicy
```

## Design notes

- **Publish after commit.** `AuthService.register` commits the user first, then publishes `OTP_GENERATE`. Publishing inside the transaction could let the consumer run before the commit and not find the user. If the broker is down the account exists and the response carries `otpDispatched:false`; `POST /auth/resend-otp` recovers.
- **Malformed events never crash the listener.** Unparseable or unsupported messages are logged and dropped. A processing failure (e.g. DB outage) rejects without requeue (`default-requeue-rejected: false`), so there is no redelivery loop. There is no DLQ in the MVP.
- **Attempt counting survives failures.** `verifyOtp` uses `noRollbackFor = ApiException.class`, otherwise a wrong guess would roll back its own attempt counter.
- **JWT** uses Spring Security's Nimbus JOSE (HS256) rather than a third-party library; it has no Jackson-version coupling under Boot 4.
- **Schema.** Flyway owns it (`db/migration/V1__init_security_and_driver_schema.sql`). `Role` is mapped as `VARCHAR` explicitly (Hibernate would otherwise choose a native `ENUM`), and `Instant` as a plain UTC `TIMESTAMP` (`hibernate.type.preferred_instant_jdbc_type`), so `ddl-auto: validate` matches on MySQL and H2.

## Application workflow (Phase 2)

Status machine: `DRAFT` → `PENDING_REVIEW` → `APPROVED` | `REJECTED`; editing a rejected application reopens it as `DRAFT`. Endpoints, error codes and the user-facing flow are in the root README.

- **One application per driver** (`driver_applications.user_id` is unique) created on first read. `national_id` and `plate_number` are unique across drivers; the service checks first (`409 NATIONAL_ID_IN_USE` / `PLATE_IN_USE`) and the unique indexes are the backstop for a race.
- **Optimistic locking.** `DriverApplication` has a `@Version` column, so two admins deciding at once, or a driver and an admin, cannot both win: the loser gets `409 CONCURRENT_MODIFICATION`. `AdminApplicationService.decide` only accepts `PENDING_REVIEW` (`409 INVALID_STATUS_TRANSITION` otherwise) and requires a note to reject.
- **Decision, notification and status change are one transaction.** The `APPLICATION_DECIDED` event is published to `notification.queue` only after that transaction commits (`TransactionOperations`), best-effort: if the broker is down the decision stands and the in-app notification already exists.
- **Files.** `DocumentStorageService` checks the size, then identifies the file from its **magic bytes** (PDF, JPEG, PNG), never from the client's `Content-Type` or extension. It stores the bytes under a random UUID validated against a strict pattern, so user input never forms a path, and it keeps a sanitised display name separately. Downloads are `nosniff` and `no-store`. Files live in `takeoff.storage.upload-dir` (`UPLOAD_DIR`, default `./data/uploads`, git-ignored); `MAX_UPLOAD_BYTES` (default 5 MB) is the application-level limit, and `spring.servlet.multipart` (6 MB per file, 7 MB per request) is a slightly larger transport limit so an oversize file gets the friendly `413 FILE_TOO_LARGE` instead of a container error.
- **Access control.** `SecurityConfig` requires `ROLE_APPLICANT_DRIVER` for `/api/v1/drivers/**` and `ROLE_LOGISTICS_ADMIN` for `/api/v1/admin/**`. A driver only ever loads their own application (the id comes from the JWT, not the URL); admins address applications by id.
- **Changing your own password.** `PUT /api/v1/account/password` (`AccountController` → `AccountService`), for any role. The account is always the token's own user. A wrong current password is a `400` field error, deliberately not a `401`: the SPA signs a user out on any 401, and here they are still properly signed in. The request record redacts both passwords in `toString()`.
- **Accounts with temporary passwords.** `AdminUserService` creates accounts (`POST /admin/users`), assigns roles (`PATCH /admin/users/{id}/role`) and issues new temporary passwords. `TemporaryPasswordGenerator` produces them from a `SecureRandom` (18 characters, every class present, no `Il1O0`); they are returned once and stored only as a BCrypt hash (`IssuedCredentialDto` redacts it in `toString()`). While `users.must_change_password` is set, `TakeoffUserDetails` grants only the `PASSWORD_CHANGE_REQUIRED` authority instead of a role, so `SecurityConfig` refuses every role-protected route with `403 PASSWORD_CHANGE_REQUIRED` and allows only `PUT /account/password`; that call clears the flag. Roles are read from the database on every request, so a role change is immediate. `temporary_password_expires_at` (default 72 h, `takeoff.accounts.temporary-password-hours`) is enforced at sign-in and on every request. Own-role changes and own-password resets are refused so the last administrator cannot be removed.
- **Hosted mode.** The `MessageBus` and `FileContentStore` interfaces hide which transport and store are in use. Migrations exist twice, `db/migration` (MySQL, also run on H2 in tests) and `db/migration-postgresql`; `PostgresMigrationsTest` applies both and compares tables, columns and nullability so they cannot drift apart.
- **Notifications.** `NotificationService` writes the in-app rows. `NotificationConsumerListener` consumes `APPLICATION_DECIDED` and texts the driver through the same `SmsSender` as OTPs; it drops malformed events and never sends to the fictional test phone.

## SMS delivery

`OtpConsumerListener` texts the code through `SmsSender` **after** the OTP is stored and its transaction has committed. `takeoff.sms.provider` (`SMS_PROVIDER`) selects `twilio` (real SMS via Twilio's REST API over plain HTTPS, no SDK) or `none` (default). Full setup, behaviour and limits are in the root README under *SMS delivery*.

- Twilio credentials are validated at startup, before any HTTP client is built, so a missing value is reported by name.
- A provider failure never breaks the listener: it logs the HTTP status and Twilio error code (never the number, body or credentials) and keeps the stored code valid so the user can resend.
- `TakeoffProperties` records that hold secrets redact them in `toString()`.
- `AuthService.resendOtp` caps sends per number per hour (`OTP_MAX_SENDS_PER_HOUR`, default 5).

## OTP storage

`otp_tokens.code` holds the plain 6-digit code only when `takeoff.otp.hash-codes=false` (dev/test). Otherwise it is `HMAC-SHA256(OTP_PEPPER, userId:code)` in hex. MVP limitation: 10^6 possibilities means a hash alone is weak against someone holding both database and pepper; short expiry and the attempt limit are the real protection.

## Tests

```bash
./mvnw test              # 223 tests, no external services required
./mvnw clean package
```

| Class | Focus |
|---|---|
| `PasswordPolicyTest` | policy rules, every special character, per-rule messages, message-escaping of `{}$` |
| `SampleDocumentsTest` | the fictional `documentation/TEST_*` PDFs and PNGs are accepted by the real upload validator (real type from the bytes, under 5 MB), and each test driver has all three documents |
| `AccountManagementIntegrationTest` | creating accounts with a temporary password (shown once, stored hashed, never in the list), sign-in with it and the `403 PASSWORD_CHANGE_REQUIRED` lock until the person chooses their own, the same for a new administrator, expiry (also for an already-issued token), issuing a new one, role assignment taking effect immediately for an existing token, own-role and own-reset protection, validation, duplicates, drivers and anonymous callers refused, search and paging |
| `TemporaryPasswordGeneratorTest` | 2,000 samples all meet the password policy, contain every character class, avoid misreadable characters, are all different, and are shuffled |
| `RedisMessagingTest` | the Redis transport against an in-memory queue: topics map to the right lists, producers queue clean events and fail cleanly when Redis is down, the worker delivers in order, drops a poison message, survives an outage, and stops cleanly |
| `DatabaseFileContentStoreTest` | documents kept in the database round-trip byte for byte (up to 5 MB), the storage service applies the same type and size rules, and the sample test documents survive |
| `PostgresMigrationsTest` | the PostgreSQL migrations apply, and describe the same tables, columns and nullability as the MySQL ones |
| `HostingSupportTest` | the public health probe (and nothing else is open), and the `demo` profile gets dev conveniences but not the built-in JWT secret |
| `AccountPasswordIntegrationTest` | any signed-in person changing their own password over the real security chain (own throw-away accounts): old password stops working and the new one works, wrong current password is a field error and changes nothing, weak or unchanged new password refused, required fields, anonymous callers get 401, the request never prints the passwords |
| `DriverAccountSeederTest` | the optional seeded driver: created phone-verified with a hashed password, email normalised, only in dev/test, never overwrites an existing email or phone, refuses weak passwords and incomplete config |
| `AuthServiceTest` | register (duplicates, broker down), verify (valid/invalid/expired/consumed/attempt limit/hashed), login, resend cooldown and hourly send cap |
| `OtpConsumerListenerTest` | fixed OTP for `+15550199`, random codes otherwise, invalidation order, malformed/unsupported events, bypass safety, SMS sent after the code is stored, never for the test phone, provider failures contained |
| `TwilioSmsSenderTest` | exact Twilio request (endpoint, Basic auth, form fields) against a mock HTTP server, messaging-service vs from-number, error handling that leaks no number/body/credentials, startup validation |
| `SmsConfigTest` | provider selection, fail-fast on missing Twilio settings, unknown provider, secrets redacted in `toString()` |
| `JwtServiceTest` | claims, tamper/expiry/wrong-secret rejection, startup guards |
| `OtpCodecTest` | hashing, per-user binding, hashing cannot be disabled in prod |
| `AuthControllerIntegrationTest` | full stack over MockMvc, real security chain, real Flyway migration on H2: registration, password 400s, 409s, OTP flow, login, RBAC (200/401/403), CORS, JSON error shape |
| `DocumentStorageServiceTest` | type detection from magic bytes (a renamed `.exe` is refused), size limit, filename sanitising, random storage keys, path-traversal keys rejected, load/delete |
| `NotificationConsumerListenerTest` | approval texted with the Reference ID, rejection texted without revealing the reviewer's note, test phone never texted, provider and unexpected failures contained, malformed / unsupported / unknown-user events dropped |
| `ApplicationFlowIntegrationTest` | the whole workflow over MockMvc against the real migrations: KYC/vehicle validation and duplicates, uploads, submit rules, admin list/filter/search/detail/document access, approve (persisted, driver notified, event published) and reject-then-revise-and-resubmit, a RabbitMQ outage not undoing a decision, notifications scoped to their owner, and role separation (a driver on admin endpoints and the reverse) |

**What the tests do not cover.** They run against H2 in MySQL compatibility mode and a mocked `RabbitTemplate` whose captured message is handed to the real listener. They have **not** been run against a real MySQL, RabbitMQ or **Twilio** (the Twilio client is verified only against a mock server, and the real JDK HTTP client construction with its timeouts is not exercised in tests). Before relying on this in a shared environment, start `docker compose up -d` and do a manual registration → OTP → login pass.
