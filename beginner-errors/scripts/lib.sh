# Shared helpers for the scripts in this folder. Sourced, not executed.
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEMO_DIR"

# --- host side: Docker Compose and kcadm.sh ------------------------------------------------

# Docker Compose v2 plugin ("docker compose") or the standalone binary ("docker-compose")
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# Runs a script inside the toolbox container (curl, jq, openssl), so the host needs Docker only
run_in_toolbox() {
  local script=$1
  shift
  local opts=(--rm -e ACCESS_TOKEN -e ID_TOKEN)
  [ -t 0 ] || opts+=(-T)
  compose --progress quiet run "${opts[@]}" toolbox "/scripts/$script" "$@"
  exit
}

# kcadm.sh runs inside the Keycloak container: the request comes from localhost,
# so it works even when "Require SSL" blocks your browser
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

# Prints one field of a client as compact JSON, e.g. client_field spa redirectUris
client_field() {
  # kcadm writes a stray empty line to stderr for --fields
  kcadm get "clients/$(client_uuid "$1")" -r demo --fields "$2" 2>/dev/null | tr -d ' \n'
  echo
}

# Sets a client field (JSON value) and prints before -> after
set_client_field() {
  local client=$1 field=$2 value=$3 before after
  before=$(client_field "$client" "$field")
  kcadm update "clients/$(client_uuid "$client")" -r demo -s "$field=$value" >/dev/null
  after=$(client_field "$client" "$field")
  report "client $client" "$before" "$after"
}

set_realm_field() {
  local field=$1 value=$2 before after
  before=$(kcadm get realms/demo --fields "$field" --format csv --noquotes)
  kcadm update realms/demo -s "$field=$value"
  after=$(kcadm get realms/demo --fields "$field" --format csv --noquotes)
  report "realm demo: $field" "$before" "$after"
}

report() {
  if [ "$2" = "$3" ]; then
    echo "  $1: unchanged $3"
  else
    echo "  $1: $2 -> $3"
  fi
}

has_default_scope() {
  # not grep -q: it exits early, and with pipefail the SIGPIPE in kcadm would count as "not found"
  kcadm get "clients/$(client_uuid "$1")/default-client-scopes" -r demo --fields name --format csv --noquotes \
    | grep -x "$2" >/dev/null
}

scope_uuid() {
  kcadm get client-scopes -r demo --fields id,name --format csv --noquotes | grep ",$1\$" | cut -d, -f1
}

# Brings services up with an optional override from compose/ and waits until they are healthy
compose_up() {
  local files=(-f docker-compose.yml)
  [ -n "$1" ] && files+=(-f "compose/$1")
  shift
  echo "  docker compose ${files[*]} up -d --wait $* (takes a few seconds)"
  compose --progress quiet "${files[@]}" up -d --wait "$@"
}

# --- toolbox side: token helpers ----------------------------------------------------------

# Shortens long values (tokens, codes) for screenshots: eyJhbGciOiJS…(1234 chars)
short() {
  local s=$1
  if [ "${#s}" -gt 24 ]; then echo "${s:0:12}…(${#s} chars)"; else echo "$s"; fi
}

# Decodes the payload of a JWT locally (base64url, no signature check)
jwt_payload() {
  jq -R 'split(".")[1] | gsub("-"; "+") | gsub("_"; "/") | @base64d | fromjson' <<< "$1"
}

# Reads a token from the given env var, or asks for it
require_token() {
  local var=$1 label=$2
  if [ -z "${!var:-}" ]; then
    echo "Paste the $label (SPA button \"Copy $label\") and press Enter:" >&2
    read -r "${var?}"
  fi
}
