#!/usr/bin/env bash
# Decodes a JWT locally (never paste real tokens into online decoders).
# Usage: scripts/decode-token.sh [--all]   token from ACCESS_TOKEN or asked for
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox decode-token.sh "$@"

require_token ACCESS_TOKEN "access token"

echo "\$ jq -R 'split(\".\")[1] | gsub(\"-\"; \"+\") | gsub(\"_\"; \"/\") | @base64d | fromjson' <<< \"\$TOKEN\""
if [ "${1:-}" = "--all" ]; then
  jwt_payload "$ACCESS_TOKEN"
else
  jwt_payload "$ACCESS_TOKEN" | jq '{iss, aud, exp, exp_utc: (.exp | todate), typ, azp, "allowed-origins"}'
fi
