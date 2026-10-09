#!/usr/bin/env bash
# Switches apps between "naive" (Spring defaults) and "hardened" mode and restarts them.
#   scripts/mode.sh hardened                     all apps
#   scripts/mode.sh hardened events-api          one app
#   scripts/mode.sh                              shows the current modes
source "$(dirname "$0")/lib.sh"

load_modes
if [ $# -eq 0 ]; then
  echo "events-api: $EVENTS_API_MODE, notification-service: $NOTIFICATION_SERVICE_MODE, events-portal: $EVENTS_PORTAL_MODE"
  exit
fi

mode=$1
shift
[[ $mode == naive || $mode == hardened ]] || { echo "Usage: scripts/mode.sh [naive|hardened] [app...]"; exit 1; }
apps=("$@")
[ ${#apps[@]} -gt 0 ] || apps=("${APPS[@]}")

for app in "${apps[@]}"; do
  case $app in
    events-api) EVENTS_API_MODE=$mode ;;
    notification-service) NOTIFICATION_SERVICE_MODE=$mode ;;
    events-portal) EVENTS_PORTAL_MODE=$mode ;;
    *) echo "Unknown app: $app (${APPS[*]})"; exit 1 ;;
  esac
done

cat > .env <<EOF
EVENTS_API_MODE=$EVENTS_API_MODE
NOTIFICATION_SERVICE_MODE=$NOTIFICATION_SERVICE_MODE
EVENTS_PORTAL_MODE=$EVENTS_PORTAL_MODE
EOF

echo "events-api: $EVENTS_API_MODE, notification-service: $NOTIFICATION_SERVICE_MODE, events-portal: $EVENTS_PORTAL_MODE"
echo "  docker compose up -d --wait ${apps[*]} (takes a few seconds)"
compose --progress quiet up -d --wait "${apps[@]}"
