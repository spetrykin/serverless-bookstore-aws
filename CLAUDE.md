# AWS Cost & Safety Guardrails

## Profile and environment
- All AWS operations performed by the agent use the `<agent-profile>` profile (restricted permissions, deny on destructive operations).
- Deploys (`sam deploy`), cleanup (`sam delete`, deleting leftovers) and the `update-authorizer` fix are run ONLY manually by the user, under the `<admin-profile>` profile, never by the agent. Since 2026-08-08 the deploy goes through `scripts/deploy.sh` (not a bare `sam deploy` directly), unless the user explicitly decides otherwise for a specific case — see the section "Mandatory manual step after a deploy that touches BookstoreAuthorizer" below.

## Require explicit confirmation before the call
Before calling `call_aws` (or any AWS tool) for the following actions — describe the plan and wait for confirmation ("yes"/"go ahead"):
- Any resource creation that may be repeated in a loop (for example, creating a Lambda, a DynamoDB table, an EventBridge rule) — especially inside retry logic or a loop.
- Any Bedrock `InvokeModel` call in a loop more than 3 times in a row without an explicit user request to test exactly that.
- Any operation that is inherently billable per call (Bedrock, EventBridge PutEvents, high-frequency API Gateway tests).
- `dynamodb:UpdateItem` that changes the `role` field (raising/lowering a user's permissions, e.g. assigning ADMIN for testing) — always confirm before the call, separately from the loop rule above: this is privilege escalation, even if IAM formally allows it for the `<agent-profile>` profile.

## Forbidden without explicit permission in this message
- Loops that call the AWS API more than 5 times in a row without a pause to check the result.
- Load testing scripts (load testing) against real AWS resources.
- Changing IAM policies, roles, permission sets.

## If something goes wrong
- On any unexpected AWS API error — stop and report, do not try to "fix it by retrying" more than 2 times in a row.
- If more than 3 resources have been created in one session — summarize exactly what was created before continuing.

## Bedrock
- Default model for tests: Amazon Nova Micro (not Claude, not large models), unless explicitly stated otherwise.
- Before the first real Bedrock call in a session — confirm with the user that it is not a mock.

## Project architecture decisions (see docs/architecture-plan.md)
- Stock decrement is synchronous in the Order Lambda, not via EventBridge.
- The term "CQRS" is not used literally for the catalog GSI filter.
- AWS profile for the agent: <agent-profile> (not <admin-profile>).
- Region: eu-central-1.

## Session start
At the start of every new session, read `PROGRESS.md` and
`docs/architecture-plan.md` (`PROGRESS.md` must stay short — see
"Documentation rules" below) — this restores context without
having to read the chat history. Do NOT read `docs/incident-log.md`
automatically — consult it only when: (a) the user explicitly
asks for a historical reference, (b) you are debugging a bug and suspect that a
similar problem has already happened before.

## Documentation rules (mandatory on every update)

**`PROGRESS.md`** — a snapshot of the current state, not a journal. On every
update: rewrite existing lines if a fact has changed, do not
append a new line on top of the old one. Keep "Current state" within
~15-20 lines — if a fact needs more than a couple of lines of explanation, it is either an
architecture decision (→ `architecture-plan.md`) or a debugging history
(→ `incident-log.md`), not growth of `PROGRESS.md`.

**`docs/architecture-plan.md`** — evergreen decisions and their rationale
("what" and "why"), without a step-by-step chronicle of "who found what in what order".
If a new decision overrides/refines an old one — edit the existing
item in place (add a clarification or reformulate), do not add a
contradicting item next to the old one. If an item describes a specific
found-and-fixed bug with a trace/logs — it does not belong here, it goes in
`incident-log.md`, and only the resulting architectural
consequence stays here, if there is one (e.g. "IAM: DescribeTable is needed by all functions,
not only those that explicitly work with DynamoDB" — that is an architectural fact;
the full chronicle with logs and stack traces goes in incident-log).

**`docs/incident-log.md`** — an append-only chronicle of investigations. A new
entry for every found-and-fixed bug, in the existing format
(Symptom/Cause/Fix/Why it wasn't caught earlier). Do not edit
old entries, only add new ones.

**Periodic check (once per development week, no more often):** if
`PROGRESS.md` or `architecture-plan.md` start growing beyond
reasonable again (~50-75 lines for PROGRESS.md, rapid growth of architecture-plan.md
due to details that should have been moved to incident-log) — propose a
restructuring to the user, similar to this one, do not wait for an explicit request.

## Git
- Make single logical commits. Show the staged diff before each commit and wait for the
  user's confirmation.
- Write the commit message to a file and commit with `git commit -F <file>`; never use `-m`
  with backticks (shell command substitution silently corrupts the message).
- End every commit message with `Co-Authored-By: Claude <noreply@anthropic.com>` as the last
  line, after one blank line.
- Never push or force-push without explicit confirmation. Never use `git add .` — stage
  explicit paths.

## AWS CLI
Always use `--profile <agent-profile>` explicitly in every AWS CLI command run by the agent
(including calls through call_aws), do not rely on the AWS_PROFILE
environment variable — it may not be set in a new terminal session. Deploys, cleanup and
`update-authorizer` are not agent commands: the user runs them manually under
`<admin-profile>` (see "Profile and environment").

## Cross-check before decisions
When planning any component — not only at the start of a session — cross-check
against docs/requirements.md (business requirements), docs/architecture-plan.md
(architecture decisions) and template.yaml (AWS resources that already exist,
including the API type — HttpApi, not REST Api). If you propose a choice
between options — first check whether one of them contradicts already
existing code/configuration or a recorded decision.

## Checklist: IAM Policies of a new Lambda function (before template.yaml, not after a failed deploy)

A single jar + one `@SpringBootApplication` component-scan
(architecture-plan.md §6.1) means: every function at cold start
brings up **all** unconditional `@Component`/`@Repository`/`@Service` beans
from `common/` (and from any other package touched by the component-scan) —
not only those its handler explicitly calls. The reasoning "this function does not
touch X, so IAM for X is not needed" is systematically wrong for such a monolithic
Spring context — it has failed in practice repeatedly (five documented cases, see docs/incident-log.md)
(`dynamodb:DescribeTable` for `DynamoDbCracResource`
on SnapStart restore; `dynamodb:PutItem` through the shared `TokenService` in
`LoginFunction`, which itself does not write a profile; `ssm:GetParameter` for
`JwtProperties` in all 6 week-2 functions — none of them decodes a JWT
itself; `dynamodb:DeleteItem` for `AdminUserDeleteFunction` (the `TransactWriteItems`
wrapper action does not cover the per-item actions); `dynamodb:Query` on the GSI index ARN
for `RecommendationFunction`; full history — docs/incident-log.md, architecture-plan.md §5.10).

Before fixing the `Policies:` of a new function in `template.yaml`
(**before** `sam build`/`sam deploy`, not after the first failed deploy):
1. List **all** unconditional beans that will actually end up in the
   Spring context of this function — not only what the handler/service
   it calls uses directly.
2. For each — check what happens in its constructor/
   `@PostConstruct` (not only in the business methods that this
   particular function calls): which AWS calls are made already at the stage of
   bean creation.
3. Combine the resulting list of required IAM permissions and compare it with
   the draft `Policies` of the new function.
4. Default, while the project stays monolithic (architecture-plan.md §6.1,
   not migrated to multi-module): grant both existing shared
   YAML anchors (`*JwtSigningKeyReadStatement`,
   `*DynamoDbDescribeTableStatement`) to the new function, rather than reasoning
   case by case "this function does not touch it — not needed". Revisit this default
   only if/when multi-module decoupling happens — then every
   function will have a genuinely narrow Spring context.

## Cleanup after deploy
`sam delete` removes the CloudFormation stack (Lambda functions, roles, the DynamoDB
table, HttpApi), but does NOT remove:
1. The SSM parameter `/bookstore/jwt-signing-key` — it was created manually, not part of the
   stack. Removed separately: `aws ssm delete-parameter --name
   /bookstore/jwt-signing-key --profile <admin-profile>`.
2. The managed S3 bucket (`aws-sam-cli-managed-default-samclisourcebucket-*`) —
   SAM creates it automatically for deploy artifacts, and it lives between
   deploys on purpose. Do NOT delete between work sessions — only when
   the project is fully closed.
3. CloudWatch Log Groups (`/aws/lambda/<FunctionName>`) — they are not removed
   together with the stack, this is a known CloudFormation/SAM peculiarity.
   Check: `aws logs describe-log-groups --log-group-name-prefix
   /aws/lambda/bookstore-dev --profile <admin-profile>`.

Policy: do NOT delete the whole stack between regular work sessions —
pay-per-use resources (Lambda/DynamoDB/API Gateway) cost almost nothing
when idle. `sam delete` — only on a deliberate decision of the user
(a long break, full completion of a week/stage), not as a routine step
after every testing session. The agent does not delete resources on its own
under any circumstances — this is an administrative action, always done manually
by the user, like sam deploy. After a long idle period (more than 14 days, when SnapStart
snapshots go `Inactive`), run `scripts/wake-stack.sh`; do not send real requests to the admin
or order routes to wake functions, since that would run real mutations.

## Mandatory manual step after a deploy that touches BookstoreAuthorizer

`template.yaml` carries `EnableSimpleResponses: false` on `BookstoreAuthorizer`
as a documented intent — but the server-side transform
`AWS::Serverless-2016-10-31` (not our code, not the local SAM CLI —
a CloudFormation-hosted macro) contains a truthy-check bug
(`if self.enable_simple_responses:` instead of `is not None` in
`aws-sam-translator`, `model/apigatewayv2.py`) that **silently drops**
`EnableSimpleResponses: false` every time the authorizer's
OpenAPI description is rebuilt. API Gateway then by default treats the
missing key as `enableSimpleResponses: true` — exactly
the opposite of what `template.yaml` says. The full chronicle of the
finding — docs/incident-log.md, the architectural explanation —
architecture-plan.md §5.1.

**What this breaks if not fixed manually:** `AuthorizerFunction`
returns `IamPolicyResponse` (the classic policy-document contract) —
but as long as `enableSimpleResponses` on the live resource is `true`, API Gateway
expects the simple-response format (`{isAuthorized, context}`), does not find
`isAuthorized` in the response and returns `500` to the client on **any** protected
route, without ever invoking the backend Lambda.

**When exactly this triggers (confirmed from the real stack
events history, not an assumption):** not on every `sam deploy` at all — only
when the deploy changes the generated OpenAPI `Body` of the
`BookstoreHttpApi` resource. This includes: (a) a direct change of the `Auth:`/
`Authorizers:` block or routes in `template.yaml`; (b) indirectly —
a replacement (not an update) of any Lambda function referenced by a route,
for example when its `Role`/`Policies` change (see
`scripts/cleanup-orphaned-lambda-artifacts.sh` — the same mechanism is already
documented there for orphaned resources). Deploys that touch neither
(for example, only a new CloudWatch Log Group) do not touch the Body —
the manual step is not needed in that case, but it is cheaper to ask once too often
than to forget once.

**Deploy — via `scripts/deploy.sh`, not a bare `sam deploy`** (see also
the top part of the file, "Profile and environment"). The script: (1) runs
`sam deploy` as an explicit, visible step — nothing is hidden behind an abstraction;
(2) after a successful deploy asks `y/n` whether to check the current
`EnableSimpleResponses` on the live authorizer; (3) if it turned out to be
`true` — separately asks `y/n` before the actual `update-authorizer`
call. Nothing is mutated automatically. If the user for some
reason calls `sam deploy` directly, bypassing the script — the same manual
check below is still relevant as before:

```bash
aws apigatewayv2 get-authorizers --api-id <api-id> --profile <admin-profile> \
  --query "Items[?Name=='BookstoreAuthorizer'].{Id:AuthorizerId,Simple:EnableSimpleResponses}"
# if Simple: true — fix it:
aws apigatewayv2 update-authorizer --api-id <api-id> --authorizer-id <authorizer-id> \
  --no-enable-simple-responses --profile <admin-profile>
```

`<api-id>` — the value of the `HttpApiUrl` domain from the stack `Outputs` (the subdomain
before `.execute-api...`). **Always** check the actual state, do not
assume that the previous manual fix survived a new deploy —
every deploy that touches `BookstoreAuthorizer` runs the same transform bug again.

## Full cleanup when closing the project

This is NOT a routine procedure between sessions (see "Cleanup after deploy" above —
the policy there is "do not delete between sessions"). This is a separate scenario: the project
is closed completely (the goal is reached, a decision was made to stop work, or
a long break for an indefinite period). Performed only on an explicit
decision of the user, the agent does not initiate or perform it on its own.

### 1. AWS resources — CloudFormation stack

```bash
sam delete --stack-name bookstore-dev --profile <admin-profile>
```

It will confirm the deletion and ask about the managed S3 bucket separately (see below).

The stack at the time of this edit (Stretch §7.1 item 2, Recommendation Lambda +
Bedrock — see PROGRESS.md) includes **16 Lambda functions**:
Register/Login/Refresh/Authorizer, CatalogList,
AdminBookList/Create/Update, OrderCreate/List, OrderCreatedConsumer,
AdminUserList/Block/Unblock/Delete, Recommendation (see template.yaml —
the source of truth for the exact list, do not rewrite the number here manually
on future changes, check against the actual state; 16 `Type: AWS::Serverless::Function`
resources at the time of writing).
The EventBridge rule + Lambda permission for `OrderCreatedConsumerFunction`
(auto-generated by the SAM construct `Events: OrderCreated: Type:
EventBridgeRule`, confirmed on a real deploy) — are deleted together with the stack as ordinary CloudFormation resources,
nothing needs to be done separately. **The default event bus itself is not
deleted** — it is a permanent account/region-level AWS resource, not ours and
not created by the stack; only our rule (`OrderCreated`) and the
resource-based permission on it are deleted, not the bus itself.

### 2. AWS resources — outside the stack (sam delete does not touch)

**SSM parameter:**
```bash
aws ssm delete-parameter --name /bookstore/jwt-signing-key --profile <admin-profile>
```

**Managed S3 bucket** (SAM deploy artifacts):
```bash
aws s3 rb s3://aws-sam-cli-managed-default-samclisourcebucket-<suffix> --force --profile <admin-profile>
```

**CloudWatch Log Groups for Lambda functions** (not deleted together with the
stack — Lambda creates them implicitly on first invocation, this is not a
CloudFormation resource, so `sam delete` does not see them):
```bash
aws logs describe-log-groups --log-group-name-prefix /aws/lambda/bookstore-dev --profile <admin-profile>
# for each one found:
aws logs delete-log-group --log-group-name <name> --profile <admin-profile>
```

**`HttpApiAccessLogGroup`/`HttpApiAccessLogGroupPolicy` — the opposite
case, deleted automatically.** Unlike the Lambda logs above, these are
explicit `AWS::Logs::LogGroup`/`AWS::Logs::ResourcePolicy` resources in `template.yaml`
itself — CloudFormation deletes them together with the stack, no separate
step is required. Do not confuse two CloudWatch Logs resources that look similar
but behave oppositely on `sam delete`.

**DynamoDB — idempotency guard records (`PK=EVENT#<orderId>`).**
They need no separate cleanup — they live in the same `BookstoreTable`
(single-table design) as all the other items of the project (users,
books, orders, refresh tokens); they are deleted automatically together with
`AWS::DynamoDB::Table` on `sam delete`, like everything else in the
table.

**X-Ray — implemented (week 3), confirmed against the actual template.yaml.**
`Globals.Function` carries `Tracing: Active` (confirmed in template.yaml).
X-Ray trace segments are temporary AWS-managed
telemetry data, not a resource that we create/store; the needed IAM permission
(`AWSXrayWriteOnlyAccess`, auto-attached by SAM to the execution role of every
function with `Tracing: Active`, not a manual `Policies:` statement —
architecture-plan.md §5.15) is deleted together with the role on `sam delete`, like
any other permission on it. Confirmed in practice: no separate
cleanup item turned out to be needed. Separately from cleanup, not related to this
section: Lambda-level tracing verified 2026-10-06; SDK subsegments deferred
(docs/incident-log.md 2026-08-18 and 2026-10-06; architecture-plan.md §5.15).

**Bedrock (`RecommendationFunction`, Stretch §7.1 item 2) — confirmed against
the actual template.yaml, 2026-08-20.** `bedrock:InvokeModel` is an ordinary
`Policies:` statement on `RecommendationFunctionRole` (SAM
auto-generates the role from `Policies:`, as for all the other functions),
not a separate AWS resource — it is deleted together with the stack/role on `sam
delete`, like any other `Policies:` statement. Explicitly checked: there is not
a single `AWS::Bedrock::*` resource in template.yaml — the stack does not create or
own any Bedrock resource; Nova Micro and its Geo (EU)
inference profile (`eu.amazon.nova-micro-v1:0`) are AWS-managed, they exist
independently of our stack, we provision nothing of our own there.
`BedrockRecommendationEngine` is a conditional Spring bean
(`RECS_MODE=bedrock` only, the default on the live stack is `mock`): with the
default `RECS_MODE=mock` it is not constructed at all (confirmed by a test
against a real Spring context), it creates nothing even
temporarily; with `RECS_MODE=bedrock` it creates only an SDK client
(`BedrockRuntimeClient`) in the memory of the Lambda container — not an AWS resource,
it disappears together with the container/function. It requires no separate cleanup in
any state of `RECS_MODE`.

**Verifying that nothing is left** (a final check across the whole account,
not only by the expected names):
```bash
aws cloudformation list-stacks --profile <admin-profile> --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE
aws lambda list-functions --profile <admin-profile> --region eu-central-1
aws dynamodb list-tables --profile <admin-profile> --region eu-central-1
aws apigatewayv2 get-apis --profile <admin-profile> --region eu-central-1
aws events list-rules --event-bus-name default --profile <admin-profile> --region eu-central-1
```
`lambda list-functions` — the expected number of functions before cleanup: 16
(see §1 above, check against template.yaml at the time of reading, not against this
number manually). `events list-rules` — confirms that the `OrderCreated` rule
(and its resource-based permission on
`OrderCreatedConsumerFunction`) really disappeared together with the stack, and is not
just "supposed to be gone" by assumption. The default bus itself will not disappear from the
output (see above) — what matters is the absence of our rule specifically in the
list, not an empty output of the whole command.

If something unexpected is left — do not delete blindly, figure out what
it is and where it came from before deleting.

### 3. Billing — after resource cleanup

- Check AWS Budgets — lower the threshold or delete the Budget if the account
  is no longer in use.
- Check Cost Explorer 24-48 hours after cleanup — billing is not
  instantaneous, the final charges may appear with a delay.
- If the account was created specifically for this project and is no longer needed
  at all — consider closing the AWS
  account itself via the Billing Console (Account → Close Account), not only
  cleaning up the resources inside it. This is a separate, irreversible action,
  requires separate explicit confirmation, and is not directly related to this instruction.

### 4. Local environment (optional, not mandatory)

- `~/.m2/settings.xml` — the central→repo1.maven.org mirror can be left in place,
  it does not interfere with other projects, harmless.
- `~/.aws/config` — the profiles `<admin-profile>`/`<agent-profile>` can be left
  or removed manually from the file if the account is closed completely.
- IAM Identity Center permission sets (`AdministratorAccess`,
  `AgentScopedAccess`) — deleted automatically if the AWS account itself is closed;
  if the account stays but the project is closed — the permission sets can be
  left for future projects or deleted manually via the
  console.

### 5. Final step

Update `PROGRESS.md`: mark the project as completed/suspended,
with the date and a short summary (what was done, where it stopped) — in case
the work resumes in months rather than weeks.
