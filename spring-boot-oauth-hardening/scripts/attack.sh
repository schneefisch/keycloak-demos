#!/usr/bin/env bash
# Sends real Keycloak tokens that events-api should reject, and two that it should accept.
#   scripts/attack.sh [all|foreign-audience|id-token|foreign-role|controls]
# The expected status depends on the mode of events-api (scripts/mode.sh).
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox attack.sh "$@"

MODE=$EVENTS_API_MODE

case_foreign_audience() {
  echo "=== Token for another service: reporting-job's token for notification-service ==="
  local token
  token=$(client_token reporting-job notifications:send | jq -r .access_token)
  echo "  token aud: $(jwt_part 1 "$token" | jq -c .aud)"
  call_api GET /api/events "$token"
  expect "$MODE" 200 401 "$STATUS"
}

case_id_token() {
  echo "=== ID token used as bearer token (alice) ==="
  local token
  token=$(login alice "openid events:read" | jq -r .id_token)
  echo "  header typ: $(jwt_part 0 "$token" | jq -c .typ), payload typ: $(jwt_part 1 "$token" | jq -c .typ), aud: $(jwt_part 1 "$token" | jq -c .aud)"
  call_api GET /api/events "$token"
  expect "$MODE" 200 401 "$STATUS"
}

case_foreign_role() {
  echo "=== bob is admin in notification-service only, and deletes an event in events-api ==="
  local token
  token=$(login bob "openid events:read" | jq -r .access_token)
  echo "  token resource_access: $(jwt_part 1 "$token" | jq -c .resource_access)"
  echo "  events-api sees: $(api_whoami "$token")"
  call_api DELETE /api/events/1 "$token"
  expect "$MODE" 204 403 "$STATUS"
}

case_controls() {
  echo "=== Controls: alice's access token works in both modes ==="
  local token
  token=$(login alice "openid events:read" | jq -r .access_token)
  echo "  events-api sees: $(api_whoami "$token")"
  call_api GET /api/events "$token"
  expect "$MODE" 200 200 "$STATUS"
  call_api DELETE /api/events/1 "$token"
  expect "$MODE" 204 204 "$STATUS"
}

echo "events-api mode: $MODE"
echo
case "${1:-all}" in
  foreign-audience) case_foreign_audience ;;
  id-token) case_id_token ;;
  foreign-role) case_foreign_role ;;
  controls) case_controls ;;
  all)
    case_foreign_audience
    echo
    case_id_token
    echo
    case_foreign_role
    echo
    case_controls
    ;;
  *)
    echo "Usage: scripts/attack.sh [all|foreign-audience|id-token|foreign-role|controls]"
    exit 1
    ;;
esac
exit $FAILED
