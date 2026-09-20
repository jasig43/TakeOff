# takeoff-backend

Spring Boot 4 / Java 17 REST API for TakeOFF (MVP Phase 1): registration, RabbitMQ-based OTP, JWT authentication and RBAC.

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
| `test` | Used by the automated tests (H2, mocked broker). Lives in `src/test/resources`. |
| `prod` | Strict: everything must come from the environment; OTPs hashed; no dev shortcuts. |

Startup guards: a missing/short `JWT_SECRET` fails with an explicit message; the built-in `dev-only-…` secret is rejected outside dev/test; the fixed-OTP bypass and un-hashed OTPs are rejected outside dev/test.

## Package layout (`com.takeoff.backend`)

```
config/      SecurityConfig, JwtAuthenticationFilter, RabbitMqConfig, TakeoffProperties, AdminAccountSeeder
controller/  AuthController, DriverController, AdminController
dto/         request/response records and the OtpEvent message contract
exception/   ApiException, ApiErrorResponse, GlobalExceptionHandler
model/       User, Role, OtpToken
repository/  UserRepository, OtpTokenRepository
security/    JwtService, CustomUserDetailsService, TakeoffUserDetails
service/     AuthService, DriverService, OtpProducerService, OtpConsumerListener, OtpCodec
sms/         SmsSender, TwilioSmsSender, LoggingSmsSender, SmsDeliveryException (provider chosen in config/SmsConfig)
validation/  @CompliantPassword, PasswordComplianceValidator, PasswordPolicy
```

## Design notes

- **Publish after commit.** `AuthService.register` commits the user first, then publishes `OTP_GENERATE`. Publishing inside the transaction could let the consumer run before the commit and not find the user. If the broker is down the account exists and the response carries `otpDispatched:false`; `POST /auth/resend-otp` recovers.
- **Malformed events never crash the listener.** Unparseable or unsupported messages are logged and dropped. A processing failure (e.g. DB outage) rejects without requeue (`default-requeue-rejected: false`), so there is no redelivery loop. There is no DLQ in the MVP.
- **Attempt counting survives failures.** `verifyOtp` uses `noRollbackFor = ApiException.class`, otherwise a wrong guess would roll back its own attempt counter.
- **JWT** uses Spring Security's Nimbus JOSE (HS256) rather than a third-party library; it has no Jackson-version coupling under Boot 4.
- **Schema.** Flyway owns it (`db/migration/V1__init_security_and_driver_schema.sql`). `Role` is mapped as `VARCHAR` explicitly (Hibernate would otherwise choose a native `ENUM`), and `Instant` as a plain UTC `TIMESTAMP` (`hibernate.type.preferred_instant_jdbc_type`), so `ddl-auto: validate` matches on MySQL and H2.

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
./mvnw test              # 131 tests, no external services required
./mvnw clean package
```

| Class | Focus |
|---|---|
| `PasswordPolicyTest` | policy rules, every special character, per-rule messages, message-escaping of `{}$` |
| `AuthServiceTest` | register (duplicates, broker down), verify (valid/invalid/expired/consumed/attempt limit/hashed), login, resend cooldown and hourly send cap |
| `OtpConsumerListenerTest` | fixed OTP for `+15550199`, random codes otherwise, invalidation order, malformed/unsupported events, bypass safety, SMS sent after the code is stored, never for the test phone, provider failures contained |
| `TwilioSmsSenderTest` | exact Twilio request (endpoint, Basic auth, form fields) against a mock HTTP server, messaging-service vs from-number, error handling that leaks no number/body/credentials, startup validation |
| `SmsConfigTest` | provider selection, fail-fast on missing Twilio settings, unknown provider, secrets redacted in `toString()` |
| `JwtServiceTest` | claims, tamper/expiry/wrong-secret rejection, startup guards |
| `OtpCodecTest` | hashing, per-user binding, hashing cannot be disabled in prod |
| `AuthControllerIntegrationTest` | full stack over MockMvc, real security chain, real Flyway migration on H2: registration, password 400s, 409s, OTP flow, login, RBAC (200/401/403), CORS, JSON error shape |

**What the tests do not cover.** They run against H2 in MySQL compatibility mode and a mocked `RabbitTemplate` whose captured message is handed to the real listener. They have **not** been run against a real MySQL, RabbitMQ or **Twilio** (the Twilio client is verified only against a mock server, and the real JDK HTTP client construction with its timeouts is not exercised in tests). Before relying on this in a shared environment, start `docker compose up -d` and do a manual registration → OTP → login pass.
