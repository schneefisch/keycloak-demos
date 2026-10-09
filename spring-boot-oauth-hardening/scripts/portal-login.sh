#!/usr/bin/env bash
# Logs a user in to events-portal the way a browser does, then lets the portal delete event 1.
#   scripts/portal-login.sh [alice|bob]
# Shows the principal name the portal uses and how events-api answered the portal's calls.
source "$(dirname "$0")/lib.sh"
[ -n "${IN_TOOLBOX:-}" ] || run_in_toolbox portal-login.sh "$@"

user=${1:-alice}

echo "events-portal mode: $EVENTS_PORTAL_MODE, events-api mode: $EVENTS_API_MODE"
echo
echo "=== $user logs in: portal → Keycloak login form → callback with code → portal session ==="
portal_login "$user"
name=$(portal_user)
[[ -n "$name" && "$name" != "not signed in" ]] || { echo "  ✘ Login failed"; exit 1; }
echo "  Signed in as: $name"
echo "  Events from events-api: $(sed -nE 's/.*<pre>(\{status=[0-9]+).*/\1}/p' <<< "$PAGE")"

echo
echo "=== $user clicks \"Delete event 1\" (the portal calls DELETE on events-api with $user's token) ==="
csrf=$(sed -nE 's/.*name="_csrf" value="([^"]*)".*/\1/p' <<< "$PAGE" | head -1)
echo "  CSRF token in the form: ${csrf:-(none: CSRF protection is off)}"
# -d makes it a POST; curl follows the redirect after it with a GET, like a browser
page=$(curl -s -L -c "$JAR" -b "$JAR" -d "${csrf:+_csrf=$csrf}" "$PORTAL/events/1/delete")
result=$(sed -nE 's/.*class="result">([^<]*)<.*/\1/p' <<< "$page")
echo "  $result"

if [ "$user" = bob ]; then
  # 403 in both modes: events-portal's role scope holds only events-api roles (Keycloak 26.8 way,
  # "Full scope allowed" off), so bob's portal token carries no notification-service role at all.
  # Compare scripts/attack.sh foreign-role: demo-cli needs roles for both APIs, and there it leaks.
  echo "  Keycloak left bob's notification-service role out of the portal's token (role scope)"
  expect "$EVENTS_API_MODE" 403 403 "${result##* }"
fi
exit $FAILED
