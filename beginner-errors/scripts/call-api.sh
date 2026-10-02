#!/usr/bin/env bash
# Error 5 (401 from the API): calls GET /api/hello with different tokens and prints
# the status and the WWW-Authenticate header.
# Tokens come from the env vars ACCESS_TOKEN / ID_TOKEN or are asked for.
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox call-api.sh "$@"

# The toolbox shares Keycloak's network, so the API is reached by its service name
API=http://api:8081/api/hello

call() {
  local label=$1 token=${2:-}
  local auth=() shown=""
  if [ -n "$token" ]; then
    auth=(-H "Authorization: Bearer $token")
    shown=" -H 'Authorization: Bearer $(short "$token")'"
  fi
  echo
  echo "=== $label ==="
  echo "\$ curl -i$shown $API"
  local headers body status
  headers=$(mktemp)
  body=$(mktemp)
  status=$(curl -s -D "$headers" -o "$body" -w '%{http_code}' "${auth[@]}" "$API")
  echo "HTTP $status"
  grep -i '^www-authenticate:' "$headers" | tr -d '\r' || echo "(no WWW-Authenticate header)"
  [ -s "$body" ] && { cat "$body"; echo; }
  rm -f "$headers" "$body"
}

case_no_token() { call "No token"; }

case_malformed() { call "Malformed token" "not-a-jwt"; }

case_valid() {
  require_token ACCESS_TOKEN "access token"
  call "Access token" "$ACCESS_TOKEN"
}

case_id_token() {
  require_token ID_TOKEN "ID token"
  call "ID token instead of the access token" "$ID_TOKEN"
}

case_no_audience() {
  require_token ACCESS_TOKEN "access token"
  if jwt_payload "$ACCESS_TOKEN" | jq -e '[.aud] | flatten | index("demo-api")' >/dev/null; then
    echo "Note: this token still contains aud demo-api. Run scripts/break.sh 5, log out and in again, copy a new token."
  fi
  call "Access token without the demo-api audience" "$ACCESS_TOKEN"
}

case_expired() {
  require_token ACCESS_TOKEN "access token"
  local exp now wait
  exp=$(jwt_payload "$ACCESS_TOKEN" | jq -r .exp)
  now=$(date +%s)
  # Spring Security accepts tokens up to 60 seconds after exp (clock skew)
  wait=$((exp + 61 - now))
  if [ "$wait" -gt 0 ]; then
    echo "Token expires at $(jq -rn "$exp | todate"); Spring allows 60 s clock skew. Waiting ${wait} s..."
    while [ "$wait" -gt 0 ]; do
      sleep $((wait < 30 ? wait : 30))
      wait=$((exp + 61 - $(date +%s)))
      [ "$wait" -gt 0 ] && echo "  ${wait} s left"
    done
  fi
  call "Expired access token" "$ACCESS_TOKEN"
}

case "${1:-all}" in
  no-token) case_no_token ;;
  malformed) case_malformed ;;
  valid) case_valid ;;
  id-token) case_id_token ;;
  no-audience) case_no_audience ;;
  expired) case_expired ;;
  all)
    case_no_token
    case_malformed
    case_valid
    case_id_token
    ;;
  *)
    echo "Usage: scripts/call-api.sh [all|no-token|malformed|valid|id-token|no-audience|expired]"
    exit 1
    ;;
esac
