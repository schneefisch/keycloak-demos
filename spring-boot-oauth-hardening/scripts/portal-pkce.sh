#!/usr/bin/env bash
# Shows the authorization request events-portal sends to Keycloak, and whether Keycloak accepts it.
# The demo realm requires PKCE (S256) for events-portal, so a request without it is rejected.
#   scripts/portal-pkce.sh
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox portal-pkce.sh "$@"

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
