# Bookstore frontend

Minimal Vue 3 (`<script setup>`) + Vite demo frontend for the bookstore API
(Stretch §7.1 item 4, `docs/architecture-plan.md`). Local dev only — not
deployed to AWS.

## Run

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`. Login with any user seeded in the dev
stack (see `../scripts/seed-test-data.sh`), or register a new one.

## How it talks to the backend

`VITE_API_BASE` in `.env` points at the deployed dev stack
(`bookstore-dev`, `eu-central-1`). The browser never calls that URL
directly — it calls same-origin `/api/*`, and Vite's dev-server proxy
(`vite.config.js`) forwards those requests to `VITE_API_BASE` from Vite's
own Node process. This is a deliberate workaround, not an oversight: the
deployed `HttpApi` has no `CorsConfiguration` in `template.yaml` (confirmed
by inspection, not assumed), so a direct browser call would fail CORS
preflight. Fixing that backend-side would mean touching `template.yaml`
and redeploying — which risks the `EnableSimpleResponses` authorizer trap
documented in the root `CLAUDE.md` — for a frontend demo that doesn't need
it. If the dev stack is ever redeployed to a new `HttpApiUrl`, update
`VITE_API_BASE` in `.env` to match (see the stack's CloudFormation
`Outputs`).

API calls are hand-written in `src/api/client.js` directly against the
schemas in the repo-root `openapi.yaml` (JSDoc shape comments, no codegen
step).

## State management — no Pinia, no vue-router

Two module-level singleton composables carry all shared state:

- `src/composables/useAuth.js` — access token, decoded role, refresh flow.
- `src/composables/useCart.js` — the in-progress order (cart).

`App.vue` switches between six screens with a single reactive `view` ref
(`'login' | 'register' | 'catalog' | 'order' | 'orders' | 'recommendations'`)
— a router was judged not worth its weight for six screens with no
deep-linking requirement.

## Token storage — a deliberate, non-default choice

- **Access token: in-memory only** (a plain `ref`, never persisted). Lost on
  every page reload, by design.
- **Refresh token: `sessionStorage`.** Survives a reload within the same
  browser tab/session; cleared when the tab closes.

On mount, `App.vue` calls `useAuth().bootstrap()`, which checks
`sessionStorage` for a refresh token and — if present — silently exchanges
it for a new access token, so a reload doesn't force a re-login as long as
the refresh token is still valid. If that exchange fails (expired, revoked,
or the user was blocked), the session is cleared and the user lands back on
the login screen.

This is a conscious tradeoff for a live-demo project, not a production
security posture: `localStorage` would be more convenient (survives longer,
across tabs) but has a larger XSS-exfiltration blast radius since it never
expires on its own; an access token that's *only* ever in memory shrinks
that surface for the token that matters most (it's the one actually sent on
every request), at the cost of a slightly less seamless reload experience.

On any `401` from an authenticated call, `useAuth().authedCall(...)`
performs exactly one silent `/refresh` + retry before giving up and
propagating the error — matching the backend's no-rotation refresh design
(`docs/architecture-plan.md` §5.14: the same refresh token stays valid
across repeated calls, no new one is issued on `/refresh`).

## Structure and navigation

```
src/
  App.vue            view switch + session bootstrap
  components/        AppHeader (nav, data-driven via NAV_ITEMS), Spinner
  composables/       useAuth, useCart (module-level singletons)
  views/             one file per screen
  api/client.js      all fetch calls, one function per endpoint
```

Navigation lives in two places only: `App.vue` owns the `view` ref and
which view renders for each value; `AppHeader.vue`'s `NAV_ITEMS` array
lists the header buttons. Adding a screen = one view file + one `v-if`
branch in `App.vue` + one `NAV_ITEMS` entry. There is no URL routing, so
the browser back button and deep links don't work (accepted for six
screens, see above).

## Known gaps (API-driven, not oversights)

- **Dormant stack returns 500 / slow first requests.** The backend uses
  Lambda SnapStart; after ~14 days with no traffic every function's
  snapshot goes `Inactive`, and the first invocation of each function
  fails (`SnapStartNotReadyException`, surfaced by API Gateway as a bare
  `500`) while Lambda rebuilds it (~30 s). Normal cold restores afterwards
  are ~4-6 s on the first DynamoDB call. If the app shows errors after a
  long idle period, wait a minute and retry. Details: `docs/incident-log.md`

- **`npm ci` reports 3 high-severity advisories (known, assessed, not
  applicable; `npm audit fix` deliberately not run).** Two distinct
  advisories across 3 packages: `@vue/server-renderer` (SSR XSS; `vue` is
  flagged only through it) — not applicable, the app is client-only and
  never uses server-side rendering; `source-map-js` (event-loop DoS via
  crafted source maps) — build-time only, not in the shipped bundle.
- **No true "Hello `<username>`" after a plain login.** `LoginResponse`
  only carries `userId`, `accessToken`, `refreshToken` — no `name` — and
  there's no `GET /me` endpoint to fetch it separately. The header falls
  back to the email the user typed into the login form. Only a session that
  started via `/register` (which does return `name`) shows a real name.
- **No Profile-edit screen**, despite one existing in the original
  wireframes (`docs/requirements.md`). No backend endpoint exists to back
  it (no `PUT /users/me` or equivalent in `openapi.yaml`) — not built
  against nothing.
- **No admin screens.** Scope for this pass is the user-facing flow only
  (login/register → catalog → order → orders → recommendations); admin
  book/user management (`/admin/*`) was deliberately left out of this
  iteration.

## Screens

1. Login
2. Register (auto-login on success, per `openapi.yaml` /register)
3. Catalog — `GET /books`, cursor-paginated ("Next"), add-to-cart,
   `ABSENT` badge when `count === 0`
4. Order (cart review + `POST /orders`, inline 404/409 handling)
5. Orders — `GET /orders`, cursor-paginated
6. Recommendations — `GET /recommendations`, shows whether the result is
   `personalized` or `fallback`

## Tests

None — out of scope for this Stretch item (agreed plan).

## Verification status

Verified manually against the live dev stack, after running
`scripts/wake-stack.sh` (see Known gaps above): login, catalog, order,
orders and recommendations all worked as expected. Placing an order
created a real record and decremented that book's stock. **Register was
not exercised in this pass.**

CI (per manual check in GitHub Actions): both the `validate` and
`frontend` jobs passed; the `frontend` job ran on Node 24.x.
