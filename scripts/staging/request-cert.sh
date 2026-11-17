#!/usr/bin/env bash
# Emite o certificado do subdomínio de homologação (HTTP-01 via webroot, sem parar nada) e instala o hook que
# recarrega os nginx a cada renovação. Pré-requisitos: registro DNS A do subdomínio apontando para esta VPS e
# scripts/staging/enable-prod-acme.sh já executado. Rode como root na VPS.
set -euo pipefail

DOMAIN="${1:-staging.clubequinzeapp.cloud}"
WEBROOT="${WEBROOT:-/var/www/certbot}"
HOOK_DIR=/etc/letsencrypt/renewal-hooks/deploy

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "rode como root"
command -v certbot >/dev/null || die "certbot não instalado"

log "Conferindo DNS de $DOMAIN"
resolved="$(getent hosts "$DOMAIN" | awk '{print $1}' | head -n1 || true)"
[ -n "$resolved" ] || die "$DOMAIN ainda não resolve. Crie o registro A apontando para o IP desta VPS e aguarde alguns minutos."
public_ip="$(curl -fsS https://api.ipify.org || true)"
if [ -n "$public_ip" ] && [ "$resolved" != "$public_ip" ]; then
  die "$DOMAIN resolve para $resolved, mas esta VPS é $public_ip"
fi

log "Conferindo que o nginx de produção serve o desafio ACME para $DOMAIN"
mkdir -p "$WEBROOT/.well-known/acme-challenge"
echo "pong" > "$WEBROOT/.well-known/acme-challenge/ping-test"
body="$(curl -fsS "http://$DOMAIN/.well-known/acme-challenge/ping-test" || true)"
rm -f "$WEBROOT/.well-known/acme-challenge/ping-test"
[ "$body" = "pong" ] || die "o desafio não está acessível em http://$DOMAIN/.well-known/... Rode antes scripts/staging/enable-prod-acme.sh"

log "Emitindo o certificado (webroot)"
certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" --non-interactive --agree-tos --keep-until-expiring

log "Instalando o hook de renovação (recarrega os nginx)"
mkdir -p "$HOOK_DIR"
cat > "$HOOK_DIR/reload-nginx.sh" <<'HOOK'
#!/bin/sh
# Recarrega (sem derrubar) os nginx depois de renovar certificados.
for c in clube-quinze-nginx clube-quinze-staging-nginx; do
  docker exec "$c" nginx -s reload >/dev/null 2>&1 || true
done
HOOK
chmod +x "$HOOK_DIR/reload-nginx.sh"

log "OK: certificado de $DOMAIN emitido."
certbot certificates -d "$DOMAIN" 2>/dev/null | grep -E "Certificate Name|Domains|Expiry" || true
