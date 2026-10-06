# Bookstore pet-project — architecture review and plan

## 1. Weak points of the original plan

This section is the review of the initial plan, written before implementation.

### 1.1 Time conflict (critical)
The original breakdown used up the entire time budget for a single pet project, although
the goal was one of several projects within a fixed time box (scope control).
**Decision:** the plan is compressed to **Core (3 weeks)** + **Stretch (with the remaining time)**.
If Core does not fit into 3 weeks — stop at that scope, do not expand the scope.

### 1.2 The moment of stock decrement is not settled (architecturally significant)
In the original plan, decrement stock was "either synchronously in the Order Lambda, or via
EventBridge — we'll decide as we go". This is not a detail but a source of race conditions and
inconsistent data if left to "we'll decide later".

**Decision:** the stock decrement happens **synchronously inside the Order Lambda**,
via a DynamoDB conditional write (`Count > 0`), **before** the event is published.
EventBridge is used only for side effects (analytics, notifications),
and is never the source of truth for stock. This removes
the ambiguity and makes the Order Lambda the only place that changes stock.

### 1.3 Incorrect name of the pattern — "CQRS"
The split "visible books via the GSI" vs "all books for the admin via the main
table" is **not CQRS**, but an ordinary read optimization via a secondary index.
Real CQRS implies separate write and read models, often with
asynchronous projection. Calling the GSI filter "CQRS" in a technical walkthrough is a risk:
a reviewer experienced with CQRS will ask "how is the read model projected", and it will come out that
there is no such pattern in fact.

**Decision:** in documentation and code call it what it is — "read
model via GSI projection", not CQRS. For a real CQRS implementation —
optionally in Stretch: an asynchronous projection of the catalog into a separate
denormalized table via DynamoDB Streams, that is already a real pattern.

### 1.4 No idempotency strategy for event handlers
EventBridge + Lambda — **at-least-once delivery**. If the `OrderCreated`
handler (analytics/notifications) is not idempotent, a duplicate invocation produces
duplicated side effects.

**Decision:** the event handler stores `eventId` (or `orderId`) as an
idempotency key, a conditional write prevents repeated processing.

### 1.5 No test strategy at all
The plan does not mention tests even once. For production-grade engineering the absence of
automated tests is a noticeable gap, even in a pet project.

**Decision:** at minimum — unit tests of the business logic (JUnit 5 + Mockito) and
contract tests of the Lambda handlers via `sam local invoke` with event
fixtures. Do not cover 100%, but show a deliberate approach.

### 1.6 No observability
The plan covered measuring cold start, but did not build in observability as a permanent
practice: structured logs, tracing of the chain Order → EventBridge →
consumer. (Superseded: see §5.15; HttpApi has no tracing property, EventBridge is not
traced; Lambda-level recording verified 2026-10-06 — see §5.15.)

**Decision:** structured JSON logs (not `System.out.println`), AWS X-Ray
enabled on API Gateway + Lambda + EventBridge to trace the asynchronous
order chain. Cheap in time, high value for the cost.

### 1.7 No CI/CD
A manual `sam deploy` is fine for development, but without a pipeline it leaves the delivery
process undemonstrated.

**Decision:** a Stretch task — a GitHub Actions workflow: `sam build` + `sam
deploy` on push to `main`, with `sam validate --lint` as a gate.

**Status (implemented differently, Stretch §7.1 item 1):** `.github/workflows/ci.yml` runs on
push and pull request to `main`. Job `validate`: `mvn test`, `sam validate --lint`,
`sam build`. Job `frontend`: `npm ci` and `npm run build` (Node 24). There is no deploy
step and no AWS credentials in CI; deploys are manual by rule (CLAUDE.md).

### 1.8 Lambda IAM roles are not delimited per function
The plan covered restricting the agent's permissions, but did not address this: every Lambda function
must have its **own** narrow execution role (the Auth Lambda must not be able to
write to Bedrock, the Recs Lambda must not be able to delete DynamoDB records).
SAM by default may generate broader roles unless specified explicitly.

**Decision:** explicit `Policies:` sections in the SAM template for every function,
least-privilege following the pattern "exactly what this function needs and no more".

### 1.9 No protection of auth endpoints from brute force
Argon2id protects the storage of passwords, but does not protect against password guessing via
repeated login attempts if the endpoint is not rate limited.

**Decision:** throttling on API Gateway for `/login` (implemented as route-level
throttling on `POST /login` — `RouteSettings` on `BookstoreHttpApi` in `template.yaml`, rate
limit 2, burst limit 5 — not a usage plan) at the very least; a WAF rate-based rule — optional in Stretch.

### 1.10 No API contract
There is no OpenAPI specification — for a portfolio this is an expected artifact.

**Decision:** `openapi.yaml` is generated next to the SAM template, at least for
the main endpoints (auth, catalog, orders).

---

## 2. Architecture (updated)

Diagram: Client → API Gateway → 4 service areas (Auth, Catalog, Order,
Recs) → DynamoDB (single-table) / EventBridge / Bedrock.

Clarifications to the diagram:
- Auth and Catalog write to **the same** DynamoDB table, different partitions
  (`PK=USER#...`, `PK=BOOK#...`) — not two stores.
- Order Lambda: the only write point for `Count`. It publishes `OrderCreated`
  **after** a successful conditional write, not before.
- The EventBridge consumer (analytics/notifications) is idempotent, it does not touch stock.
- Each Lambda has a separate IAM execution role, least-privilege.
- The deployed stack has 16 Lambda functions (`template.yaml`): 4 auth, 4 catalog, 2 order,
  1 order-event consumer, 4 admin-user, 1 recommendation. 15 of them access DynamoDB item
  data; `AuthorizerFunction` holds only `dynamodb:DescribeTable` (§5.4).

---

## 3. Revised plan

### Core (3 weeks, mandatory scope)

**Week 1 — foundation + Auth**
- SAM project, Java 25 + Spring Boot 4.1.0 (see §5.6), DynamoDB single-table via the SAM
  template (IaC from day one).
- Auth: registration (Argon2id), login, access+refresh tokens, Lambda
  Authorizer, throttling on `/login`.
- SnapStart enabled right away, first cold start measurement.
- Unit tests of the auth logic.

**Week 2 — Catalog + Order**
- Read model of books (GSI on `visible=true`) — the user's storefront.
- Admin CRUD of books (the full list, hiding, not deleting).
- Order: conditional write for the decrement, price snapshot, synchronously within the
  Order Lambda (see item 1.2).
- The user's order list.
- `sam local invoke` tests for the Order handler (including the stock race).

**Week 3 — events, admin, observability**
- EventBridge: `OrderCreated` → idempotent consumer (analytics/log).
- Admin: user list, block/unblock (effect on
  refresh tokens), deletion — blocking and its effect on refresh tokens: §5.2;
  list, unblock and delete are described in the code and in `openapi.yaml`.
- ~~CRaC hooks for the DynamoDB client~~ — already implemented as part of week 1/2
  (`DynamoDbCracResource`, see §5.10), not an open item of week 3.
- X-Ray tracing across the whole chain (Lambda-level only; Lambda-level recording verified
  2026-10-06; see §5.15).
- Per-function IAM roles brought to least-privilege (applied as
  each new function is written, not as a separate phase at the end).

### Stretch (with the remaining time, not required for the core system)

- Recommendation Lambda + Bedrock (Nova Micro, mock → real).
- Vue frontend via AI generation, minimal.
- GitHub Actions CI/CD.
- OpenAPI specification.
- Real CQRS: DynamoDB Streams → a denormalized read table of the catalog
  (for a real pattern, not a renaming of the GSI).
- A WAF rate-based rule on top of API Gateway throttling.

---

## 4. Open risks

- 3 weeks for Core is an optimistic estimate; if week 1 stretches, cut Stretch, not Core.
- CRaC hooks and SnapStart state-freezing are the least familiar area;
  allow more time for week 3 than it seems on paper.
- Bedrock and the frontend in Stretch are deliberately optional — if time is short
  toward the end of the time box, Core by itself already covers the full demonstrable scope
  (auth security, inventory concurrency, event-driven order flow, IaC,
  observability, least-privilege IAM).

---

## 5. Architecture decisions — Auth and infrastructure

> This section answers the question "how the system is structured and why" —
> evergreen facts and their rationale. The step-by-step chronicle of investigations
> (who found what, in what order, with stack traces) is in
> `docs/incident-log.md`. If a decision here is overridden/refined by a new
> decision — the corresponding item is edited in place, not duplicated
> by a new item next to it.

### 5.1 API Gateway — resource type
`AWS::Serverless::HttpApi` (HTTP API) is used, not
`AWS::Serverless::Api` (REST API). Reason: cheaper ($1 vs $3.5 per million
requests), more modern, fits our stack.

**Direct consequence:** the Lambda Authorizer can only be of type **REQUEST**.
The TOKEN type is not available for HttpApi (it is available only for REST Api) — this is a
platform limitation, not an architectural choice.

**Reversal of the week-1 decision (week 2): `IamPolicyResponse`, not HttpApi
"simple response".** Initially (week 1) `AuthorizerFunction` returned
its own record `AuthorizerSimpleResponse` (`{isAuthorized, context}`)
with `EnableSimpleResponses: true` — the HttpApi-specific simplified authorizer
contract, introduced precisely so as not to assemble the IAM
policy document/ARN by hand. The decision was right in principle at that
moment — less code, no need to construct the resource ARN by hand. But on the
first really tested protected route of week 2 (`POST
/admin/books`, the first call that went through
`BookstoreAuthorizer` at all on the deployed stack — see §5.8 below for why this was not
caught earlier) every request failed with `500` even before the backend
Lambda.

The cause is not our code but `spring-cloud-function-adapter-aws` (version
`5.0.3`, confirmed by directly analyzing the bytecode of
`AWSLambdaUtils.class` of exactly this version, not from the description in a
GitHub issue): `AWSLambdaUtils.isSupportedAWSType()` skips
wrapping the function's return value into an envelope of the form
`APIGatewayProxyResponseEvent` (`{statusCode, headers, body,
isBase64Encoded}`) **only** if the package of the return type starts with
`com.amazonaws.services.lambda.runtime.events` — a package-prefix check, not a list of
specific class names (older GitHub issues on this topic
describe exactly a list, but in this version of the adapter the logic is already generalized to
a package prefix). Any of our own types (`AuthorizerSimpleResponse`,
as well as a bare `java.util.Map` — both were checked) never
satisfies this condition, so it was **always** wrapped into this envelope. HttpApi
`EnableSimpleResponses: true` expects `{isAuthorized, context}` literally
at the top level of the Lambda response, not nested in a `body` string inside the
envelope — upon receiving the envelope instead, API Gateway treats the authorizer's response
as invalid and returns `500` without ever invoking the backend
Lambda.

**Fix:** `AuthorizerFunction` returns
`com.amazonaws.services.lambda.runtime.events.IamPolicyResponse`
(the classic policy-document contract, not "simple response") — this
class physically lies in the package `com.amazonaws.services.lambda.runtime.events`,
so it is guaranteed not to be wrapped. `template.yaml` declares
`EnableSimpleResponses: false` as the intended setting, but that value does not reach the
live authorizer by itself (see the correction below). `Resource` in the policy statement is `event.getRouteArn()` from the
incoming `APIGatewayV2CustomAuthorizerEvent` directly (a ready-made ARN from API
Gateway), not assembled by hand from route/method components.

**Why this is not "changed our mind" but a justified rollback:** the original
principle (minimum code, trust the simpler HttpApi contract) was
right in the absence of information about this limitation of
`spring-cloud-function-adapter-aws` — the limitation is discovered only
on a real call through the deployed API Gateway (see §5.8:
`sam local start-api` cannot emulate this authorizer at all), not on
`mvn test`/`sam validate --lint`/`sam build`. It duplicates the week-2 lesson from
§5.10/CLAUDE.md ("Checklist: IAM Policies of a new Lambda function (before template.yaml, not after a failed deploy)") on another
plane of the same nature: decisions made on the basis of locally
verifiable facts may run into platform/library behavior
visible only on a real deploy — this is not a reason to avoid simple solutions
in advance, but a reason to keep the first real end-to-end test of every new
path (here — "the authorizer really protects a real protected route") on
the list of mandatory checks, and not to rely on the fact that since the tests and the build
passed, everything works.

**Correction — the `Condition: null` hypothesis did not resolve the `500`.** After the
`IamPolicyResponse` switch, the very first end-to-end curl test (`GET /books`, valid token)
still failed with `500`. The serialized policy statement did carry a literal
`"Condition":null`, and the global Jackson setting below removed it from the response, but
the `500` persisted. The real cause was the server-side SAM transform silently dropping
`EnableSimpleResponses: false` from the generated OpenAPI document, so API Gateway kept
treating the authorizer as `enableSimpleResponses: true` (incident-log 2026-08-05/07).
`template.yaml` keeps `EnableSimpleResponses: false` as the documented intent, but it does
not take effect by itself: after every deploy that touches `BookstoreAuthorizer`, the manual
step `aws apigatewayv2 update-authorizer --no-enable-simple-responses` is mandatory
(CLAUDE.md, "Mandatory manual step after a deploy that touches BookstoreAuthorizer").
Separately, the authorizer's invoke permission (`AuthorizerFunctionApiPermission`) was
missing at first; it is documented only in the comment on that resource in `template.yaml`,
not in the incident-log.

**A finding about reuse of `ObjectMapper` (important in itself, not
only for the `Condition: null` change).** Analysis of the bytecode of
`ContextFunctionCatalogAutoConfiguration$JsonMapperConfiguration.jackson()`
(`spring-cloud-function-context:5.0.3`) showed: spring-cloud-function's
`JacksonMapper` (the implementation of `org.springframework.cloud.function.json.JsonMapper`,
the internal serializer for AWS Lambda input/output, separate from the
Spring bean `tools.jackson.databind.ObjectMapper` that the project's service classes
receive via DI) does **not** create a separate, uncontrolled
Jackson instance — it calls `applicationContext.getBean(ObjectMapper.class).rebuild()`,
inheriting the configuration of the already existing Spring-managed bean (with a fallback to
`JsonMapper.builder()` only if there is no `ObjectMapper` bean in the context at
all). **Consequence:** a global Jackson setting via
`spring.jackson.default-property-inclusion` (or any other setting of the
auto-configured Spring Boot `ObjectMapper` bean) also applies to the
serialization that spring-cloud-function-adapter-aws does for AWS
event/response objects — not only to the `ObjectMapper` explicitly injected into our
Function classes. This closes the risk of "different parts of the system
serializing differently, unpredictably" for future similar-situations.

**Change (kept; it did not resolve the `500`):** `src/main/resources/application.properties` —
`spring.jackson.default-property-inclusion=non_null`. The file did not
exist before (`src/main/resources` was empty). The side effect is global,
not targeted: any null field is now skipped on serialization
in **all** JSON responses of the project (our own API responses too, not
only the authorizer). Assessed as safe: a missing key and an explicit
`null` are indistinguishable to a typical JSON client, and no DTO of the project
relies on distinguishing "field is absent" vs "field is present but null".

### 5.2 Refresh tokens — journaling in DynamoDB
Refresh tokens are stored as separate items in DynamoDB (`PK=USER#<id>,
SK=REFRESH#<tokenId>`), not as a stateless JWT with `tokenVersion`.

Reason: targeted revocation of a single token/device (not a mass invalidation
of all sessions at once), groundwork for rotation-with-reuse-detection in the future.
The `expiresAt` attribute with `TimeToLiveSpecification` in `template.yaml` was created
precisely for this — automatic cleanup of expired refresh tokens via DynamoDB TTL.

A limitation that remains regardless of the choice: the **access token** is not
revoked instantly in either variant — blocking a user takes effect
on new refresh requests immediately, but on an already issued access token — only
after its TTL expires (15 minutes).

**Week-2 clarification — `role` shares the same mechanism.** `User`/`UserItem`
gained a `role` field (`USER`/`ADMIN`, week 2, Catalog/Order admin endpoints).
The role is baked into the access token as a claim at issuance (`issueAccessToken`), and is not
checked by a live read from DynamoDB in the authorizer (this preserves §5.4 —
the authorizer stays stateless). It propagates the same way as
`status`: `RefreshService.refresh()` already does a live
`userRepository.findById(...)` before issuing a new access token (to
check `status=ACTIVE`) — the same call returns the current `role`, so
a role change takes effect **on the next `/login` OR `/refresh`**, not
only on `/login`. The lag is the same as for blocking: an already issued
access token keeps the old role until its TTL expires (15 minutes).

**Week-3 flag — closed.** Blocking a user now instantly invalidates
**all** of their `REFRESH#` records, not just the next refresh request:
`AdminUserBlockFunction` does `Query(PK=USER#<id>, SK begins_with
REFRESH#)` + a per-item `UpdateItem status=REVOKED` for each record found
(`RefreshTokenRepository.revokeAllActive`, a separate Lambda with
its own narrow IAM policy — not an extension of `RefreshFunction`).

### 5.3 Auth Lambda — granularity
4 separate functions: `RegisterFunction`, `LoginFunction`, `RefreshFunction`,
`AuthorizerFunction`. Each has its own IAM execution role with minimal permissions
(Register: `PutItem`/`TransactWriteItems`; Login: `GetItem`+`PutItem`;
Refresh: `GetItem` only; Authorizer — see §5.4). Reason: the blast
radius on compromise of a single function is limited by its role, not by a shared role
for all auth operations (the principle from §1.8).

`Login`'s `PutItem` — not for the user's own profile (that one is
only read), but because `LoginService` uses the shared
`TokenService` (§5.5), which on every successful login writes a new
`RefreshTokenItem` — the same thing `RegisterFunction` already does on
registration. A shared token-issuing service implies a shared need
for `PutItem` on `REFRESH#` records, not only on the profile/email uniqueness.

### 5.4 AuthorizerFunction — access to DynamoDB
`AuthorizerFunction` only checks the signature/expiry of the access token
(stateless) — it does not read user data, does not check the `blocked` status
in real time (blocking is reflected only on new refresh requests,
see §5.2).

Its IAM role is nevertheless not "zero" with respect to DynamoDB: it needs
`dynamodb:DescribeTable` (see §5.10) — not because of its own business logic,
but because of the Spring context shared by all 16 functions (§2). The least-privilege
formulation for this function: **"no access to data (item-level
read/write)"**, not "no access to DynamoDB at all".

### 5.5 Auto-login on registration
`RegisterFunction` immediately returns access+refresh tokens, a separate call to
`/login` after registration is not needed. Basis — `docs/requirements.md`,
section "Role — user": "after which they must be redirected to the main
page of the application".

**Consequence:** `RegisterFunction` and `LoginFunction` use the shared
`TokenService` (access+refresh generation, writing the refresh token to DynamoDB)
from the `common/` package, they do not duplicate the token-issuing logic.

### 5.6 Technology stack — final versions
- Spring Boot `4.1.0`, Java `25`, Spring Cloud Function `5.0.3`
  (`spring-cloud-function-adapter-aws`).
- Jackson 3 (`tools.jackson.*`) — the Spring Boot 4 default, not Jackson 2. The code
  uses `tools.jackson.databind.ObjectMapper`.
- `jjwt-gson`, not `jjwt-jackson` — avoids a second, conflicting
  JSON stack (Jackson 2) on the classpath.
- `software.amazon.awssdk:dynamodb-enhanced:2.46.7`, `de.mkammerer:argon2-jvm:2.12`,
  `io.jsonwebtoken:jjwt-{api,impl,gson}:0.13.0`, `org.crac:crac:1.5.0`,
  `com.amazonaws:aws-lambda-java-events:3.16.1`.
- Versions were confirmed by directly checking the contents of `.pom`/`.jar` files on
  `repo1.maven.org`, not via WebSearch or search indexes (both turned out to be
  unreliable for checking the existence of artifacts — see
  `docs/incident-log.md`, "Spring Boot version: three attempts").
- Local environment: `repo.maven.apache.org` (Fastly) is unreachable from the
  working machine — a mirror to `repo1.maven.org` (Cloudflare) is configured in
  `~/.m2/settings.xml` (`mirrorOf: central`).

### 5.7 Java/Maven/SAM CLI — tooling versions
The current confirmed versions of the tooling — see
`PROGRESS.md`, section "Environment". This section is not duplicated here,
so as not to get out of sync — `PROGRESS.md` is considered the source of truth
for tool versions (this is the state of the environment, not an architectural
decision).

### 5.8 SAM template — build/deploy contracts
- `Metadata: BuildMethod: maven` is **not used** — SAM auto-detects
  Maven/Gradle by the presence of `pom.xml`/`build.gradle` (`ManifestWorkflowSelector`).
- `MAIN_CLASS: com.serhii.bookstore.BookstoreApplication` — a mandatory
  env variable in `Globals.Function.Environment.Variables`. Without it
  `spring-cloud-function-adapter-aws` cannot determine the main class:
  the SAM Java-Maven builder does not preserve the manifest that
  `spring-boot-maven-plugin`'s `repackage` goal writes.
- `PayloadFormatVersion: "1.0"` — set explicitly on `Events.Api.Properties` for
  Register/Login/Refresh: the code returns `APIGatewayProxyResponseEvent`
  (a class of format `1.0`), the contract must match explicitly, not rely on the
  SAM default (`2.0`).
- `AuthorizerPayloadFormatVersion: "2.0"` — a separate, independent
  setting of the contract of the **incoming** authorizer event
  (`APIGatewayV2CustomAuthorizerEvent`), not tied either to the format of the
  backend integration above or to the format of the authorizer's **response**
  (`EnableSimpleResponses`/`IamPolicyResponse`, see §5.1 — the input payload
  format 2.0 stays, only the output contract was turned back to
  IAM policy document).
- `Metadata.cfn-lint.config.ignore_checks: [E3030]` on every function —
  a targeted suppression of a known false positive (the `cfn-lint` schema
  lags behind the real list of Lambda runtimes; `java25` is a real
  managed runtime).
- `sam local start-api` cannot emulate `BookstoreAuthorizer`
  (an authorizer via `!Ref Function.Alias` does not resolve locally) —
  the behavior of the `DefaultAuthorizer` is verified only on a really
  deployed stack.
- **`sam local invoke` cannot get past building the Spring context
  for any function under the `<agent-profile>` profile.** `JwtProperties` is an
  unconditional `@Component` (§5.10), its constructor makes a real
  `ssm:GetParameter` on `/bookstore/jwt-signing-key`; `sam local invoke` does not
  assume the function's execution role — the call runs under the local
  profile passed via `--profile`, and `<agent-profile>` does not have this permission
  (only Lambda execution roles get it, via
  `*JwtSigningKeyReadStatement`). Confirmed empirically when implementing
  week 3 — `AccessDeniedException` on
  `OrderCreatedConsumerFunction`, unrelated to the week-3 code, applicable to
  any function. Deliberately **not** worked around by extending the IAM of
  `<agent-profile>` — the agent does not need access to the JWT signing key by its
  role, this would be a departure from least-privilege for the convenience of local
  debugging. A practical consequence, matching how weeks 1-2 were actually
  verified: `sam local invoke` is useful only up to this
  point (build/routing/dependency-injection wiring), the substantive
  verification of business logic is via `mvn test` (repositories are mocked) and
  the curl checklist on a really deployed stack, not via local invoke
  fixtures.

### 5.9 JWT signing key — delivery via SSM SDK fetch, not a dynamic reference
The `ssm-secure` CloudFormation dynamic reference is not supported in Lambda
`Environment.Variables` (the official list of supported resources does not
include `AWS::Lambda::Function`). Instead: `JwtProperties` reads the
parameter `/bookstore/jwt-signing-key` via `SsmClient.getParameter(...,
withDecryption(true))` at cold start. `template.yaml` carries only the name of the
parameter (`JWT_SIGNING_KEY_PARAM`), not the secret.

A CRaC hook for this client is not needed (unlike `DynamoDbClientHolder`):
`SsmClient` is created and closed via try-with-resources inside the
constructor, used once and closed before the constructor returns —
only the `signingKey` itself (ordinary bytes in
memory) ends up in the SnapStart snapshot, not a live connection.

Logging of the key value is ruled out: there is no logger that would print it;
`JwtProperties`/`SecretKeySpec` do not override `toString()`.
The only theoretical leak risk is wire-level/debug logging
of the AWS SDK v2 itself, which is not enabled anywhere in the project.

### 5.10 IAM: unconditional `@Component`s in the shared Spring context require their permissions from ANY function, not only the one that uses them

The general principle (not only about DescribeTable, see the week-2 clarification below):
if a bean is an unconditional `@Component`/`@Repository` (without
`@ConditionalOnProperty` and the like), and all functions use one jar with one
`@SpringBootApplication` component-scan (§6.1), then Spring brings this
bean up in the context of **every** function at cold start — including functions whose
own handler code does not call it directly. This means the IAM role
of every function must cover the permissions the bean requires already in its
constructor/initialization, not only the permissions needed by the business logic
of the specific handler.

**Instance 1 — `dynamodb:DescribeTable`.** `DynamoDbCracResource.afterRestore()`
makes a prime-call `describeTable` on SnapStart restore.
`DynamoDbCracResource`/`DynamoDbConfig` are unconditional `@Component`s, shared
by all functions — Spring brings them up in the context of every function, including
`AuthorizerFunction`, whose own handler code does not touch DynamoDB.
`SnapStart: ApplyOn: PublishedVersions` in `Globals` applies equally to
all functions. `dynamodb:DescribeTable` on `BookstoreTable` is granted to all
functions via a shared YAML anchor (`&DynamoDbDescribeTableStatement`).
`DescribeTable` gives only the table schema metadata, not access to data
(see §5.4).

**Clarification (2026-08-22) — this primer solves one task, not two, and the second
remains open.** The investigation of the `POST /orders` latency (5828 ms in
a single measurement, docs/incident-log.md 2026-08-22) revealed that the
`DynamoDbCracResource`'s `describeTable` primer above closes
exactly one of two different problems that are easy to confuse under a single
word "warm-up" — and did not try to close the second:

1. **Network/credentials re-establishment — solved correctly.** A live
   TCP/TLS connection and the scheduled refresh of the credentials provider,
   frozen into the snapshot, are meaningless after restore (a different network
   state, a different physical machine) — the `beforeCheckpoint()` code explicitly
   closes the client, `afterRestore()` builds a new one and forces credentials
   resolution through a real call. This directly matches the official AWS
   recommendation ("Re-establish network connections... in the function
   handler [or] an after-restore runtime hook",
   [Maximize Lambda SnapStart performance](https://docs.aws.amazon.com/lambda/latest/dg/snapstart-best-practices.html)) —
   this part of the primer does not need to be revisited.
2. **JIT compilation/class-loading of the first real data-plane call —
   not solved, and could not have been solved by this code in its current form.** Two
   independent causes, both confirmed by the official AWS documentation,
   not only by this project's logs:
   - **The wrong CRaC hook.** The primer runs in `afterRestore()` — that
     is **after** the snapshot is already frozen. Any JIT compilation/
     class-loading that this call produces does not get into the snapshot
     and is paid for again on **every** restore, not once at
     publish. The official AWS pattern for Java+Spring Boot+SnapStart
     ("Invoke Priming"/"Class Priming",
     [AWS Compute Blog, April 2025](https://aws.amazon.com/blogs/compute/optimizing-cold-start-performance-of-aws-lambda-using-advanced-priming-strategies-with-snapstart/))
     explicitly requires warm-up in `beforeCheckpoint()`, precisely so that the warmed-up
     state gets into the snapshot itself and is not paid for again.
   - **The wrong code path, even if the hook were right.** `DescribeTable` is a
     separate operation with its own generated marshaller/unmarshaller
     classes, different from `GetItem`/`Query`/
     `TransactWriteItems`. Warming up `DescribeTable` does not warm up the JIT/
     class-loading state for the operations that the business logic really
     performs — one needs to warm up **the same** operation that real traffic
     will call, not an operation "of the same service type".

   The order of magnitude is confirmed empirically, not by assumption: a cold
   `POST /orders` — `Duration` 4472-4527 ms with a `Restore Duration` of only
   1104-1639 ms (i.e. ~3.2-3.6 s is neither restore nor network overhead); the same
   order of magnitude on `OrderListFunction` (only `Query`, no
   `TransactWriteItems`/EventBridge) on its own cold invocations.
   It matches in scale the official AWS measurement for a similar stack
   (Java+Spring Boot+SnapStart, the same blog; figures re-checked against the blog's results
   table on 2026-10-06: 5047.94 ms and 1177.87 ms): ON_DEMAND p50 5048 ms →
   SnapStart-without-priming p50 1178 ms — that is, even after SnapStart
   a few seconds of latency remain precisely because of the un-warmed JIT/
   class-loading paths, exactly the same phenomenon that we see here.

**Why this distinction is architecturally important, not terminological nitpicking:**
the pattern (`DynamoDbCracResource`/`DynamoDbClientFactory`/`DynamoDbClientHolder`)
is shared by the functions that touch DynamoDB directly, and the mistaken
assumption "the primer already warms up the cold start for DynamoDB calls" is
not an isolated inaccuracy about one function, but a systematic overestimation of
how warmed-up the whole project is, all at once. It also determines the shape of the future
fix: "adding one more `afterRestore()` call to the primer" will not solve
the JIT/class-loading part in principle, no matter how many calls are added there —
the fix has to move to a different CRaC hook (`beforeCheckpoint()`),
which is a structurally different implementation, not a bigger version of the same one. Moreover,
a `beforeCheckpoint()` warm-up of a real data-plane operation (for example,
a real `GetItem`/`Query` against the live table during publish, rather than
only the schema metadata via `DescribeTable`) has a different profile of
privileges/side effects than the current metadata-only primer,
and this should be taken into account when designing the fix, not only when implementing it
(see also the AWS warning about the idempotency of such calls in the same
blog — "invoke priming should only be used when code executed during
priming is either idempotent or does not modify state").

**Status — deferred deliberately, by the same pattern as X-Ray (§5.15),
not forgotten.** Both considered fix options have been analyzed and not
implemented — the full comparison, scoping (7-10 sentinel calls over
specific operations, one shared `beforeCheckpoint()` method, a split
read/write strategy) and the exact numbers — docs/incident-log.md 2026-08-22
(a continuation of the same entry, not a separate incident). In brief:

- **Invoke Priming** — the right long-term fix (an official
  AWS pattern, see the links above), $0 of ongoing cost (only a
  small duration cost at publish, not on every invoke), but real
  non-trivial work, not verifiable without an actual redeploy +
  restore. Deferred, not rejected.
- **Provisioned Concurrency** — considered and **rejected outright**,
  not just deferred: incompatible with SnapStart at the function level
  (mutually exclusive settings, confirmed by the AWS docs — enabling PC
  disables the `DynamoDbCracResource` CRaC hooks for this function), does not
  speed up the JVM/Spring Boot init itself (the same order of magnitude as a
  cold start without SnapStart — it is just paid for in the background, not in front of the
  user), and at the project's actual memory size (512 MB) costs from
  ≈$5.40/month (one function) to ≈$81/month (all 15 functions that access DynamoDB data, §2) —
  it breaks the near-$0/pay-per-invocation invariant that the project has deliberately held
  from the very beginning (PROGRESS.md/CLAUDE.md), and adds the
  risk "a forgotten toggle = a silent bill during idle time between sessions".

**Concrete revisit triggers (not "someday", checkable):**
(1) before a live demo, where a noticeable
multi-second spinner on `POST /orders` would look bad; (2)
if placing an order becomes a frequently demonstrated path in a walkthrough
(not a one-off mention). Until then — the frontend loading spinners
(Stretch §7.1 item 4, all screens with loading/submit) remain an honest,
sufficient UX compensation for the purposes of a demo project: the user sees
that something is happening, rather than that the site has hung — the delay itself
remains, is not eliminated, and this is accepted deliberately, not hidden.

**Instance 2 — `ssm:GetParameter` (week 2, found on the first deploy, not
predicted in advance).** `JwtProperties` is also an unconditional `@Component`
(the constructor does `SsmClient.getParameter(..., withDecryption(true))`,
see §5.9) — so the same conditions apply: all 6 new Catalog/Order
functions bring up `JwtProperties` in their Spring context at cold start,
**even if the handler itself never calls `jwtService`** (they trust the
context from `BookstoreAuthorizer`, see `common.security.AuthorizerContext`).
The first `sam deploy` attempt for week 2 ended in `UPDATE_ROLLBACK_COMPLETE`
for exactly this reason — `AccessDeniedException` on `ssm:GetParameter` for
all 6 new functions at once (see docs/incident-log.md). Fixed:
`*JwtSigningKeyReadStatement` was added to all 6 functions in `template.yaml`,
even though their business logic does not touch JWT.

**Practical consequence for the future:** when adding a new Lambda function
to this monolithic jar — by default grant it both shared
YAML anchors (`*JwtSigningKeyReadStatement`, `*DynamoDbDescribeTableStatement`),
and do not reason "this function itself does not touch JWT/DynamoDB, so it is not
needed" — this very reasoning has failed repeatedly (see docs/incident-log.md; §6.1 notes the same
about the shared blast radius at the code level, here — the same effect at the level of
IAM). Revisit this default only if/when multi-module
decoupling happens (§6.1) — then every function will have a
genuinely narrow Spring context, and the reasoning "not needed" will become
correct again.

### 5.11 DynamoDB: explicit mapping of the PK/SK attributes
The real table (`BookstoreTable`) defines its key attributes as
`PK`/`SK` (uppercase). The DynamoDB Enhanced Client by default derives the attribute
name from the name of the bean property of the getter (`getPk()` → `"pk"`, lowercase) —
attributes in DynamoDB are case-sensitive, the default derivation does not match the
real schema.

All three DynamoDB beans of the project (`UserItem`, `EmailAccountItem`,
`RefreshTokenItem`) explicitly annotate their getters with
`@DynamoDbAttribute("PK")`/`@DynamoDbAttribute("SK")` on top of
`@DynamoDbPartitionKey`/`@DynamoDbSortKey` — they do not rely on the default
name derivation.

Condition expressions (`Expression.builder().expression(...)`) refer to
the **raw DynamoDB attribute name**, not to the Java bean property — they must be
written with the same capitalization as the real schema
(`attribute_not_exists(PK)`, not `(pk)`).

### 5.12 Error handling: logging and classification of exceptions
All Lambda functions (`RegisterFunction`/`LoginFunction`/`RefreshFunction`) and
`ApiGatewayResponseFactory` use the SLF4J logger (available transitively
via `spring-boot-starter`, no separate dependency needed):
`log.error(..., exception)` with a full stacktrace on unexpected exceptions,
`log.warn(...)` on expected `ApiException`s, `log.info(...)` before every
`return` in the success path.

`DynamoDbUserRepository.createUser()` classifies
`TransactionCanceledException` by `e.cancellationReasons()`, not unconditionally
— `DuplicateEmailException` is thrown only if the real cancellation reason is
`ConditionalCheckFailed`; any other reason is propagated as is with a
full log of the reasons.

### 5.13 Registration model — mandatory fields
Per `docs/requirements.md` ("Role — user") — the registration form
requires the name, email, password, password confirmation, date of birth and gender, all
fields mandatory.

- `RegisterRequest`: `name`, `email`, `password`, `confirmPassword`,
  `birthday` (raw wire-format `String`, parsed in the service), `gender`.
- `User` (domain) / `UserItem` (DynamoDB bean, the same item
  `PK=USER#<id>, SK=PROFILE`, without a new item type): added `name`,
  `birthday` (`LocalDate`), `gender`. `name` is **not** unique (uniqueness
  only for `email`, via `EmailAccountItem`).
- `RegisterResponse`: added `name`.

Validation: `name` non-blank; `confirmPassword` == `password` (before
hashing); `birthday` — only the ISO-8601 format (`LocalDate.parse`),
the business rule "date in the past" is deliberately deferred, not implemented;
`gender` — non-blank, with no fixed allow-list (the specification does not list the allowed
values).

### 5.14 `/refresh` — without refresh-token rotation (a deliberate week-1 decision)
`RefreshFunction` returns **only** a new access token
(`RefreshResponse(String accessToken)`), the client's original refresh token
is reused until its own expiry — `RefreshService` does not
issue a new refresh token, does not write or update
`RefreshTokenItem` (only `GetItem` to check `status=ACTIVE`, see
§5.3). Rotation-with-reuse-detection (standard practice — every
`/refresh` invalidates the old refresh token and issues a new one; reuse
of an already invalidated token is a compromise signal,
triggering a revoke of the whole "family" of tokens) is **deliberately not implemented in
week 1**, not forgotten or left unfinished — this is directly reflected in the code
(`RefreshService`/`RefreshResponse`, comments "No rotation in week 1").

Reason to defer: §5.2 already lays down refresh tokens journaled in DynamoDB
with `expiresAt`/TTL precisely as groundwork for future rotation — the infrastructure
is ready, but adding the rotation itself requires retire logic (for which
`RefreshTokenRepository` will need an `UpdateItem`/a new `save` call
inside `/refresh`, not only `GetItem`) and a decision on reuse-detection
(what exactly happens when reuse is detected — a revoke of
a single token or of the whole `USER#<id>` partition, which overlaps with the week-3 flag
in §5.2). The minimal week-1 scope — a working end-to-end auth (register →
login → refresh of the access token) — has already been achieved without this; adding
rotation now would mean solving the week-3 scope (revoking the whole partition)
ahead of time.

An explicit limitation that follows from this: a compromised refresh token
remains valid until its own `exp`, the server has no way to
detect that someone other than the legitimate client is using the same
refresh token again. Accepted as a week-1 limitation, not as an
unintended gap. Still deferred: revoke-all was done in week 3 (§5.2, as a separate
`AdminUserBlockFunction`), rotation was not.

### 5.15 X-Ray tracing — Lambda-level only, SDK subsegments deferred (week 3)

`Globals.Function.Tracing: Active` in `template.yaml`. Two things verified
against the canonical AWS docs before writing this (not assumed):

1. **`AWS::Serverless::HttpApi` has no tracing-related property at all** —
   confirmed against its full documented `Properties` list (`AccessLogSettings`,
   `Auth`, `CorsConfiguration`, `DefaultRouteSettings`, `DefinitionBody`,
   `DefinitionUri`, `Description`, `DisableExecuteApiEndpoint`, `Domain`,
   `FailOnWarnings`, `Name`, `PropagateTags`, `RouteSettings`, `StageName`,
   `StageVariables`, `Tags`) — unlike `AWS::Serverless::Api` (REST API), which
   exposes `TracingEnabled`. **Practical consequence:** "tracing across the
   chain" here means Lambda-execution segments only — there is no
   API-Gateway-originated top-level segment for the request itself, because
   the platform doesn't offer one for HttpApi. This is stated precisely, not
   glossed over.
2. **No per-function `Policies:` entry needed for the X-Ray write IAM
   permission** — the canonical `AWS::Serverless::Function` reference states
   verbatim: "If specified as `Active` or `PassThrough` and the `Role`
   property is not set, AWS SAM adds the
   `arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess` policy to the Lambda
   execution role that it creates for you." None of this project's 16
   functions sets an explicit `Role:` (all rely on SAM auto-generating the
   role from `Policies:`), so this applies project-wide automatically — unlike
   the permissions discussed in §5.10, which must be granted explicitly. This is
   confirmed here because SAM documents it, not because of the general
   component-scan blast-radius reasoning that motivates §5.10.

**SDK-call subsegments (DynamoDB/EventBridge calls showing up as X-Ray
subsegments, not just the Lambda top-level segment) — deferred, not
implemented, with a confirmed reason, not a guess.**
`aws-xray-recorder-sdk-aws-sdk-v2` (the `TracingInterceptor` needed to wire
this into `DynamoDbClientFactory`/`OrderEventPublisher`) transitively pulls
Jackson 2 (`com.fasterxml.jackson:jackson-databind`/`jackson-core`), the JSON
stack this project deliberately avoided (§5.6 — the `jjwt-gson`-over-
`jjwt-jackson` decision, to keep only Jackson 3 `tools.jackson.*` on the
classpath). Before accepting that deviation or excluding the transitive
dependency, both were tested empirically, not assumed:

- **Exclude Jackson 2 via `<exclusions>` and see if X-Ray still works** —
  tested directly with a throwaway JUnit test that built an
  `AWSXRayRecorder`, ran a real `DynamoDbClient` call through
  `TracingInterceptor`, and forced segment serialization/emission.
  **Confirmed broken**, not hypothesized: `AWSXRayRecorderBuilder.build()`
  itself throws `NoClassDefFoundError: com/fasterxml/jackson/databind/
  ObjectMapper` inside `LocalizedSamplingStrategy.<clinit>` — the recorder's
  default sampling-rules loader needs Jackson 2 just to construct itself,
  before any AWS SDK call is even attempted. The exclusion is not viable.
- Accepting Jackson 2 onto the classpath un-excluded was the remaining
  option, but was not exercised — this section documents the deferral
  instead, per the same "don't implement past a real trigger" pattern as
  §6.1-6.4.

**Concrete revisit trigger (not "someday" — one of these two, checkable):**
1. A concrete need to demonstrate the full DynamoDB/EventBridge trace chain
   (e.g., for a live-demo walkthrough of this project's observability
   story) — at that point, accept the Jackson 2 dependency un-excluded
   (confirmed structurally non-colliding: different Java package namespace
   than Jackson 3, `tools.jackson.*` vs `com.fasterxml.jackson.*`; the actual
   risk class this project cares about — see §5.1's `ObjectMapper`-reuse
   finding — is type-based bean lookup, which stays unambiguous across the
   two packages) and document that deviation explicitly, verifying via
   `mvn test` that nothing else breaks.
2. The Jackson-3 ecosystem or a future `aws-xray-recorder-sdk-aws-sdk-v2`
   release ships a Jackson-3-native (or Jackson-agnostic) serialization path,
   removing the trade-off entirely — worth a quick repo1.maven.org check
   next time this section is revisited, not assumed to still be true.

**Post-deploy update — Lambda-level recording works; the earlier negative result is unexplained.**
Real curl verification against the deployed stack (single call, an
explicit `Sampled=1` header, and a 15-request burst) found **zero X-Ray
segments recorded**, despite IAM/config/header all confirmed correct;
the cause of that result was not determined (`docs/incident-log.md`,
2026-08-18 entry). A later check on 2026-10-06 verified Lambda-level
recording: `aws xray batch-get-traces` for a `LoginFunction` trace
returned two segments, `AWS::Lambda` (HTTP 200) and
`AWS::Lambda::Function` with a visible `Restore` subsegment, and no
unprocessed trace IDs (`docs/incident-log.md`, 2026-10-06 entry). Net
effect: the native Lambda-managed path works at Lambda level. The
SDK-subsegment path stays deferred because of the Jackson 2 conflict
(see above, with its revisit triggers), and there is no API Gateway
segment because HttpApi has no tracing property. `Tracing: Active`
stays in `template.yaml` (IAM cost is zero since it's already
auto-attached).

## §6. Deferred architecture decisions

### 6.1 Multi-module Maven decoupling — deliberately deferred

**Current state:** a single Maven artifact (`bookstore-0.1.0-aws.jar`),
all 16 Lambda functions (§2) use one and the same
`CodeUri`, they differ only via `spring_cloud_function_definition`.
Each function brings up the **whole** Spring context at cold start/restore,
not only the code it actually runs.

**Why it is done this way (a deliberate choice, not a default out of ignorance):**
- A shared `TokenService`/`JwtService` without duplicating code or a separate
  shared library with its own versioning.
- One `pom.xml` — a single point of dependency management (it substantially
  simplified the migration to Spring Boot 4.1.0 / Jackson 3 / jjwt-gson in one
  pass, not 4 synchronized changes).
- Less overhead at the scale of a 3-week pet project of a single developer.

**A real drawback, seen in practice:** a bug in a shared bean
(`JwtProperties`: a multi-line signing key broke Base64 decoding) took down all 4 functions
at once at that time, despite
IAM isolation — IAM protects resources, it does not protect against the shared code-level
blast radius.

**Triggers for revisiting (do not do it preemptively, only on the facts):**
1. **Measurable** — if after adding Catalog/Order (week 2) the `INIT
   Duration` grows substantially (guideline: noticeably above the current ~28 s)
   because of the expanded `@ComponentScan` capturing unrelated
   packages (for example, `AuthorizerFunction` starts loading the beans
   `CatalogService`/`OrderService` although it does not need them).
2. **Demonstration value of the modular approach** — if after Core is finished there is time
   left in the Stretch weeks, implementing multi-module decoupling is worthwhile in itself (it
   demonstrates the trade-offs of monolithic vs modular serverless deployment).

**Trigger 1 — outcome:** not measured right after week 2. Observed later, with all 16
functions in the jar: `Init Duration` 29105 ms (`AuthorizerFunction`) and 32559 ms
(`LoginFunction`), both from full (non-snapshot) inits while SnapStart snapshots were being
re-created (incident-log 2026-10-06). Trigger not met / inconclusive: 29-32 s is a small change
from ~28 s, and these were snapshot rebuilds, not a before/after comparison. No multi-module
split was made.

**Migration plan, if/when we revisit (incrementally, not all at once):**
1. Extract `bookstore-common` (`TokenService`, `JwtService`,
   `DynamoDbClientHolder`, `DynamoDbCracResource`) into a separate Maven module,
   `mvn install` locally into `~/.m2` — without a remote repository,
   sufficient for a single developer. The rest of the code is still monolithic, but
   already depends on `common` as an explicit dependency.
2. Split `auth/` into separate modules (`bookstore-auth-register` and
   so on), each with its own minimal `pom.xml` depending on
   `bookstore-common`. Explicitly limit `@ComponentScan` to the boundaries of each
   module + `common`.
3. Update `template.yaml` — each function gets its own
   `CodeUri` instead of the shared jar.

**Known cost of the approach, if we decide to migrate:** version drift between
modules (`RegisterFunction` on `common:1.2`, `LoginFunction` still on
`common:1.1`, if someone forgot to rebuild/redeploy in sync) — at the
scale of a single developer it is managed manually; on a team — it requires
CI/CD, which would synchronize the dependency versions at build time anyway.

**Status:** do not implement without an explicit trigger from the list above.

### 6.2 Admin book listing — `Scan`, not a GSI (week 2, deliberately deferred)

`AdminBookListFunction` (`GET /admin/books`) reads the **whole** list of books
(visible and hidden — the admin must see the hidden ones, see
`docs/requirements.md`) via a `Scan` with a `FilterExpression
begins_with(PK, "BOOK#")`, not via a separate GSI.

**Why it is done this way (a deliberate choice, not a default out of ignorance):**
the only existing index (`GSI1-visible-books`) by construction
excludes hidden books — the admin list requires exactly the opposite.
Creating a second GSI for a list that at the scale of a pet project
(tens to hundreds of books) is read in a single `Scan` without pagination problems
would be premature optimization.

**Trigger for revisiting:** if the catalog really grows to the point that
`Scan` becomes measurably expensive/slow — add a `GSI2` with
`PK=CATALOG#ALL` (all books, no filter on `visible`), using the same projection
pattern that is already applied in `GSI1-visible-books`. Do not do it preemptively.

### 6.3 Order placement — sequential `GetItem`, not `BatchGetItem` (week 2, deliberately deferred)

`OrderService.placeOrder()` reads each order line (`bookId` →
current `name`/`priceCents`/`visible`) with sequential `GetItem`s, not
with a single `BatchGetItem` for all lines at once.

**Why it is done this way:** the size of one order (a cart) at the scale of a
pet project is a handful of lines, not dozens. The latency difference between
sequential `GetItem`s and a single `BatchGetItem` at such a scale is not
worth the additional complexity of the batch API (partial failures,
retry logic for unprocessed keys).

**Trigger for revisiting:** if the real usage scenario starts to
involve orders with a large number of distinct lines — move to
`BatchGetItem`. Do not do it preemptively.

**Status (6.2, 6.3):** do not implement until an explicit trigger, similarly to
6.1.

### 6.4 Atomic decrement/increment — only the low-level `DynamoDbClient`, not the Enhanced-client bean API (week 2, an architectural pattern)

`DynamoDbOrderRepository`'s decrement of the book stock (`Count`) uses the
low-level `DynamoDbClientHolder.rawClient().transactWriteItems(...)` with a
raw `UpdateExpression: SET #count = #count - :qty`, rather than
`TransactWriteItemsEnhancedRequest`/`updateItem(item)` from the
DynamoDB Enhanced Client (the same client used by all the other
repositories of the project — `DynamoDbUserRepository`, `DynamoDbBookRepository`).

**Why it is done this way (architecturally significant, not an implementation detail):**
The Enhanced-client bean API can only SET an absolute value from a
Java object — there is no way to express "attribute = attribute − N" with
bean-based `updateItem`. The naive workaround ("read the current Count,
subtract N, write the absolute value with the condition `Count >= N`")
looks correct but is not atomic: the `ConditionExpression` is checked
against the **actual server value** at the moment of the write, and not against
the value read by the client — if two concurrent orders read
Count=5 independently, both will pass the condition `Count >= 2` and both will write
the same absolute value (3), actually decrementing only 2 units instead of
4 (a lost update). Found and fixed at the implementation stage, before deploy —
the trace details are in docs/incident-log.md, if needed.

**Rule for the future:** any new counter field with the same semantics
("atomically change relative to the current value, with a condition on the current
value") — for example, any future relative counter or stock change — is implemented with
the same
pattern (`rawClient()` + raw `UpdateExpression`/`ConditionExpression`),
not via the Enhanced-client bean API. The bean API remains the right choice
for all "full replacement of an item" operations (Create/full-replace-Update), where
it is already used in all the other repositories.

### 6.5 Book not-found on update — two independent sources, a known TOCTOU limitation (week 2)

`DynamoDbBookRepository.updateBook()` determines "book not found" through
**two** separate mechanisms: (1) a preliminary `GetItem` — not found →
`BookNotFoundException` immediately, before attempting the write; (2) a conditional
`UpdateItem` (`attribute_exists(PK)`) as defense-in-depth — if the book
existed at the moment of (1), but disappeared before the commit of (2), the same exception
is still thrown via `ConditionalCheckFailedException`. The reason for
mechanism (1): the Enhanced-client `UpdateItem` with the default `ignoreNulls=false`
has no way to leave `createdAt` untouched (which is absent from the incoming request) and
at the same time correctly REMOVE `GSI1PK`/`GSI1SK` when hiding a book (§2) —
it was necessary to read the existing item to carry its `createdAt`
over into the item being written.

**Known limitation:** between (1) and (2) there is a theoretical TOCTOU window
(check-then-act without a single atomic operation). **In week 2 this is not an
active risk** — there is no path in the system by which a book could
be deleted between the two calls (delete-book does not exist as functionality).
But this is not a final decision forever: if in week 3 (or
later) an admin delete-book appears — this code will need to be revisited
(for example, move the retrieval of `createdAt` inside the same conditional
`UpdateItem`/`TransactWriteItems`, rather than as a separate preceding call,
or explicitly accept the window and document its consequences). Do not
consider the current code correct by default for any future
delete functionality — a revisit is mandatory, not optional, if such
functionality appears.

---

## §7. Stretch scope — order and decisions

> The order of implementation of the Stretch scope (§3) and the decisions made while
> refining it. The list of items does not change the Stretch scope itself — it is already
> recorded in §3, what is recorded here is the **order** and the **rationale** for the
> order, plus two specific decisions (a change of the frontend stack, a rejection of
> CQRS), made on the transition from Core to Stretch.

### 7.1 Order of implementation

1. **GitHub Actions CI/CD — first.** It provides an automatic gate (`mvn test`
   + `sam validate --lint`) for all the subsequent Stretch work — every
   following item (Recs Lambda, OpenAPI, frontend) goes through this
   pipeline from the first commit, rather than getting it retroactively after
   a volume of code unchecked by a pipeline has already accumulated.
2. **Recommendation Lambda + Bedrock — second.** It completes the growth of the
   project's API surface before the OpenAPI specification is written — if
   the spec were written first and Recs added afterwards, the spec would have to be
   rewritten or kept incomplete along the way.
3. **OpenAPI specification — third.** At this step the API surface is already
   final (Auth + Catalog + Order + Admin + Recommendations) — the spec
   turns out to be a complete snapshot, not an intermediate draft that
   would have to be returned to and completed.
4. **Vue frontend — fourth.** It is built against an already ready, finished
   OpenAPI spec, not against an API that may still change.
5. **WAF rate-based rule — no fixed place in the list.** It does not
   depend on the other Stretch items in either direction —
   it can be done at any moment of this list, including in parallel with
   something else, if that is more convenient in the course of the work.
6. **CQRS via DynamoDB Streams — not implemented, deliberately deferred.**
   The current read model via a GSI (§1.3/§2) already provides the needed functionality
   for a pet project; real CQRS with a denormalized projection via
   DynamoDB Streams would add architectural complexity without practical
   benefit at this scale. Do not confuse with §1.3 — it already records that the
   current GSI filter should not be *called* CQRS; here a separate
   decision is recorded not to *implement* real CQRS at all within this
   project. If it is needed for a live demo —
   the implementation plan is known and requires no new research: DynamoDB
   Streams → a separate Lambda projection → a denormalized read table
   of the catalog — but it is not in the current scope, do not revisit the status without
   a concrete trigger (a live demo or walkthrough where this will really be asked/needs to
   be shown), not "it would be nice".

### 7.2 Frontend stack: Vue, not React

The original wording of the Stretch scope in §3 named React as the
assumed choice for an AI-generated frontend. This changes to
**Vue** — a deliberate decision of the maintainer, not an architectural
necessity and not a technical constraint: a personal preference,
an additional goal — to show familiarity with both major
frontend frameworks, not just one. No API contract and
no backend decision in this document depends on the choice of framework
— only the wording of the Stretch item is affected, in all the places where React
used to appear.

**Status:** This section only records why the change was made; §3 remains the source of truth.