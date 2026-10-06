#!/usr/bin/env bash
# Wake every SnapStart-Inactive Lambda in the bookstore-dev stack.
#
# After ~14 days without traffic each function's `live` snapshot goes
# Inactive; the first invocation fails with SnapStartNotReadyException while
# Lambda rebuilds it (~30-60 s), and API Gateway shows that as a bare 500.
# See docs/incident-log.md 2026-10-06.
#
# Safe by construction: a function is invoked ONLY if its state is not
# Active, and an Inactive function is rejected before any handler code runs,
# so this never executes admin block/delete/order logic. Already-Active
# functions are skipped, never invoked. Run manually by the maintainer (it makes
# ~2 AWS calls per function, more than the agent's 5-call loop guardrail).
#
# Usage: scripts/wake-stack.sh [--no-wait]
set -euo pipefail

: "${AGENT_PROFILE:?AGENT_PROFILE is not set - export the name of your restricted (read/invoke) AWS CLI profile, e.g. export AGENT_PROFILE=my-agent-profile}"
PROFILE="$AGENT_PROFILE"
REGION="eu-central-1"
PREFIX="bookstore-dev-"
QUALIFIER="live"
aws_() { aws --profile "$PROFILE" --region "$REGION" "$@"; }

state_of() {
  aws_ lambda get-function-configuration --function-name "$1" \
    --qualifier "$QUALIFIER" --query State --output text
}

mapfile -t FUNCS < <(aws_ lambda list-functions \
  --query "Functions[?starts_with(FunctionName,'${PREFIX}')].FunctionName" \
  --output text | tr '\t' '\n')

echo "Found ${#FUNCS[@]} functions."
for f in "${FUNCS[@]}"; do
  s=$(state_of "$f")
  if [[ "$s" == "Active" ]]; then
    printf '  %-58s Active (skipped)\n' "$f"
    continue
  fi
  printf '  %-58s %s -> invoking to trigger restore\n' "$f" "$s"
  # Expected to fail with SnapStartNotReadyException; that failure IS the wake-up.
  aws_ lambda invoke --function-name "$f" --qualifier "$QUALIFIER" \
    --cli-binary-format raw-in-base64-out --payload '{}' /dev/null \
    >/dev/null 2>&1 || true
done

[[ "${1:-}" == "--no-wait" ]] && exit 0

echo "Waiting for all functions to become Active (up to ~10 min)..."
for _ in $(seq 1 40); do
  pending=0
  for f in "${FUNCS[@]}"; do
    [[ "$(state_of "$f")" == "Active" ]] || pending=$((pending + 1))
  done
  if [[ $pending -eq 0 ]]; then echo "All ${#FUNCS[@]} functions Active."; exit 0; fi
  echo "  $pending not yet Active..."
  sleep 15
done
echo "Timed out; re-run to continue." >&2
exit 1
