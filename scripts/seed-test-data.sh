#!/usr/bin/env bash
set -euo pipefail

: "${API_URL:?API_URL is not set - export the API Gateway base URL of your stack (no trailing slash), e.g. export API_URL=https://abc123.execute-api.eu-central-1.amazonaws.com}"
: "${ADMIN_PROFILE:?ADMIN_PROFILE is not set - export the name of your full-rights AWS CLI profile (needed once to promote the demo admin), e.g. export ADMIN_PROFILE=my-admin-profile}"
ADMIN_EMAIL="loadtest-admin@example.com"
ADMIN_PASSWORD="correct-horse-battery-staple"

echo "=== 1. Register the admin ==="
ADMIN_REG=$(curl -s -X POST "$API_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Load Test Admin\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\",\"confirmPassword\":\"$ADMIN_PASSWORD\",\"birthday\":\"1990-01-01\",\"gender\":\"other\"}")
ADMIN_USER_ID=$(echo "$ADMIN_REG" | python3 -c "import sys,json; print(json.load(sys.stdin)['userId'])")
echo "Admin userId: $ADMIN_USER_ID"

echo "=== 2. Promote to ADMIN (a manual action, not via the agent) ==="
aws dynamodb update-item \
  --table-name bookstore --profile "$ADMIN_PROFILE" \
  --key "{\"PK\":{\"S\":\"USER#$ADMIN_USER_ID\"},\"SK\":{\"S\":\"PROFILE\"}}" \
  --update-expression "SET #role = :role" \
  --expression-attribute-names '{"#role":"role"}' \
  --expression-attribute-values '{":role":{"S":"ADMIN"}}'

echo "=== 3. Admin login (a new token with role=ADMIN) ==="
ADMIN_LOGIN=$(curl -s -X POST "$API_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

echo "=== 4. Create 30 books ==="
BOOK_IDS=()
BOOK_TITLES=("Dune" "Foundation" "Neuromancer" "Snow Crash" "The Hobbit" "1984" "Brave New World" "Fahrenheit 451" "The Martian" "Ender's Game" "Hyperion" "The Left Hand of Darkness" "Do Androids Dream of Electric Sheep" "The Three-Body Problem" "Children of Time" "The Expanse" "Red Mars" "Blindsight" "Anathem" "The Diamond Age" "Altered Carbon" "Old Man's War" "The Forever War" "Ancillary Justice" "A Fire Upon the Deep" "The Diamond Age" "Consider Phlebas" "Excession" "The Player of Games" "Use of Weapons")

for i in "${!BOOK_TITLES[@]}"; do
  TITLE="${BOOK_TITLES[$i]}"
  PRICE=$(( (RANDOM % 3000) + 500 ))
  COUNT=$(( (RANDOM % 20) + 1 ))
  RESP=$(curl -s -X POST "$API_URL/admin/books" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$TITLE\",\"priceCents\":$PRICE,\"count\":$COUNT,\"photoUrl\":\"https://example.com/book$i.jpg\",\"visible\":true}")
  BOOK_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['bookId'])")
  BOOK_IDS+=("$BOOK_ID")
  echo "  [$((i+1))/30] $TITLE -> $BOOK_ID"
  sleep 0.3
done

echo "=== 5. Register users with orders ==="
for u in 1 2 3 4 5; do
  EMAIL="loadtest-user$u@example.com"
  REG=$(curl -s -X POST "$API_URL/register" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Load Test User $u\",\"email\":\"$EMAIL\",\"password\":\"correct-horse-battery-staple\",\"confirmPassword\":\"correct-horse-battery-staple\",\"birthday\":\"1995-0$u-0$u\",\"gender\":\"other\"}")
  USER_TOKEN=$(echo "$REG" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

  NUM_LINES=$(( (RANDOM % 3) + 3 ))
  LINES="[]"
  for l in $(seq 1 $NUM_LINES); do
    RAND_BOOK="${BOOK_IDS[$((RANDOM % ${#BOOK_IDS[@]}))]}"
    LINES=$(echo "$LINES" | python3 -c "import sys,json; l=json.load(sys.stdin); l.append({'bookId':'$RAND_BOOK','quantity':1}); print(json.dumps(l))")
  done

  ORDER=$(curl -s -X POST "$API_URL/orders" \
    -H "Authorization: Bearer $USER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"lines\":$LINES}")
  echo "  User $u ($EMAIL): order for $NUM_LINES books -> $(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('orderId','ERROR'))")"
  sleep 0.3
done

echo ""
echo "=== Done ==="
echo "Admin: $ADMIN_EMAIL / $ADMIN_PASSWORD"
echo "Users: loadtest-user{1..5}@example.com / correct-horse-battery-staple"