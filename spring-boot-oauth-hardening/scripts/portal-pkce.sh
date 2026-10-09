#!/usr/bin/env bash
# Shows the authorization request events-portal sends to Keycloak, and whether Keycloak accepts it.
#   scripts/portal-pkce.sh            show the request parameters (state, nonce, PKCE)
#   scripts/portal-pkce.sh enforce    Keycloak requires PKCE (S256) for events-portal
#   scripts/portal-pkce.sh relax      Keycloak doesn't require PKCE (Keycloak's default)
source "$(dirname "$0")/lib.sh"

set_pkce() {
  kc_login
  kcadm update "clients/$(client_uuid events-portal)" -r demo -s "attributes.\"pkce.code.challenge.method\"=$1"
  if [ -n "$1" ]; then echo "Keycloak: events-portal must use PKCE ($1)"; else echo "Keycloak: PKCE optional for events-portal"; fi
}

case "${1:-show}" in
  enforce) set_pkce S256; exit ;;
  relax) set_pkce ""; exit ;;
  show) ;;
  *) echo "Usage: scripts/portal-pkce.sh [show|enforce|relax]"; exit 1 ;;
esac

[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox portal-pkce.sh show

# Sends an authorization request to Keycloak and prints its answer
keycloak_answer() {
  local status location error
  read -r status location < <(curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$1")
  if [ "$status" = 200 ]; then
    echo "  Keycloak: HTTP 200, login page. Request accepted."
  else
    error=$(sed -nE 's/.*error_description=([^&]*).*/\1/p' <<< "$location" | sed 's/+/ /g; s/%3A/:/g')
    echo "  Keycloak: HTTP $status, redirect back to the portal with an error: $error"
  fi
}

echo "events-portal mode: $EVENTS_PORTAL_MODE"
echo
echo "=== Authorization request (redirect from http://localhost:8082/oauth2/authorization/keycloak) ==="
url=$(curl -s -o /dev/null -w '%{redirect_url}' http://localhost:8082/oauth2/authorization/keycloak)
for param in client_id scope state nonce code_challenge code_challenge_method redirect_uri; do
  value=$(sed -nE "s/.*[?&]$param=([^&]*).*/\1/p" <<< "$url")
  printf '  %-22s %s\n' "$param" "${value:-(not sent)}"
done
keycloak_answer "$url"

echo
echo "=== The same request without PKCE, as a Spring Boot 3.x BFF sends it by default ==="
keycloak_answer "$(sed -E 's/&code_challenge(_method)?=[^&]*//g' <<< "$url")"
