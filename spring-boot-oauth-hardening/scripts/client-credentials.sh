#!/usr/bin/env bash
# notification-service calls events-api with the client credentials grant.
#   scripts/client-credentials.sh
# Naive notification-service requests no scope: Keycloak issues a token without the events-api
# audience. A hardened events-api rejects it. Hardened notification-service requests events:read.
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox client-credentials.sh "$@"

echo "notification-service mode: $NOTIFICATION_SERVICE_MODE, events-api mode: $EVENTS_API_MODE"
echo
echo "=== The token notification-service would get (decoded here, never in the service itself) ==="
if [ "$NOTIFICATION_SERVICE_MODE" = hardened ]; then scope=events:read; else scope=""; fi
echo "  \$ curl -X POST $OIDC/token -u notification-service:… -d grant_type=client_credentials${scope:+ -d scope=$scope}"
client_token notification-service "$scope" | jq -r .access_token | { read -r token; jwt_part 1 "$token" | jq -c '{scope, aud, azp, client_id}'; }

echo
echo "=== GET http://notification-service:8083/reminders/preview ==="
result=$(curl -s http://notification-service:8083/reminders/preview)
jq '{whoami: .whoami, events: .events.status}' <<< "$result"

status=$(jq -r .whoami.status <<< "$result")
if [ "$NOTIFICATION_SERVICE_MODE" = naive ] && [ "$EVENTS_API_MODE" = hardened ]; then expected=401; else expected=200; fi
if [ "$status" = "$expected" ]; then
  echo "  ✔ events-api answered HTTP $status (expected for this combination)"
else
  echo "  ✘ events-api answered HTTP $status, expected $expected"
  exit 1
fi
