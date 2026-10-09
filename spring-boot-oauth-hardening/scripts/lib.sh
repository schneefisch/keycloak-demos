# Shared helpers for the scripts in this folder. Sourced, not executed.
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEMO_DIR"

APPS=(events-api notification-service events-portal)

# --- host side: Docker Compose, modes and kcadm.sh -----------------------------------------

# Docker Compose v2 plugin ("docker compose") or the standalone binary ("docker-compose")
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# Current mode of each app, from .env (written by scripts/mode.sh). Default: naive
load_modes() {
  EVENTS_API_MODE=naive NOTIFICATION_SERVICE_MODE=naive EVENTS_PORTAL_MODE=naive
  # shellcheck disable=SC1091
  [ -f .env ] && source .env
  export EVENTS_API_MODE NOTIFICATION_SERVICE_MODE EVENTS_PORTAL_MODE
}

# Runs a script inside the toolbox container (curl, jq, openssl), so the host needs Docker only.
# Then exits: the rest of the calling script runs only inside the container.
run_in_toolbox() {
  local script=$1
  shift
  load_modes
  local opts=(--rm -e EVENTS_API_MODE -e NOTIFICATION_SERVICE_MODE -e EVENTS_PORTAL_MODE)
  [ -t 0 ] || opts+=(-T)
  compose --progress quiet run "${opts[@]}" toolbox "/scripts/$script" "$@"
  exit
}

# kcadm.sh runs inside the Keycloak container
kcadm() {
  compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"
}

kc_login() {
  local out
  out=$(kcadm config credentials --server http://localhost:8080 --realm master \
    --user admin --password admin 2>&1) || { echo "$out" >&2; return 1; }
}

client_uuid() {
  kcadm get clients -r demo -q clientId="$1" --fields id --format csv --noquotes
}

# --- toolbox side: tokens and calls ---------------------------------------------------------

OIDC=http://localhost:8080/realms/demo/protocol/openid-connect
EVENTS_API=http://events-api:8081
FAILED=0

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# Shortens long values (tokens) for readable output: eyJhbGciOiJS…(1234 chars)
short() {
  local s=$1
  if [ "${#s}" -gt 24 ]; then echo "${s:0:12}…(${#s} chars)"; else echo "$s"; fi
}

# Decodes one part of a JWT locally (0 = header, 1 = payload), no signature check
jwt_part() {
  jq -R --argjson i "$1" 'split(".")[$i] | gsub("-"; "+") | gsub("_"; "/") | @base64d | fromjson' <<< "$2"
}

# Prints the action URL of Keycloak's login form in the HTML in $1, or nothing. "|| true": without a
# form grep fails, and under pipefail that would end the script before the caller can report it.
login_form_action() {
  grep -o '<form[^>]*id="kc-form-login"[^>]*>' <<< "$1" | sed -E 's/.*action="([^"]*)".*/\1/; s/&amp;/\&/g' || true
}

# Logs a user in through the public client demo-cli (authorization code + PKCE), the way a
# browser would: fetch the login form, post the credentials, exchange the code.
# Prints the token response. Scripts only: real users log in through events-portal.
login() {
  local user=$1 scope=$2 jar verifier challenge state html action location code
  jar=$(mktemp)
  verifier=$(openssl rand 48 | b64url)
  challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | b64url)
  state=$(openssl rand 12 | b64url)
  html=$(curl -s -c "$jar" -b "$jar" -G "$OIDC/auth" \
    --data-urlencode client_id=demo-cli --data-urlencode response_type=code \
    --data-urlencode "scope=$scope" --data-urlencode redirect_uri=http://localhost:9999/callback \
    --data-urlencode "state=$state" --data-urlencode "code_challenge=$challenge" \
    --data-urlencode code_challenge_method=S256)
  action=$(login_form_action "$html")
  [ -n "$action" ] || { echo "No Keycloak login form for $user" >&2; return 1; }
  location=$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' --data-urlencode "username=$user" \
    --data-urlencode password=password --data-urlencode credentialId= "$action")
  rm -f "$jar"
  code=$(sed -nE 's/.*[?&]code=([^&]+).*/\1/p' <<< "$location")
  [ -n "$code" ] || { echo "Login failed for $user: $location" >&2; return 1; }
  curl -s -X POST "$OIDC/token" --data-urlencode grant_type=authorization_code \
    --data-urlencode client_id=demo-cli --data-urlencode "code=$code" \
    --data-urlencode "code_verifier=$verifier" --data-urlencode redirect_uri=http://localhost:9999/callback
}

# Logs a user in to events-portal like a browser: portal → Keycloak login form → callback → session.
# Uses the cookie jar in $JAR and leaves the resulting page in $PAGE.
PORTAL=http://localhost:8082
portal_login() {
  local user=$1 html action
  JAR=${JAR:-$(mktemp)}
  html=$(curl -s -L -c "$JAR" -b "$JAR" "$PORTAL/oauth2/authorization/keycloak")
  action=$(login_form_action "$html")
  [ -n "$action" ] || { echo "  ✘ No Keycloak login form" >&2; return 1; }
  PAGE=$(curl -s -L -c "$JAR" -b "$JAR" --data-urlencode "username=$user" --data-urlencode password=password \
    --data-urlencode credentialId= "$action")
}

# Prints the first CSRF token on the page in $PAGE. sed stops by itself: "| head -1" would make
# sed fail with SIGPIPE under pipefail when the page has several forms.
csrf_token() {
  sed -nE '/name="_csrf"/{s/.*name="_csrf" value="([^"]*)".*/\1/p;q;}' <<< "$PAGE"
}

# Prints who the portal session belongs to, or "not signed in"
portal_user() {
  sed -nE 's/.*Signed in as <code>([^<]*)<\/code>.*/\1/p; s/.*Log in with Keycloak.*/not signed in/p' <<< "${1:-$PAGE}"
}

# Client credentials grant. Prints the token response.
client_token() {
  local client=$1 scope=${2:-}
  local args=(-d grant_type=client_credentials)
  [ -n "$scope" ] && args+=(--data-urlencode "scope=$scope")
  curl -s -X POST "$OIDC/token" -u "$client:$client-secret" "${args[@]}"
}

# Calls events-api. Prints the request, the status and the reason for a 401/403. Sets STATUS.
call_api() {
  local method=$1 path=$2 token=$3 headers
  headers=$(mktemp)
  echo "  \$ curl -X $method $EVENTS_API$path -H 'Authorization: Bearer $(short "$token")'"
  STATUS=$(curl -s -o /dev/null -D "$headers" -w '%{http_code}' -X "$method" \
    -H "Authorization: Bearer $token" "$EVENTS_API$path")
  sed -nE 's/^[Ww][Ww][Ww]-[Aa]uthenticate: .*error_description="([^"]*)".*/  reason: \1/p' "$headers"
  rm -f "$headers"
}

# Prints what events-api made of a token (principal and authorities)
api_whoami() {
  # -f: a 401 fails curl, and with it the pipeline
  curl -sf -H "Authorization: Bearer $1" "$EVENTS_API/api/whoami" | jq -c '{principal, authorities}' || echo "(rejected)"
}

# Compares a result (HTTP status or outcome) with the expectation for the mode:
#   expect MODE NAIVE HARDENED ACTUAL
expect() {
  local mode=$1 naive=$2 hardened=$3 actual=$4 expected prefix=""
  if [ "$mode" = hardened ]; then expected=$hardened; else expected=$naive; fi
  [[ $actual =~ ^[0-9]+$ ]] && prefix="HTTP "
  if [ "$actual" = "$expected" ]; then
    echo "  → $prefix$actual, as expected in $mode mode (naive: $naive, hardened: $hardened)"
  else
    echo "  ✘ UNEXPECTED: $prefix$actual, expected $expected in $mode mode (naive: $naive, hardened: $hardened)"
    FAILED=1
  fi
}
