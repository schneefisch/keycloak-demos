#!/usr/bin/env bash
# Logout in events-portal: is the Keycloak SSO session gone too, and does the portal notice when
# the session ends in Keycloak?
#   scripts/portal-logout.sh
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox portal-logout.sh "$@"

MODE=$EVENTS_PORTAL_MODE
echo "events-portal mode: $MODE"
echo
echo "=== 1. alice clicks \"Log out\" in the portal ==="
portal_login alice
csrf=$(csrf_token)
location=$(curl -s -o /dev/null -c "$JAR" -b "$JAR" -w '%{redirect_url}' -d "${csrf:+_csrf=$csrf}" "$PORTAL/logout")
echo "  portal redirects to: $(sed -E 's/id_token_hint=([^&]{12})[^&]*/id_token_hint=\1…/' <<< "$location")"
# The browser follows the redirect: for RP-initiated logout that's Keycloak's end_session_endpoint
curl -s -L -o /dev/null -c "$JAR" -b "$JAR" "$location"

echo "  alice clicks \"Log in\" again:"
html=$(curl -s -L -c "$JAR" -b "$JAR" "$PORTAL/oauth2/authorization/keycloak")
if grep -q 'id="kc-form-login"' <<< "$html"; then
  result=password-required
  echo "  Keycloak asks for the password: the SSO session ended with the portal session"
else
  result=silent-login
  echo "  Signed in again without a password as $(portal_user "$html"): the Keycloak SSO session is still alive"
fi
expect "$MODE" silent-login password-required "$result"

echo
echo "=== 2. alice's session ends in Keycloak (an admin signs her out), is she still in the portal? ==="
JAR=$(mktemp)
portal_login alice
echo "  portal before: $(portal_user)"
admin=$(curl -s "http://localhost:8080/realms/master/protocol/openid-connect/token" -d grant_type=password \
  -d client_id=admin-cli -d username=admin -d password=admin | jq -r .access_token)
alice=$(curl -s -H "Authorization: Bearer $admin" \
  "http://localhost:8080/admin/realms/demo/users?username=alice&exact=true" | jq -r '.[0].id')
curl -s -o /dev/null -X POST -H "Authorization: Bearer $admin" "http://localhost:8080/admin/realms/demo/users/$alice/logout"
# Waits up to 5 s for Keycloak's back-channel call to reach the portal, instead of a fixed sleep
for _ in 1 2 3 4 5; do
  after=$(portal_user "$(curl -s -b "$JAR" "$PORTAL/")")
  [ "$after" = "not signed in" ] && break
  sleep 1
done
echo "  portal after:  $after"
if [ "$after" = "not signed in" ]; then result=ended; else result=still-signed-in; fi
expect "$MODE" still-signed-in ended "$result"
exit $FAILED
