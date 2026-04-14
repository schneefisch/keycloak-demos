#!/usr/bin/env bash
# Downloads the keycloak-restrict-client-auth SPI JAR.
# Run this once before docker-compose up.
#
# https://github.com/sventorben/keycloak-restrict-client-auth

set -euo pipefail

VERSION="v26.1.0"
JAR="keycloak-restrict-client-auth.jar"
URL="https://github.com/sventorben/keycloak-restrict-client-auth/releases/download/${VERSION}/${JAR}"
DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [ -f "${DIR}/${JAR}" ]; then
  echo "SPI JAR already exists: ${DIR}/${JAR}"
  exit 0
fi

echo "Downloading ${JAR} ${VERSION}..."
curl -sL -o "${DIR}/${JAR}" "${URL}"
echo "Downloaded to ${DIR}/${JAR}"
