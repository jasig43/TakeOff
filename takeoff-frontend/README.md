# takeoff-frontend

React 19 + Vite + TypeScript + Tailwind CSS 4 single-page app for TakeOFF (MVP Phase 2): sign-in, registration and OTP, plus the driver application wizard and the administrator review portal.

## Scripts

```bash
npm install
npm run dev            # http://localhost:5173 (strict port)
npm run build          # tsc -b && vite build
npm run lint           # oxlint
npm test -- --run      # Vitest (non-interactive)
```

## Configuration

Copy `.env.example` to `.env.local`.

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Backend base URL (baked in at build time; on Vercel set it in the project's environment variables and redeploy) |

Deploying to Vercel: set the project's *Root Directory* to `takeoff-frontend`. `vercel.json` provides the single-page-app rewrite (every path serves `index.html`) and long caching for the hashed assets. See [`DEPLOYMENT.md`](../DEPLOYMENT.md).

## Routes

| Route | Who | What |
|---|---|---|
| `/` | everyone | Sign in (the landing page, for drivers and admins alike) |
| `/register`, `/verify-otp` | everyone | Sign up and phone verification |
| `/driver/dashboard` | driver only | Application status, progress, latest notifications |
| `/driver/application` | driver only | The five-step wizard (personal, identity and licence, vehicle, documents, review and submit); after submission, the read-only view with the Reference ID and status, or the rejection note with **Update and resubmit** |
| `/driver/notifications` | driver only | Inbox with mark-as-read |
| `/admin/dashboard` | admin only | Counts by status (links into the filtered list) and the review queue |
| `/admin/applications` | admin only | Searchable, filterable, paged list (filter and page are kept in the URL) |
| `/admin/applications/:id` | admin only | Every field, each document, and the approve / reject controls |
| `/admin/settings` | admin only | Two tabs: *Account and security* (details, change password) and *Users and roles* (search accounts, create one with a temporary password, assign a role, issue a new temporary password) |
| `/change-password` | signed in, on a temporary password | Choose your own password; nothing else works until then. Anyone who does not need it is sent to their dashboard |

The older `/login` and `/admin/login` URLs redirect to `/`. After login, the user is sent to the dashboard for the role the server returns, and the sidebar only offers that role's pages.

`ProtectedRoute` sends signed-out visitors to the matching login page and sends a signed-in user with the wrong role to their own dashboard (never back to a login page, which avoids redirect loops). It is a UX convenience; the backend enforces RBAC independently.

## Structure

```
src/
  api/          axios client (token interceptor, error normalisation), authApi, applicationApi
                (applicationApi, notificationApi, reviewApi), types
  components/   layout/  auth/  application/ (wizard steps, uploads, summary, decision panel)  ui/
  context/      AuthContext, ToastContext (+ *.ts context objects)
  hooks/        usePasswordValidation, useCountdown, useApiResource, useStepForm, useDebounce,
                usePolledResource (the header bell's data), …
  pages/        one component per route (lazy-loaded)
  routes/       AppRouter, ProtectedRoute
  utils/        passwordValidation, formValidation, applicationValidation, formatting, apiHelpers, tokenStorage
  test/         shared fixtures and a renderPage helper (real providers, signed in as a chosen role)
public/         favicon.ico, favicon-16/32, apple-touch-icon, icon-192/512, icon-maskable-512, site.webmanifest
```

## Behaviour worth knowing

- **Password rules** live in `utils/passwordValidation.ts` and mirror the backend's `PasswordPolicy`; keep them in sync. The submit button stays disabled until every field is valid.
- **Token storage.** The JWT, its expiry and a safe user summary are in `localStorage` (`takeoff.auth`); expired sessions are discarded on read and by a timer. Trade-off: readable by any script on the origin (XSS). Planned hardening: httpOnly refresh cookie. Pending OTP state (email, masked phone) is in `sessionStorage` and never holds a password or token.
- **API client.** Errors are normalised to `ApiError` (`status`, `code`, `fieldErrors`). Network failures and 5xx show generic messages; details go to the console only in dev. A 401 on a token-bearing, non-auth request clears the session once and signs the user out; auth endpoints are excluded so a wrong password can never trigger a sign-out loop.
- **Accessibility.** Labelled inputs with errors linked by `aria-describedby`, visible focus, skip link, state never conveyed by colour alone (icons + text), polite live regions for password strength and toasts, OTP usable by keyboard and paste.
- **Motion.** `MotionConfig reducedMotion="user"` plus a CSS `prefers-reduced-motion` reset; confetti is disabled for reduced motion.
- **Theme.** Follows the operating system: light and dark colours are CSS variables switched by `@media (prefers-color-scheme: dark)` (`src/index.css`), so it updates live when the OS setting changes, has no flash on load, stores nothing, and has no toggle.
- **Layout.** Public pages (sign in, sign up, OTP) have no navigation. Signed-in pages use `AppShell`: a fixed left `Sidebar` from the `lg` breakpoint, and below that a slide-in drawer opened by a small floating menu button (focus moves into the drawer, Tab is kept inside it, Escape closes it and returns focus). Every signed-in page also has a fixed frosted-glass `TopBar` (the sidebar's glass look, tinted with the page background; it stays in view while the page scrolls, and from `lg` it sits beside the sidebar) holding the notification bell. A driver's bell shows the unread count and a panel with the latest notifications (mark one or all as read, and a link to the full inbox at `/driver/notifications`); an administrator's bell shows how many applications are waiting for review and links to that queue. Notifications are deliberately not in the sidebar or on the dashboards.

- **Application forms.** Each step validates on submit with the same rules as the backend (`utils/applicationValidation.ts`) and saves on its own, so a driver can leave and resume. Server messages are shown next to the field they belong to (validation errors by field name, and duplicate ID / plate by error code). A submitted application is read-only; a rejected one is shown with the reviewer's note first.
- **Documents.** The file input is native (visually hidden, still keyboard and screen-reader operable). Files are pre-checked for type and size, but the server has the final say because it inspects the real bytes. Protected files are fetched with the bearer token and opened as a blob in a new tab (opened synchronously in the click handler so popup blockers allow it).
- **Loading data.** `useApiResource` derives `loading` from the request that is current, ignores results from superseded requests, and keeps the previous data while filters change, so lists don't flash empty.
- **Icons.** The app icon and favicons are generated from one source image (a chauffeur beside a van). The stock caption is cropped away and the artwork is composited black-on-white so it stays legible on both light and dark browser tabs. The same `icon-192.png` is the sidebar logo.

## Tests

Vitest + Testing Library: password rules (every special character, boundaries, strength levels), form validators, `OtpInput` (numeric only, auto-advance, backspace, paste, arrows), `ProtectedRoute` role matrix and expired sessions, the sign-in page (driver and admin redirects, unverified phone, bad credentials, button gating), and the sidebar (current page, user and role, sign out, phone drawer open/close, Escape, focus handling, and that each role is offered only its own links). Phase 2 adds the application validators; the driver dashboard and application wizard (validation, saving, server errors, uploads, review and submit, the submitted and rejected views); the admin dashboard, application list (filter, debounced search, paging, empty and error states) and detail page (documents, approve, reject-needs-a-reason, confirmation, conflicts, already-decided applications, invalid ids). The header (fixed frosted-glass bar; the driver bell's count, panel, mark-as-read, Escape and click-away; the administrator bell's waiting count and queue link) has its own tests, as does the admin Settings page (account details, the button gated on the password rules, success clearing the fields, server errors under the right field, double-submit). The users-and-roles panel (list, search, paging, create with validation and duplicates, the temporary password shown once and forgotten, copy, role change with confirmation, new temporary password) and the forced password-change screen have their own tests, and the route guard and sign-in are tested for the temporary-password redirect. Currently 143 tests.
