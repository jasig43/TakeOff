# takeoff-frontend

React 19 + Vite + TypeScript + Tailwind CSS 4 single-page app for TakeOFF (MVP Phase 1).

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
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Backend base URL |
| `VITE_SHOW_DEV_HINTS` | `false` | Show the evaluator OTP hint in non-dev builds (always shown under `npm run dev`) |

The hint (test phone `+15550199` → OTP `123456`) is only honoured by a backend running in dev/test mode.

## Routes

`/` sign in (the landing page, for drivers and admins alike) · `/register` sign up · `/verify-otp` · `/driver/dashboard` (applicant only) · `/admin/dashboard` (admin only). The older `/login` and `/admin/login` URLs redirect to `/`. After login, the user is sent to the dashboard for the role the server returns.

`ProtectedRoute` sends signed-out visitors to the matching login page and sends a signed-in user with the wrong role to their own dashboard (never back to a login page, which avoids redirect loops). It is a UX convenience; the backend enforces RBAC independently.

## Structure

```
src/
  api/          axios client (token interceptor, error normalisation), authApi, types
  components/   layout/  auth/  ui/
  context/      AuthContext, ToastContext (+ *.ts context objects)
  hooks/        usePasswordValidation, useCountdown, useApiResource, …
  pages/        one component per route (lazy-loaded)
  routes/       AppRouter, ProtectedRoute
  utils/        passwordValidation, formValidation, tokenStorage
```

## Behaviour worth knowing

- **Password rules** live in `utils/passwordValidation.ts` and mirror the backend's `PasswordPolicy`; keep them in sync. The submit button stays disabled until every field is valid.
- **Token storage.** The JWT, its expiry and a safe user summary are in `localStorage` (`takeoff.auth`); expired sessions are discarded on read and by a timer. Trade-off: readable by any script on the origin (XSS). Planned hardening: httpOnly refresh cookie. Pending OTP state (email, masked phone) is in `sessionStorage` and never holds a password or token.
- **API client.** Errors are normalised to `ApiError` (`status`, `code`, `fieldErrors`). Network failures and 5xx show generic messages; details go to the console only in dev. A 401 on a token-bearing, non-auth request clears the session once and signs the user out; auth endpoints are excluded so a wrong password can never trigger a sign-out loop.
- **Accessibility.** Labelled inputs with errors linked by `aria-describedby`, visible focus, skip link, state never conveyed by colour alone (icons + text), polite live regions for password strength and toasts, OTP usable by keyboard and paste.
- **Motion.** `MotionConfig reducedMotion="user"` plus a CSS `prefers-reduced-motion` reset; confetti is disabled for reduced motion.
- **Theme.** Follows the operating system: light and dark colours are CSS variables switched by `@media (prefers-color-scheme: dark)` (`src/index.css`), so it updates live when the OS setting changes, has no flash on load, stores nothing, and has no toggle.
- **Layout.** Public pages (sign in, sign up, OTP) have no navigation. Signed-in pages use `AppShell`: a fixed left `Sidebar` from the `lg` breakpoint, and below that a slide-in drawer opened by a small floating menu button (focus moves into the drawer, Tab is kept inside it, Escape closes it and returns focus). There is no top bar.

## Tests

Vitest + Testing Library: password rules (every special character, boundaries, strength levels), form validators, `OtpInput` (numeric only, auto-advance, backspace, paste, arrows), `ProtectedRoute` role matrix and expired sessions, the sign-in page (driver and admin redirects, unverified phone, bad credentials, button gating), and the sidebar (current page, user and role, sign out, phone drawer open/close, Escape, focus handling).
