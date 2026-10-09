#!/usr/bin/env bash
# Prints decoded tokens (header + payload) from the demo realm, e.g. for screenshots.
#   scripts/show-token.sh alice [scope]                 access token and ID token (demo-cli login)
#   scripts/show-token.sh notification-service [scope]  client credentials token
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox show-token.sh "$@"

who=${1:-alice}
case $who in
  alice | bob)
    response=$(login "$who" "${2:-openid events:read}")
    for kind in access_token id_token; do
      token=$(jq -r ".$kind" <<< "$response")
      echo "=== $who: $kind ==="
      jwt_part 0 "$token" | jq -c .
      jwt_part 1 "$token"
    done
    ;;
  notification-service | reporting-job)
    token=$(client_token "$who" "${2:-}" | jq -r .access_token)
    echo "=== $who: access_token ==="
    jwt_part 0 "$token" | jq -c .
    jwt_part 1 "$token"
    ;;
  *)
    echo "Usage: scripts/show-token.sh [alice|bob|notification-service|reporting-job] [scope]"
    exit 1
    ;;
esac
