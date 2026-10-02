#!/usr/bin/env bash
# Puts the demo into the broken state for one of the six errors. Undo with scripts/fix.sh <n>.
source "$(dirname "$0")/lib.sh"

usage() {
  cat <<'EOF'
Usage: scripts/break.sh <n>

  1    HTTPS required            realm demo: Require SSL = All requests
  2    Invalid redirect_uri      client spa: Valid redirect URIs without the trailing slash
  3a   CORS (Keycloak)           client spa: Web origins emptied   (3 = 3a)
  3b   CORS (API)                API without CORS configuration    (recreates api)
  3c   CORS? (outdated URL)      SPA uses http://localhost:8080/auth (recreates spa)
  4    invalid_grant             no switch needed: run scripts/code-exchange.sh
  5    401 (audience)            clients spa + cli-demo: default scope demo-api-audience removed
  6    Issuer mismatch           Keycloak without KC_HOSTNAME, API expects keycloak:8080
                                 (recreates keycloak + api: realm is re-imported, other breaks reset)

Compose-based breaks (3b, 3c, 6) replace each other: use one at a time.
EOF
}

case "${1:-}" in
  1)
    echo "Error 1: HTTPS required"
    kc_login
    set_realm_field sslRequired all
    echo "Open http://localhost:5173 and click Login. Never do this on the master realm."
    ;;
  2)
    echo "Error 2: Invalid parameter: redirect_uri"
    kc_login
    # keycloak-js sends location.href, i.e. http://localhost:5173/ (with slash)
    set_client_field spa redirectUris '["http://localhost:5173"]'
    echo "Open http://localhost:5173 and click Login."
    ;;
  3 | 3a)
    echo "Error 3a: CORS, Keycloak does not know the SPA origin"
    kc_login
    set_client_field spa webOrigins '[]'
    echo "Open http://localhost:5173, DevTools open, and click Login."
    ;;
  3b)
    echo "Error 3b: CORS, the API sends no CORS headers"
    compose_up break-3b-api-cors-off.yml api
    echo "  api: DEMO_CORS_ENABLED=false"
    echo "Open http://localhost:5173, log in and click Call API."
    ;;
  3c)
    echo "Error 3c: SPA configured with the outdated /auth base URL"
    compose_up break-3c-spa-auth-url.yml spa
    echo "  spa: VITE_KEYCLOAK_URL=http://localhost:8080/auth"
    echo "Reload http://localhost:5173 and click Login."
    ;;
  4)
    echo "Error 4 needs no switch. Run scripts/code-exchange.sh"
    ;;
  5)
    echo "Error 5: tokens without the demo-api audience"
    kc_login
    scope_id=$(scope_uuid demo-api-audience)
    for client in spa cli-demo; do
      if has_default_scope "$client" demo-api-audience; then
        kcadm delete "clients/$(client_uuid "$client")/default-client-scopes/$scope_id" -r demo
        echo "  client $client: default client scope demo-api-audience removed"
      else
        echo "  client $client: unchanged (demo-api-audience already removed)"
      fi
    done
    echo "Log out and in again in the SPA, copy the new access token, then run scripts/call-api.sh no-audience"
    ;;
  6)
    echo "Error 6: issuer mismatch"
    compose_up break-6-issuer-mismatch.yml keycloak api
    echo "  keycloak: KC_HOSTNAME and KC_HOSTNAME_BACKCHANNEL_DYNAMIC removed"
    echo "  api: issuer-uri=http://keycloak:8080/realms/demo"
    echo "Log in again in the SPA (Keycloak was recreated) and click Call API."
    ;;
  *)
    usage
    exit 1
    ;;
esac
