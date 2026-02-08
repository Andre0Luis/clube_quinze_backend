#!/usr/bin/env bash
set -euo pipefail

WORKDIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$WORKDIR"

APP_VERSION=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)
export APP_VERSION

docker compose build --build-arg APP_VERSION="${APP_VERSION}" api

docker compose up -d api nginx
