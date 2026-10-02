#!/usr/bin/env bash
# Restores the baseline ("everything works") for one error, or for all of them.
source "$(dirname "$0")/lib.sh"

fix_1() {
  echo "Error 1: Require SSL back to External requests"
  set_realm_field sslRequired external
}

fix_2() {
  echo "Error 2: Valid redirect URIs back to http://localhost:5173/*"
  set_client_field spa redirectUris '["http://localhost:5173/*"]'
}

fix_3() {
  echo "Error 3: Web origins back to +, API and SPA back to baseline"
  set_client_field spa webOrigins '["+"]'
  compose_up "" api spa
}

fix_5() {
  echo "Error 5: default client scope demo-api-audience back on spa and cli-demo"
  local scope_id client
  scope_id=$(scope_uuid demo-api-audience)
  for client in spa cli-demo; do
    if has_default_scope "$client" demo-api-audience; then
      echo "  client $client: unchanged (demo-api-audience assigned)"
    else
      kcadm update "clients/$(client_uuid "$client")/default-client-scopes/$scope_id" -r demo
      echo "  client $client: default client scope demo-api-audience added"
    fi
  done
}

fix_6() {
  echo "Error 6: Keycloak with KC_HOSTNAME, API with issuer-uri http://localhost:8080/realms/demo"
  compose_up "" keycloak api
}

case "${1:-}" in
  1) kc_login; fix_1 ;;
  2) kc_login; fix_2 ;;
  3 | 3a | 3b | 3c) kc_login; fix_3 ;;
  4) echo "Error 4 has no switch. Nothing to fix." ;;
  5) kc_login; fix_5 ;;
  6) fix_6 ;;
  all)
    # Containers first: recreating Keycloak re-imports the realm
    fix_6
    kc_login
    fix_1
    fix_2
    fix_3
    fix_5
    ;;
  *)
    echo "Usage: scripts/fix.sh <1|2|3|3a|3b|3c|4|5|6|all>"
    exit 1
    ;;
esac
