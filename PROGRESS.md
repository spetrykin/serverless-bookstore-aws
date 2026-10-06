# Progress log

## Current state
- Account setup, architecture — completed. → docs/architecture-plan.md.
- **Week 1 (Auth)** — closed, deployed, confirmed by an end-to-end test. → architecture-plan.md §5, incident-log.md.
- **Week 2 (Catalog + Order)** — closed, deployed, 19/19 curl checklist. → architecture-plan.md §6.
- **Week 3 (Events, Admin, Observability)** — closed for Core scope (2026-08-18), except X-Ray subsegments: Lambda-level tracing verified 2026-10-06; SDK subsegments deferred. → architecture-plan.md §5.15, incident-log.md 2026-08-18 and 2026-10-06.
- **Stretch (architecture-plan.md §7) — mostly complete (WAF open).** Repository: https://github.com/spetrykin/serverless-bookstore-aws (public).
  - §7.1 item 1 CI/CD — closed. Two jobs: `validate` and `frontend` (Node 24.x); both green — per the maintainer's manual check in Actions.
  - §7.1 item 2 Recommendation Lambda + Bedrock — closed and confirmed on seed data. `RECS_MODE=mock` — **a real Bedrock call has not been made yet**. → incident-log.md 2026-08-20.
  - §7.1 item 3 OpenAPI — closed. `openapi.yaml`, 14 operations, `redocly lint`: 0 errors, 2 deliberate warnings (license url; no 4xx on /recommendations).
  - §7.1 item 4 Vue frontend — closed (admin screens and tests out of scope). Verified manually on the live stack 2026-10-06: login, catalog, order, orders, recommendations; **register was not verified**. → frontend/README.md.
  - WAF — not started.
  - **SnapStart cold-start** (~4.5-6 s on the first DynamoDB call after restore) — mechanism found (JIT/class-loading, not network). Fix deferred: Invoke Priming is the right but non-trivial option; Provisioned Concurrency rejected (incompatible with SnapStart, breaks near-$0). → architecture-plan.md §5.10, incident-log.md 2026-08-22.
  - **Dormant stack:** after >14 days idle the SnapStart snapshots become `Inactive`, the first calls return 500. → incident-log.md 2026-10-06.
- Fixtures `events/*.json` — placeholder, regeneration not done (not blocking).

## Next step
WAF with the remaining time — not required for the core system.

**After >14 days idle, before a demo — wake the stack:** `scripts/wake-stack.sh`
(run manually by the maintainer: ~2 AWS calls per function). It invokes the
`live` alias only of functions not in `Active` — the handler does not run, the
admin/order logic is not touched; it waits until all 16 functions become `Active`. Do not
wake with real HTTP requests to `/admin/users/*` and `POST /orders` — they
mutate data. Verified 2026-10-06 (run by the maintainer): 16 functions, 12
were `Inactive`, 4 `Active` skipped, all `Active` in ~4 rounds, no errors.

Live checks 2026-10-06 (after wake-stack.sh): smoke login/books/
recommendations — 200; frontend click-through — see above. The order created a
real record and reduced the book's stock — the dev stack's test data has been changed.

## Environment (verified and updated 2026-07-31, do not reopen without a reason)

All versions below were confirmed directly (official release notes/sites/GitHub releases), not by the package manager cache without verification.

| Tool | Version | Status | How it was updated |
|---|---|---|---|
| Java (SDKMAN default) | `25.0.4-zulu` | ✅ current (confirmed by Oracle/Microsoft OpenJDK release notes, July 2026) | `sdk install java 25.0.4-zulu` + `sdk default`. |
| Maven | `3.9.16` | ✅ current stable (confirmed by maven.apache.org/download.cgi; 3.10.0-rc-1/4.0.0-rc-5 exist, but they are previews, not recommended) | installed manually into SDKMAN's candidates directory (sdk install failed on the default mirror; see docs/incident-log.md) |
| SAM CLI | `1.164.0` | ✅ current (confirmed by `github.com/aws/aws-sam-cli/releases/latest`) | Updated via the official installer (not pip) into `~/.local/aws-sam-cli`, `--install-dir`/`--bin-dir` without sudo. |
| cfn-lint (standalone, `cfn-lint` in PATH) | `1.26.1` | ⚠️ partial — pip hits a ceiling of 1.26.1 (`cfn-lint==` lists the full list of versions, which ends at 1.26.1). The actual current release is newer (`cfn_lint-1.52.1.dist-info` was found inside SAM CLI 1.164.0) | Updated from 1.20.2 → 1.26.1. It goes no further via pip. |
| cfn-lint (bundled in `sam validate --lint`) | `1.52.1` | ✅ current, separate from the standalone one — already used in real validation, no action needed | Found inside `~/.local/aws-sam-cli/1.164.0/dist/_internal/`, independent of pip. |
| AWS CLI v2 | `2.36.13` | ✅ current (confirmed by `github.com/aws/aws-cli` CHANGELOG.rst) | Official installer (not apt/pip) into `~/.local/aws-cli`, `--install-dir`/`--bin-dir` without sudo. |

## Deploy (dev stack)
- Stack: `bookstore-dev`, region `eu-central-1`, deployed manually via
  `scripts/deploy.sh` (runs `sam deploy`, then checks the authorizer; see CLAUDE.md).
- The SSM parameter `/bookstore/jwt-signing-key` was created manually (not as part of the
  stack) — on a full rebuild of the environment from scratch it needs to be created again
  (see CLAUDE.md, section "Cleanup after deploy", for the command).
- The managed S3 bucket `aws-sam-cli-managed-default-samclisourcebucket-
  <suffix>` — created automatically by SAM, left between sessions
  on purpose, do not delete without an explicit decision to close the project.
- The cleanup checklist (if/when we decide to tear the stack down) — see CLAUDE.md.
