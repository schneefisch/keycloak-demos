#!/usr/bin/env bash
# Demonstrates the step-up authentication flow using curl.
#
# Prerequisites:
#   1. docker-compose up          (Keycloak on port 8080)
#   2. ./gradlew bootRun          (Spring Boot on port 8081)
#   3. testuser has OTP configured (see README)
#
# Usage:
#   bash scripts/test-step-up.sh
#   bash scripts/test-step-up.sh <totp-code>   # skip prompt, pass TOTP directly

set -euo pipefail

KEYCLOAK_URL="http://localhost:8080"
APP_URL="http://localhost:8081"
REALM="step-up-demo"
CLIENT_ID="step-up-demo-app"
USERNAME="testuser"
PASSWORD="password"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ── Step 1: Get a level-1 token (password only) ─────────────────────
echo -e "\n${YELLOW}=== Step 1: Get a level-1 token (password only) ===${NC}"
RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "grant_type=password")

TOKEN_L1=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
ACR=$(echo "$TOKEN_L1" | cut -d. -f2 | python3 -c "import sys,base64,json; d=sys.stdin.read().strip(); d+='='*(4-len(d)%4); print(json.loads(base64.urlsafe_b64decode(d)).get('acr','?'))")
echo -e "Token ACR: ${GREEN}$ACR${NC}"

# ── Step 2: Public endpoint (no auth needed) ────────────────────────
echo -e "\n${YELLOW}=== Step 2: Public endpoint (no auth) ===${NC}"
curl -s "$APP_URL/api/public/info" | python3 -m json.tool

# ── Step 3: User profile with level-1 token ─────────────────────────
echo -e "\n${YELLOW}=== Step 3: User profile with level-1 token ===${NC}"
curl -s -H "Authorization: Bearer $TOKEN_L1" "$APP_URL/api/user/profile" | python3 -m json.tool

# ── Step 4: Sensitive endpoint with level-1 token (expect 401) ──────
echo -e "\n${YELLOW}=== Step 4: Sensitive endpoint with level-1 token (expect 401) ===${NC}"
HTTP_CODE=$(curl -s -o /tmp/step-up-response.json -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN_L1" "$APP_URL/api/admin/sensitive")

if [ "$HTTP_CODE" = "401" ]; then
  echo -e "${RED}HTTP $HTTP_CODE — Step-up required!${NC}"
  python3 -m json.tool /tmp/step-up-response.json
else
  echo -e "${GREEN}HTTP $HTTP_CODE${NC} (unexpected — should be 401)"
  cat /tmp/step-up-response.json
fi

# ── Step 5: Get TOTP code ───────────────────────────────────────────
echo -e "\n${YELLOW}=== Step 5: Get a level-2 token (password + TOTP) ===${NC}"
if [ -n "${1:-}" ]; then
  TOTP_CODE="$1"
else
  echo -n "Enter TOTP code from your authenticator app: "
  read -r TOTP_CODE
fi

RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "totp=$TOTP_CODE" \
  -d "grant_type=password")

# Check for errors
if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if 'access_token' in d else 1)" 2>/dev/null; then
  TOKEN_L2=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
  ACR2=$(echo "$TOKEN_L2" | cut -d. -f2 | python3 -c "import sys,base64,json; d=sys.stdin.read().strip(); d+='='*(4-len(d)%4); print(json.loads(base64.urlsafe_b64decode(d)).get('acr','?'))")
  echo -e "Token ACR: ${GREEN}$ACR2${NC}"
else
  echo -e "${RED}Failed to get level-2 token:${NC}"
  echo "$RESPONSE" | python3 -m json.tool
  echo ""
  echo "Note: The direct grant flow does not support step-up natively."
  echo "Configure OTP for testuser, then use the authorization code flow"
  echo "via the browser with acr_values=mfa to get a level-2 token."
  exit 1
fi

# ── Step 6: Sensitive endpoint with level-2 token ───────────────────
echo -e "\n${YELLOW}=== Step 6: Sensitive endpoint with level-2 token ===${NC}"
HTTP_CODE=$(curl -s -o /tmp/step-up-response.json -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN_L2" "$APP_URL/api/admin/sensitive")

if [ "$HTTP_CODE" = "200" ]; then
  echo -e "${GREEN}HTTP $HTTP_CODE — Access granted!${NC}"
  python3 -m json.tool /tmp/step-up-response.json
else
  echo -e "${RED}HTTP $HTTP_CODE${NC} (unexpected — should be 200)"
  cat /tmp/step-up-response.json
fi

echo -e "\n${GREEN}Demo complete.${NC}"
