#!/usr/bin/env bash
# Error 4 (invalid_grant): exchanges authorization codes of the public client cli-demo by hand.
# Every case needs a fresh code: Keycloak burns a code on the first exchange attempt, even a failed one.
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox code-exchange.sh "$@"

OIDC=http://localhost:8080/realms/demo/protocol/openid-connect
CLIENT_ID=cli-demo
REDIRECT_URI=http://localhost:9999/callback

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# Prints an authorization URL with a new PKCE pair and reads the code back from the user
get_code() {
  VERIFIER=$(openssl rand 48 | b64url)
  local challenge state input
  challenge=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | b64url)
  state=$(openssl rand 12 | b64url)
  echo
  echo "Open this URL in your browser and log in as alice:"
  echo
  echo "  $OIDC/auth?client_id=$CLIENT_ID&response_type=code&scope=openid&redirect_uri=http%3A%2F%2Flocalhost%3A9999%2Fcallback&state=$state&code_challenge=$challenge&code_challenge_method=S256"
  echo
  echo "The browser then fails to load localhost:9999. That's expected."
  echo "Paste the URL from the address bar (or only the code) and press Enter:"
  read -r input
  if [[ $input == *error=* ]]; then
    echo "Keycloak returned an error instead of a code: ${input#*\?}"
    exit 1
  fi
  if [[ $input == *code=* ]]; then
    input=$(sed -E 's/.*[?&#]code=([^&#]+).*/\1/' <<< "$input")
  fi
  CODE=$input
}

# Token request; prints the equivalent curl command, the status and the body (tokens shortened)
token_request() {
  local shown=() args=() name value
  while [ $# -gt 0 ]; do
    name=${1%%=*} value=${1#*=}
    args+=(--data-urlencode "$name=$value")
    case $name in
      code | code_verifier | refresh_token) shown+=("-d $name=$(short "$value")") ;;
      *) shown+=("-d $name=$value") ;;
    esac
    shift
  done
  echo
  echo "\$ curl -s -X POST $OIDC/token ${shown[*]}"
  local response status body
  response=$(curl -s -w '\n%{http_code}' -X POST "$OIDC/token" "${args[@]}")
  status=${response##*$'\n'}
  body=${response%$'\n'*}
  echo "HTTP $status"
  jq 'with_entries(if (.key | test("token$")) then .value |= (.[0:12] + "…") else . end)' <<< "$body"
  LAST_BODY=$body
}

exchange() {
  token_request grant_type=authorization_code client_id=$CLIENT_ID "code=$1" "code_verifier=$2" "redirect_uri=$3"
}

case_reuse() {
  echo "=== Same code twice: expect \"Code not valid\" on the second exchange ==="
  get_code
  exchange "$CODE" "$VERIFIER" "$REDIRECT_URI"
  local refresh_token
  refresh_token=$(jq -r '.refresh_token // empty' <<< "$LAST_BODY")
  exchange "$CODE" "$VERIFIER" "$REDIRECT_URI"
  if [ -n "$refresh_token" ]; then
    echo
    echo "--- Does the refresh token from the first exchange still work?"
    token_request grant_type=refresh_token client_id=$CLIENT_ID "refresh_token=$refresh_token"
  fi
}

case_redirect() {
  echo "=== Different redirect_uri in the token request: expect \"Incorrect redirect_uri\" ==="
  get_code
  exchange "$CODE" "$VERIFIER" http://localhost:9999/other-callback
}

case_pkce() {
  echo "=== Wrong code_verifier: expect \"PKCE verification failed: Code mismatch\" ==="
  get_code
  exchange "$CODE" "$(openssl rand 48 | b64url)" "$REDIRECT_URI"
}

case_expired() {
  echo "=== Code redeemed too late (Client login timeout, default 1 minute) ==="
  get_code
  local wait=${WAIT_SECONDS:-70}
  echo "Waiting $wait seconds before the exchange..."
  sleep "$wait"
  exchange "$CODE" "$VERIFIER" "$REDIRECT_URI"
}

case "${1:-all}" in
  reuse) case_reuse ;;
  redirect) case_redirect ;;
  pkce) case_pkce ;;
  expired) case_expired ;;
  all)
    case_reuse
    echo
    case_redirect
    echo
    case_pkce
    ;;
  *)
    echo "Usage: scripts/code-exchange.sh [all|reuse|redirect|pkce|expired]"
    exit 1
    ;;
esac
