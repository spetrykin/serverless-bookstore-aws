#!/usr/bin/env bash
set -euo pipefail

# Wraps `sam deploy` with a mandatory post-deploy reconciliation step for
# BookstoreAuthorizer's EnableSimpleResponses setting.
#
# WHY THIS EXISTS (full chronicle: docs/incident-log.md; standing rule:
# CLAUDE.md "Mandatory manual step after a deploy that touches
# BookstoreAuthorizer"): the server-side AWS::Serverless-2016-10-31
# transform (aws-sam-translator, model/apigatewayv2.py) has a truthy-check
# bug — `if self.enable_simple_responses:` instead of `is not None` — that
# silently drops `EnableSimpleResponses: false` from the generated OpenAPI
# authorizer definition every time that definition is regenerated. API
# Gateway then defaults to `enableSimpleResponses: true`, which makes every
# protected route 500 before the backend Lambda is ever invoked.
#
# Confirmed via real CloudFormation stack-event history: this does NOT
# retrigger on every deploy — only on a deploy that changes the HttpApi's
# generated OpenAPI Body. That includes the obvious case (routes/Auth block
# edited in template.yaml) but ALSO an indirect case confirmed by
# scripts/cleanup-orphaned-lambda-artifacts.sh's own comments: if any
# function referenced by a route gets replaced (not just updated — e.g. on
# a Role/Policies change), its Lambda Alias ARN changes, which changes the
# Body text too, which re-triggers this. Given how often this project edits
# IAM Policies, that's a real, not theoretical, trigger surface for Week 3.
#
# Reliably detecting "did THIS deploy touch the Body" from outside
# CloudFormation isn't safe to automate (would need to diff processed
# templates before/after) — so this always asks instead of guessing.
#
# `sam deploy` itself stays a plain, visible step below — this script does
# not hide it behind an abstraction. Everything after it requires explicit
# y/n confirmation; nothing mutates without you typing y.
#
# Usage:
#   ./scripts/deploy.sh [any extra sam deploy args, e.g. --guided]
#
# Defaults match this project's dev stack (PROGRESS.md / architecture-plan.md):
# stack bookstore-dev, region eu-central-1, profile taken from $ADMIN_PROFILE (deploys are
# always manual, by the maintainer, per CLAUDE.md — never run by the agent).

STACK_NAME="bookstore-dev"
REGION="eu-central-1"
: "${ADMIN_PROFILE:?ADMIN_PROFILE is not set - export the name of your full-rights AWS CLI profile (used for sam deploy and the post-deploy authorizer check), e.g. export ADMIN_PROFILE=my-admin-profile}"
PROFILE="$ADMIN_PROFILE"

echo "=================================================================="
echo "Step 1/2: sam deploy (explicit — this is the real deploy, not hidden)"
echo "=================================================================="
echo "+ sam deploy --no-confirm-changeset --profile $PROFILE $*"
sam deploy --no-confirm-changeset --profile "$PROFILE" "$@"

echo
echo "=================================================================="
echo "Step 2/2: BookstoreAuthorizer EnableSimpleResponses reconciliation"
echo "=================================================================="
echo "template.yaml says EnableSimpleResponses: false, but the server-side"
echo "SAM transform has a confirmed bug that can silently drop that value"
echo "(see docs/incident-log.md, CLAUDE.md). This only matters if THIS"
echo "deploy touched routes, the Auth block, or any routed function's"
echo "Role/Policies — but reliably detecting that from here isn't safe, so"
echo "asking every time is the deliberate tradeoff (cheap to say no, costly"
echo "to silently skip)."
read -r -p "Check the live BookstoreAuthorizer's EnableSimpleResponses now? [y/N] " check_answer
if [[ ! "$check_answer" =~ ^[Yy]$ ]]; then
  echo "Skipped. If this deploy touched routes/Auth/function IAM, protected"
  echo "routes may 500 until this is checked — rerun this script, or check"
  echo "manually per CLAUDE.md, whenever convenient."
  exit 0
fi

API_ID=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --profile "$PROFILE" --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='HttpApiUrl'].OutputValue" --output text \
  | sed -E 's#https://([^.]+)\..*#\1#')

if [[ -z "$API_ID" || "$API_ID" == "None" ]]; then
  echo "Could not resolve the API ID from stack outputs — check manually:"
  echo "  aws cloudformation describe-stacks --stack-name $STACK_NAME --profile $PROFILE --query 'Stacks[0].Outputs'"
  exit 1
fi
echo "API ID: $API_ID"

AUTHORIZER_JSON=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" --profile "$PROFILE" --region "$REGION" \
  --query "Items[?Name=='BookstoreAuthorizer'] | [0]" --output json)

if [[ "$AUTHORIZER_JSON" == "null" || -z "$AUTHORIZER_JSON" ]]; then
  echo "No authorizer named BookstoreAuthorizer found on API $API_ID — check manually."
  exit 1
fi

AUTHORIZER_ID=$(echo "$AUTHORIZER_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['AuthorizerId'])")
IS_SIMPLE=$(echo "$AUTHORIZER_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['EnableSimpleResponses'])")

echo "AuthorizerId: $AUTHORIZER_ID"
echo "Live EnableSimpleResponses: $IS_SIMPLE"

# Comparing against "False" (capital F) here relies on `print(...)` rendering
# a Python bool via repr-ish str(), not JSON's lowercase `false` — that's
# what `python3 -c "print(...)"` actually does, not a JSON serialization.
# Fragile if this extraction ever changes (e.g. switched to `jq`, which
# would print lowercase `false`) — update this comparison to match if so.
if [[ "$IS_SIMPLE" == "False" ]]; then
  echo "OK — already false, matches template.yaml's intent. No action needed."
  exit 0
fi

echo
echo "!! Live EnableSimpleResponses is TRUE — this WILL 500 every protected"
echo "!! route (AuthorizerFunction returns IamPolicyResponse, which API"
echo "!! Gateway will reject as malformed while it expects a simple"
echo "!! response). This is the exact incident from docs/incident-log.md."
read -r -p "Run 'aws apigatewayv2 update-authorizer --no-enable-simple-responses' now? [y/N] " fix_answer
if [[ "$fix_answer" =~ ^[Yy]$ ]]; then
  aws apigatewayv2 update-authorizer --api-id "$API_ID" --authorizer-id "$AUTHORIZER_ID" \
    --no-enable-simple-responses --profile "$PROFILE" --region "$REGION"
  echo "Fixed. Verify with a real request against a protected route (e.g. GET /books)."
else
  echo "Skipped. Protected routes will 500 until this is applied — rerun"
  echo "this script, or apply the command above manually, when ready."
fi
