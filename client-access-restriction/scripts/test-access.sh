#!/usr/bin/env bash
# Tests that the custom authentication flow blocks/allows token issuance
# based on the grafana-access role.
#
# Prerequisites: docker-compose up (Keycloak must be healthy)

set -euo pipefail

KC_URL="http://localhost:8080"
REALM="app-access-demo"
CLIENT_ID="grafana"
CLIENT_SECRET="grafana-secret"
TOKEN_URL="${KC_URL}/realms/${REALM}/protocol/openid-connect/token"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}PASS${NC} $1"; }
fail() { echo -e "${RED}FAIL${NC} $1"; EXIT_CODE=1; }
info() { echo -e "${YELLOW}INFO${NC} $1"; }

EXIT_CODE=0

# ---------------------------------------------------------------------------
# Wait for Keycloak
# ---------------------------------------------------------------------------
info "Waiting for Keycloak to be ready..."
for i in $(seq 1 30); do
  if curl -sf "${KC_URL}/realms/${REALM}/.well-known/openid-configuration" > /dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -sf "${KC_URL}/realms/${REALM}/.well-known/openid-configuration" > /dev/null 2>&1; then
  fail "Keycloak did not become ready in time"
  exit 1
fi
info "Keycloak is ready"

echo ""
echo "=========================================="
echo " Test 1: alice (has grafana-access role)"
echo "=========================================="

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${TOKEN_URL}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "username=alice" \
  -d "password=password" \
  -d "grant_type=password")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
  pass "alice received a token (HTTP ${HTTP_CODE})"
  # Decode and show the groups claim
  ACCESS_TOKEN=$(echo "$BODY" | jq -r '.access_token')
  if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
    PAYLOAD=$(echo "$ACCESS_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null || true)
    GROUPS=$(echo "$PAYLOAD" | jq -r '.groups // empty' 2>/dev/null || true)
    if [ -n "$GROUPS" ]; then
      info "Token groups claim: ${GROUPS}"
    fi
  fi
else
  fail "alice was denied a token (HTTP ${HTTP_CODE})"
fi

echo ""
echo "=========================================="
echo " Test 2: bob (no grafana-access role)"
echo "=========================================="

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${TOKEN_URL}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "username=bob" \
  -d "password=password" \
  -d "grant_type=password")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
  pass "bob was blocked by Keycloak (HTTP ${HTTP_CODE})"
else
  fail "bob was NOT blocked (HTTP ${HTTP_CODE}) — expected 401 or 403"
fi

echo ""
echo "=========================================="
echo " Browser Test (manual)"
echo "=========================================="
echo ""
info "Open http://localhost:3000 in your browser"
info "Click 'Sign in with Keycloak'"
echo ""
info "Test with alice / password  -> expected: Grafana dashboard"
info "Test with bob   / password  -> expected: Keycloak error page (Access denied)"
echo ""

exit $EXIT_CODE
