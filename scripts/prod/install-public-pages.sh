#!/usr/bin/env bash
# Publica no nginx de PRODUÇÃO a página inicial (/) e a política de privacidade (/privacy-policy), exigidas pelo
# Google para o login OAuth (Google Agenda). Não precisa de release da API nem reinicia o nginx: copia os HTMLs para
# nginx/policies, adiciona dois "location" no nginx/default.conf (com backup), valida com nginx -t e recarrega.
# Rode como root na VPS, a partir de um clone atualizado do repositório (ex.: /opt/cq-repo).
set -euo pipefail

REPO_DIR="${REPO_DIR:-/opt/cq-repo}"
PROJECT_DIR="${PROJECT_DIR:-/docker/clube_quinze_backend}"
NGINX_CONTAINER="${NGINX_CONTAINER:-clube-quinze-nginx}"
SITE="${SITE:-clubequinzeapp.cloud}"
CONF="$PROJECT_DIR/nginx/default.conf"
POLICIES="$PROJECT_DIR/nginx/policies"
STAMP="$(date +%Y%m%d%H%M%S)"

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "rode como root"
for f in home.html privacy.html; do [ -f "$REPO_DIR/nginx/policies/$f" ] || die "não achei $REPO_DIR/nginx/policies/$f (atualize o clone: git -C $REPO_DIR pull)"; done
[ -f "$CONF" ] || die "não achei $CONF"
docker inspect "$NGINX_CONTAINER" >/dev/null 2>&1 || die "container $NGINX_CONTAINER não encontrado"

log "Backup da configuração ($STAMP)"
cp -p "$CONF" "$CONF.bak.$STAMP"
restore() {
  log "Falhou: restaurando a configuração anterior"
  cp -f "$CONF.bak.$STAMP" "$CONF"
  docker exec "$NGINX_CONTAINER" nginx -s reload || true
}
trap restore ERR

log "1/3 Copiando as páginas para $POLICIES"
mkdir -p "$POLICIES"
cp -f "$REPO_DIR/nginx/policies/home.html" "$POLICIES/home.html"
cp -f "$REPO_DIR/nginx/policies/privacy.html" "$POLICIES/privacy.html"
chmod 644 "$POLICIES/home.html" "$POLICIES/privacy.html"

log "2/3 nginx/default.conf: location da página inicial e da política"
python3 - "$CONF" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path).read()
blocks = []
if not re.search(r"location\s*=\s*/\s*\{", text):
    blocks.append("""    # Páginas públicas (Google OAuth exige página inicial e política acessíveis, sem login)
    location = / {
        root /var/www/policies;
        try_files /home.html =404;
        default_type text/html;
        add_header Cache-Control "public, max-age=300";
    }
""")
if "location = /privacy-policy" not in text:
    blocks.append("""    location = /privacy-policy {
        default_type text/html;
        alias /var/www/policies/privacy.html;
        add_header Cache-Control "public, max-age=300";
    }
""")
if not blocks:
    print("já existem; nada a fazer")
    sys.exit(0)
# Insere dentro do bloco HTTPS, antes do primeiro "location" (o "location /" genérico fica por último).
https = text.index("listen 443")
first = re.search(r"^[ \t]*location\s", text[https:], re.M)
if not first:
    sys.exit("não encontrei nenhum location no bloco HTTPS")
pos = https + first.start()
open(path, "w").write(text[:pos] + "\n".join(blocks) + "\n" + text[pos:])
print(f"{len(blocks)} location(s) adicionado(s)")
PY

log "3/3 Validando e recarregando o nginx (sem derrubar)"
docker exec "$NGINX_CONTAINER" nginx -t
docker exec "$NGINX_CONTAINER" nginx -s reload
sleep 1

log "Verificando"
home="$(curl -sS -o /tmp/home.out -w '%{http_code} %{content_type}' --resolve "$SITE:443:127.0.0.1" "https://$SITE/")"
echo "  /               -> $home"
grep -q "Clube Quinze" /tmp/home.out || die "a página inicial não traz o nome do app"
priv="$(curl -sS -o /tmp/priv.out -w '%{http_code} %{content_type}' --resolve "$SITE:443:127.0.0.1" "https://$SITE/privacy-policy")"
echo "  /privacy-policy -> $priv"
grep -q "Google Agenda" /tmp/priv.out || die "a política não traz a seção do Google Agenda"
api="$(curl -sS -o /dev/null -w '%{http_code}' --resolve "$SITE:443:127.0.0.1" "https://$SITE/api/v1/plans")"
[ "$api" = "200" ] || die "a API em produção não respondeu 200 (recebi $api)"
trap - ERR
rm -f /tmp/home.out /tmp/priv.out

log "OK: https://$SITE/ e https://$SITE/privacy-policy no ar; API segue respondendo 200."
echo "Backup: $CONF.bak.$STAMP"
