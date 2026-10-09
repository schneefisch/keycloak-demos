#!/usr/bin/env bash
# Toggles "Full scope allowed" for demo-cli, to compare tokens before and after.
#   scripts/full-scope.sh on     every token contains all of the user's roles (pre-26.8 default)
#   scripts/full-scope.sh off    only the roles in demo-cli's role scope (the demo realm's setting)
# Then: scripts/show-token.sh bob, and for "on" the warning in: docker compose logs keycloak
source "$(dirname "$0")/lib.sh"

case "${1:-}" in
  on) value=true ;;
  off) value=false ;;
  *) echo "Usage: scripts/full-scope.sh [on|off]"; exit 1 ;;
esac

kc_login
kcadm update "clients/$(client_uuid demo-cli)" -r demo -s "fullScopeAllowed=$value"
echo "Keycloak: Full scope allowed for demo-cli = $value"
