# Hosted demo: deployment guide

## The current deployment

| | |
|---|---|
| **Website** (Vercel project `take-off`) | https://take-off-seven.vercel.app (also https://take-off-jasig43s-projects.vercel.app) |
| **API** (Render web service `takeoff-backend`, Singapore, free) | https://takeoff-backend-8lkt.onrender.com, health check `GET /api/v1/health` |
| **Database** (Render Postgres `takeoff-db`, free) | internal only; the allow-list for outside connections is empty |
| **Queue** (Render Key Value `TakeOFF`, free) | internal only |
| **First administrator** | `admin@takeoff.co.zw`; the password is set in the Render service's environment and is not in this repository |

**Completing the OTP step as a reviewer:** register a driver with the evaluator test number `+15550199` and enter `123456`,
or register with any other number and read the one-time code from the API's log (Render dashboard > `takeoff-backend` >
Logs, look for `[DEV ONLY] Verification code`). The test number can be used by one account.

**Deploying a new commit:** the API was added to Render from the public repository URL, so Render does not receive push
notifications. After pushing, use *Manual Deploy* on the service (or the Render API), or connect the GitHub app to make it
automatic. Vercel is connected to the repository and deploys every push to `main` by itself.

TakeOFF runs locally on **MySQL + RabbitMQ** (see the main README). The free hosted demo instead runs on services that
have a free tier:

| Part | Where | Why this |
|---|---|---|
| Website (React) | **Vercel** | free static hosting, deploys from GitHub |
| API (Spring Boot) | **Render**, Docker web service | free, runs the `demo` profile |
| Database | **Render Postgres** | Render has no MySQL, so the app also ships PostgreSQL migrations |
| Message queue | **Render Key Value** (Redis) | Render has no RabbitMQ, so the app can use a Redis list instead |
| Uploaded documents | the database (`stored_files` table) | a free web server has no persistent disk |

The application code is the same. Two switches change what it uses:

* `SPRING_PROFILES_ACTIVE=demo` selects PostgreSQL migrations (`db/migration-postgresql`), the Redis queue
  (`takeoff.messaging.provider=redis`, RabbitMQ auto-configuration excluded) and database document storage
  (`takeoff.storage.type=database`). See `takeoff-backend/src/main/resources/application-demo.yml`.
* Locally nothing changes: no profile switch means MySQL, RabbitMQ and files on disk exactly as before.

## What the `demo` profile is, and is not

It is meant for showing the product to reviewers, with test data. It keeps the development conveniences, so:

* the one-time code is **printed in the backend log** (`[DEV ONLY] Verification code for user ...`), which is how a
  reviewer without an SMS gateway completes the OTP step;
* the evaluator test number **`+15550199` always receives the code `123456`**, and nothing is texted to it;
* OTPs are not hashed at rest.

It is **not** the development profile: there are **no built-in secrets**. The JWT secret, database and queue all come
from environment variables, and the app refuses to start without them (the built-in dev JWT secret is rejected).
Do not use it for real drivers' data.

## Environment variables (backend)

| Variable | Required | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `demo` (the Dockerfile sets it) |
| `DATABASE_URL` | yes | `jdbc:postgresql://HOST:5432/DATABASE` (a JDBC URL, not Render's `postgres://` form) |
| `DATABASE_USER`, `DATABASE_PASSWORD` | yes | database login |
| `REDIS_URL` | yes | Render Key Value internal URL, e.g. `redis://red-xxxx:6379` (reachable only from a Render service in the same region) |
| `JWT_SECRET` | yes | at least 32 random characters |
| `FRONTEND_ORIGIN` | yes | the website's origin(s), comma-separated, for CORS, e.g. `https://take-off-seven.vercel.app` |
| `ADMIN_SEED_ENABLED`, `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD`, `ADMIN_SEED_PHONE` | for the first login | creates the first administrator (created once, never overwritten) |
| `TAKEOFF_ACCOUNTS_TEMPORARY_PASSWORD_HOURS` | no | lifetime of temporary passwords (default 72) |
| `PORT` | set by Render | the port to listen on |

## Environment variable (website)

| Variable | Meaning |
|---|---|
| `VITE_API_BASE_URL` | the API's base URL, for example `https://takeoff-api.onrender.com/api/v1`. It is baked in at build time, so change it and redeploy. |

## Steps

1. **Push the repository to GitHub.** Render and Vercel both build from it.
2. **Render, database.** Create a free *PostgreSQL* database. Note its internal host, port, database name, user and
   password (they become `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`).
3. **Render, queue.** Create a free *Key Value* instance in the **same region**. Use its *internal* URL as `REDIS_URL`.
4. **Render, API.** Create a *Web Service* from the repository: runtime **Docker**, root directory `takeoff-backend`,
   instance type *Free*, health check path `/api/v1/health`, and the environment variables above.
   The first build takes several minutes (Maven); the first start takes a minute or two.
5. **Vercel, website.** In the project settings set *Root Directory* to `takeoff-frontend` (Vercel detects Vite; the
   folder's `vercel.json` adds the single-page-app rewrite), add `VITE_API_BASE_URL`, and deploy.
6. Put the website's URL into the API's `FRONTEND_ORIGIN` and redeploy the API if it changed.

## Things to know

* **Cold starts.** A free Render web service sleeps after 15 minutes without traffic. The first request afterwards takes
  about a minute. The website copes with this: when it loads it asks the API's health check, which starts the wake-up
  while the visitor is still on the sign-in page, and it shows a "Waking up the TakeOFF server" notice until the API
  answers. Its requests wait up to 90 seconds, longer than a wake-up needs. Opening the site shortly before a demo
  still makes it faster. Opening the API's own address (`GET /`) returns a short "running" message.
* **Free Postgres expires after 30 days** (with a grace period). The Key Value store is in-memory: if it restarts, any
  queued but unprocessed events are lost. That costs at most one OTP request, which the user recovers with "Resend code".
* **Queue delivery is at-most-once.** A Redis list cannot redeliver a message after a crash the way RabbitMQ can. The
  OTP flow tolerates this (Resend code); the driver's in-app notification is written to the database before its event is
  queued, so only the SMS about a decision could be missed.
* **Logs.** On Render the backend log (where the one-time codes are printed) is under the service's *Logs* tab.
* **Nothing here is a secret** except the environment variable values. Keep them in the hosts' dashboards, never in the
  repository. Rotate any token that has been pasted into a chat or a ticket.
