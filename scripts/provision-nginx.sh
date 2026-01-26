#!/usr/bin/env bash
set -euo pipefail

# Automates obtaining/renewing Let's Encrypt certs and (re)starting the nginx reverse proxy.
# Requirements: docker + docker compose installed, DNS A record pointing to this host.

DOMAIN="${DOMAIN:-clubequinzeapp.cloud}"
EMAIL="${EMAIL:-admin@${DOMAIN}}" # change if needed
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="docker compose"

cd "$PROJECT_ROOT"

echo "[info] Using domain=$DOMAIN email=$EMAIL project_root=$PROJECT_ROOT"

# Ensure nginx service exists before requesting cert (so volumes/paths are consistent)
if ! $COMPOSE config --services | grep -q "^nginx$"; then
  echo "[error] nginx service not found in compose.yaml" >&2
  exit 1
fi

# Stop nginx to free port 80 for standalone certbot challenge
if $COMPOSE ps --services --status running | grep -q "^nginx$"; then
  echo "[info] Stopping nginx to free port 80 for certbot"
  $COMPOSE stop nginx
fi

# Obtain/renew certificate using certbot standalone
mkdir -p /etc/letsencrypt

echo "[info] Requesting/renewing certificate for $DOMAIN"
docker run --rm \
  -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt \
  certbot/certbot:v2.10.0 \
  certonly --standalone \
  --non-interactive --agree-tos \
  -m "$EMAIL" \
  -d "$DOMAIN" -d "www.$DOMAIN"

echo "[info] Certificate obtained. Starting nginx with TLS"
$COMPOSE up -d nginx

# Optional: renew hook you can add to cron (monthly):
# 0 4 1 * * DOMAIN=clubequinzeapp.cloud EMAIL=admin@clubequinzeapp.cloud /bin/bash /path/to/scripts/provision-nginx.sh >> /var/log/provision-nginx.log 2>&1

echo "[done] nginx running with certificates mounted. Test: curl -I https://$DOMAIN/health"
