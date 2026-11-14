#!/usr/bin/env bash
# Prepara o nginx de PRODUÇÃO para emitir/renovar certificados SEM parar o nginx:
#   1) cria /var/www/certbot (pasta do desafio HTTP-01);
#   2) adiciona em nginx/default.conf (porta 80) o location /.well-known/acme-challenge/;
#   3) monta /var/www/certbot dentro do container nginx (recria só o nginx: ~2 s de indisponibilidade).
# Tem backup e volta sozinho ao estado anterior se algo falhar. Rode como root na VPS.
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/docker/clube_quinze_backend}"
PROJECT_NAME="${PROJECT_NAME:-clube_quinze_backend}"
NGINX_CONTAINER="${NGINX_CONTAINER:-clube-quinze-nginx}"
WEBROOT="${WEBROOT:-/var/www/certbot}"
SITE="${SITE:-clubequinzeapp.cloud}"
CONF="$PROJECT_DIR/nginx/default.conf"
COMPOSE="$PROJECT_DIR/docker-compose.yml"
STAMP="$(date +%Y%m%d%H%M%S)"

log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "rode como root"
[ -f "$CONF" ] || die "não achei $CONF"
[ -f "$COMPOSE" ] || die "não achei $COMPOSE"
docker inspect "$NGINX_CONTAINER" >/dev/null 2>&1 || die "container $NGINX_CONTAINER não encontrado"

restore() {
  log "Falhou: restaurando backups"
  cp -f "$CONF.bak.$STAMP" "$CONF"
  cp -f "$COMPOSE.bak.$STAMP" "$COMPOSE"
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE" --project-directory "$PROJECT_DIR" up -d --no-deps nginx || true
}

log "Backups ($STAMP)"
cp -p "$CONF" "$CONF.bak.$STAMP"
cp -p "$COMPOSE" "$COMPOSE.bak.$STAMP"
mkdir -p "$WEBROOT/.well-known/acme-challenge"

log "1/3 nginx/default.conf: location do desafio ACME na porta 80"
python3 - "$CONF" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path).read()
if "acme-challenge" in text:
    print("já existe; nada a fazer")
    sys.exit(0)
m = re.search(r"(server\s*\{\s*listen 80;[^\n]*\n\s*server_name[^\n]*\n)", text)
if not m:
    sys.exit("não encontrei o bloco 'listen 80' no nginx/default.conf")
block = ("    location ^~ /.well-known/acme-challenge/ {\n"
         "        root /var/www/certbot;\n"
         "        default_type text/plain;\n"
         "    }\n\n")
rest = text[m.end():]
# O "return 301" no nível do server roda ANTES da escolha do location e engoliria o desafio:
# precisa ficar dentro de um "location /".
rest, n = re.subn(r"^([ \t]*)return 301 ([^\n]*)\n",
                  lambda r: (f"{r.group(1)}location / {{\n"
                             f"{r.group(1)}    return 301 {r.group(2)}\n"
                             f"{r.group(1)}}}\n"),
                  rest, count=1, flags=re.M)
if n != 1:
    sys.exit("não encontrei o 'return 301' do bloco da porta 80")
open(path, "w").write(text[:m.end()] + block + rest)
print("location do desafio adicionado e redirecionamento movido para 'location /'")
PY

log "Validando a nova configuração no nginx em execução (nginx -t)"
if ! docker exec "$NGINX_CONTAINER" nginx -t; then
  cp -f "$CONF.bak.$STAMP" "$CONF"
  die "nginx -t falhou; configuração restaurada, nada foi alterado"
fi

log "2/3 docker-compose.yml: montar $WEBROOT no nginx"
python3 - "$COMPOSE" <<'PY'
import sys
path = sys.argv[1]
lines = open(path).read().split("\n")
if any("/var/www/certbot" in l for l in lines):
    print("já existe; nada a fazer")
    sys.exit(0)
out, done = [], False
for l in lines:
    out.append(l)
    if not done and "/etc/letsencrypt:/etc/letsencrypt:ro" in l:
        indent = l[: len(l) - len(l.lstrip())]
        out.append(f"{indent}- /var/www/certbot:/var/www/certbot:ro")
        done = True
if not done:
    sys.exit("não encontrei a linha do /etc/letsencrypt no compose")
open(path, "w").write("\n".join(out))
print("volume adicionado")
PY

log "3/3 Recriando só o nginx (~2 s)"
trap restore ERR
docker compose -p "$PROJECT_NAME" -f "$COMPOSE" --project-directory "$PROJECT_DIR" up -d --no-deps nginx
sleep 3

log "Verificando"
docker exec "$NGINX_CONTAINER" nginx -t
echo "pong" > "$WEBROOT/.well-known/acme-challenge/ping-test"
body="$(curl -fsS -H "Host: $SITE" http://127.0.0.1/.well-known/acme-challenge/ping-test)"
[ "$body" = "pong" ] || die "desafio ACME não está sendo servido (recebi: $body)"
code="$(curl -s -o /dev/null -w '%{http_code}' --resolve "$SITE:443:127.0.0.1" "https://$SITE/api/v1/plans")"
[ "$code" = "200" ] || die "a API em produção não respondeu 200 (recebi $code)"
trap - ERR
rm -f "$WEBROOT/.well-known/acme-challenge/ping-test"

log "OK. Desafio ACME servido pelo nginx de produção e a API segue respondendo 200."
echo "Backups: $CONF.bak.$STAMP e $COMPOSE.bak.$STAMP"
