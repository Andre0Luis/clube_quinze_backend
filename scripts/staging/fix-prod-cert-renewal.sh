#!/usr/bin/env bash
# Corrige a renovação automática do certificado de PRODUÇÃO: troca o modo "standalone" (que precisa da porta 80,
# ocupada pelo nginx, e por isso falhou e deixou o certificado vencer) pelo modo webroot, sem parar o nginx.
# Pré-requisito: scripts/staging/enable-prod-acme.sh já executado. Rode como root na VPS.
set -euo pipefail

CERT_NAME="${1:-clubequinzeapp.cloud-0001}"
WEBROOT="${WEBROOT:-/var/www/certbot}"
HOOK_DIR=/etc/letsencrypt/renewal-hooks

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "rode como root"
[ -f "/etc/letsencrypt/renewal/$CERT_NAME.conf" ] || die "não achei a renovação de $CERT_NAME (veja: certbot certificates)"

log "Antes"
grep -E "authenticator|webroot" "/etc/letsencrypt/renewal/$CERT_NAME.conf" || true

log "Trocando o autenticador para webroot ($WEBROOT)"
certbot reconfigure --cert-name "$CERT_NAME" --authenticator webroot --webroot-path "$WEBROOT" --non-interactive

log "Hook de renovação: recarregar (e não reiniciar) o nginx"
mkdir -p "$HOOK_DIR/deploy"
cat > "$HOOK_DIR/deploy/reload-nginx.sh" <<'HOOK'
#!/bin/sh
# Recarrega (sem derrubar) os nginx depois de renovar certificados.
for c in clube-quinze-nginx clube-quinze-staging-nginx; do
  docker exec "$c" nginx -s reload >/dev/null 2>&1 || true
done
HOOK
chmod +x "$HOOK_DIR/deploy/reload-nginx.sh"
if [ -f "$HOOK_DIR/post/restart-nginx.sh" ]; then
  mv "$HOOK_DIR/post/restart-nginx.sh" "$HOOK_DIR/post/restart-nginx.sh.disabled"
  echo "hook antigo (restart do nginx) desativado: post/restart-nginx.sh.disabled"
fi

log "Teste de renovação (dry-run)"
certbot renew --cert-name "$CERT_NAME" --dry-run

log "Depois"
grep -E "authenticator|webroot" "/etc/letsencrypt/renewal/$CERT_NAME.conf" || true
echo
echo "OK: a renovação automática agora funciona com o nginx no ar."
