#!/usr/bin/env bash
set -euo pipefail

# Cleanup orphaned Lambda versions and CloudWatch Log Groups left behind by
# repeated `sam deploy` during active debugging. Dry-run by default — prints
# what would be deleted without touching anything. Pass --apply to actually
# delete (after typing "yes" at the confirmation prompt, or pass --yes too
# to skip the prompt for scripted use).
#
# Usage:
#   ./cleanup-orphaned-lambda-artifacts.sh [--stack-name NAME] [--region REGION] [--profile PROFILE] [--apply] [--yes]
#
# Defaults match this project's dev stack (see docs/architecture-plan.md /
# PROGRESS.md): stack bookstore-dev, region eu-central-1, profile from $ADMIN_PROFILE
# or --profile (deletions require the admin profile — the restricted agent profile
# is denied destructive actions by design, see CLAUDE.md).
#
# What counts as orphaned:
#   - A Lambda version that isn't $LATEST and isn't referenced by any alias
#     (not just "live" — protects against future aliases too).
#   - A CloudWatch Log Group under /aws/lambda/<stack-name>* whose name
#     doesn't match any function currently in the stack (left behind when
#     CloudFormation replaces a function's physical resource, e.g. on a
#     Role/Policies change).
#
# Function names are resolved via CloudFormation (list-stack-resources), not
# guessed from a name prefix — so this can't accidentally touch a function
# outside this stack that happens to share a prefix.

STACK_NAME="bookstore-dev"
REGION="eu-central-1"
PROFILE="${ADMIN_PROFILE:-}"
APPLY=false
SKIP_CONFIRM=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack-name) STACK_NAME="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --apply) APPLY=true; shift ;;
    --yes) SKIP_CONFIRM=true; shift ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^#!\?//' | sed 's/^ //'
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

: "${PROFILE:?No AWS profile - set ADMIN_PROFILE (e.g. export ADMIN_PROFILE=my-admin-profile) or pass --profile NAME}"
AWS=(aws --profile "$PROFILE" --region "$REGION")

echo "== Stack: $STACK_NAME | Region: $REGION | Profile: $PROFILE | Mode: $([[ "$APPLY" == true ]] && echo APPLY || echo DRY-RUN) =="
echo

# --- Resolve the actual Lambda function names that belong to this stack ---
mapfile -t FUNCTION_NAMES < <("${AWS[@]}" cloudformation list-stack-resources \
  --stack-name "$STACK_NAME" \
  --query "StackResourceSummaries[?ResourceType=='AWS::Lambda::Function'].PhysicalResourceId" \
  --output text | tr '\t' '\n')

if [[ ${#FUNCTION_NAMES[@]} -eq 0 ]]; then
  echo "No Lambda functions found in stack '$STACK_NAME' — nothing to do." >&2
  exit 0
fi

echo "Functions in stack:"
printf '  - %s\n' "${FUNCTION_NAMES[@]}"
echo

declare -a VERSION_DELETE_PLAN=()   # entries: "functionName version"
declare -a LOGGROUP_DELETE_PLAN=()

# --- Plan: orphaned Lambda versions ---
for fn in "${FUNCTION_NAMES[@]}"; do
  mapfile -t PROTECTED_VERSIONS < <("${AWS[@]}" lambda list-aliases \
    --function-name "$fn" \
    --query 'Aliases[].FunctionVersion' --output text | tr '\t' '\n')

  # Not paginated (default page size 50) — deliberately, for the current scale
  # of the project: one function accumulates a handful to a few dozen versions per session
  # of active debugging, not hundreds. The same pattern as Scan/sequential-GetItem
  # in week 2 (architecture-plan.md §6.2/§6.3) — if there are ever more than
  # 50 versions, add a --starting-token/NextMarker loop, not now.
  mapfile -t ALL_VERSIONS < <("${AWS[@]}" lambda list-versions-by-function \
    --function-name "$fn" \
    --query 'Versions[].Version' --output text | tr '\t' '\n')

  for v in "${ALL_VERSIONS[@]}"; do
    [[ "$v" == '$LATEST' ]] && continue
    protected=false
    for p in "${PROTECTED_VERSIONS[@]}"; do
      [[ "$v" == "$p" ]] && protected=true && break
    done
    [[ "$protected" == false ]] && VERSION_DELETE_PLAN+=("$fn $v")
  done
done

# --- Plan: orphaned CloudWatch Log Groups ---
mapfile -t ALL_LOG_GROUPS < <("${AWS[@]}" logs describe-log-groups \
  --log-group-name-prefix "/aws/lambda/${STACK_NAME}" \
  --query 'logGroups[].logGroupName' --output text | tr '\t' '\n')

for lg in "${ALL_LOG_GROUPS[@]}"; do
  fn_name="${lg#/aws/lambda/}"
  known=false
  for fn in "${FUNCTION_NAMES[@]}"; do
    [[ "$fn_name" == "$fn" ]] && known=true && break
  done
  [[ "$known" == false ]] && LOGGROUP_DELETE_PLAN+=("$lg")
done

# --- Report ---
echo "Orphaned Lambda versions (not \$LATEST, not referenced by any alias):"
if [[ ${#VERSION_DELETE_PLAN[@]} -eq 0 ]]; then
  echo "  none"
else
  printf '  - %s\n' "${VERSION_DELETE_PLAN[@]}"
fi
echo

echo "Orphaned CloudWatch Log Groups (no matching live function):"
if [[ ${#LOGGROUP_DELETE_PLAN[@]} -eq 0 ]]; then
  echo "  none"
else
  printf '  - %s\n' "${LOGGROUP_DELETE_PLAN[@]}"
fi
echo

if [[ ${#VERSION_DELETE_PLAN[@]} -eq 0 && ${#LOGGROUP_DELETE_PLAN[@]} -eq 0 ]]; then
  echo "Nothing to clean up."
  exit 0
fi

if [[ "$APPLY" == false ]]; then
  echo "Dry-run only — no changes made. Re-run with --apply to delete the above."
  exit 0
fi

if [[ "$SKIP_CONFIRM" == false ]]; then
  read -r -p "Type 'yes' to delete the ${#VERSION_DELETE_PLAN[@]} version(s) and ${#LOGGROUP_DELETE_PLAN[@]} log group(s) above: " CONFIRM
  if [[ "$CONFIRM" != "yes" ]]; then
    echo "Aborted — nothing deleted."
    exit 1
  fi
fi

for entry in "${VERSION_DELETE_PLAN[@]}"; do
  fn="${entry% *}"
  v="${entry#* }"
  echo "Deleting version: $fn:$v"
  "${AWS[@]}" lambda delete-function --function-name "$fn" --qualifier "$v"
done

for lg in "${LOGGROUP_DELETE_PLAN[@]}"; do
  echo "Deleting log group: $lg"
  "${AWS[@]}" logs delete-log-group --log-group-name "$lg"
done

echo
echo "Done."
