#!/usr/bin/env bash
# Teste de fumaça da homologação: exercita o fluxo real pela API (agendar, confirmar, propor remarcação,
# aprovar, cancelar, links de calendário e configurações). Sai com erro na primeira falha.
#
# Uso: scripts/staging/smoke.sh <URL_BASE> <CHAVE_DE_ACESSO> [versão-esperada]
# Variáveis opcionais: ADMIN_EMAIL, ADMIN_PASSWORD (padrão: admin criado pelo seed da aplicação).
set -euo pipefail

BASE="${1:?uso: smoke.sh <URL_BASE> <CHAVE_DE_ACESSO> [versão-esperada]}"
KEY="${2:?informe a chave de acesso}"
EXPECTED_VERSION="${3:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-aluis283@gmail.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-luizinho@01}"
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

command -v jq >/dev/null || { echo "ERRO: jq é necessário" >&2; exit 1; }

STATUS=""
step() { printf '\n▶ %s\n' "$*"; }
fail() { printf '✖ %s\n' "$*" >&2; exit 1; }
body() { cat "$BODY_FILE"; }
field() { jq -r "$1" "$BODY_FILE"; }

# api METHOD PATH [TOKEN] [JSON]  -> grava o corpo em $BODY_FILE e o status em $STATUS (sempre no shell atual,
# nunca dentro de $(...), senão a variável se perde).
api() {
  local method="$1" path="$2" token="${3:-}" payload="${4:-}"
  local args=(-sS -m 30 -X "$method" -H "X-Staging-Key: $KEY" -H "Content-Type: application/json" -o "$BODY_FILE" -w '%{http_code}')
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer $token"); fi
  if [ -n "$payload" ]; then args+=(-d "$payload"); fi
  STATUS="$(curl "${args[@]}" "$BASE$path")"
}

expect() { # expect <status esperado> <descrição>
  [ "$STATUS" = "$1" ] || fail "$2 — esperado HTTP $1, veio $STATUS: $(body)"
  echo "✔ $2"
}

slot() { # slot <dias à frente> <hora cheia> -> data/hora ISO
  python3 - "$1" "$2" <<'PY'
import datetime, sys
days, hour = int(sys.argv[1]), int(sys.argv[2])
d = datetime.date.today() + datetime.timedelta(days=days)
print(f"{d.isoformat()}T{hour:02d}:00:00")
PY
}

# book <hora> -> cria um agendamento do cliente em um dia aleatório distante; o id fica em $APPT_ID
APPT_ID=""
book() {
  local at
  for _ in 1 2 3 4 5 6; do
    at="$(slot $((RANDOM % 250 + 90)) "$1")"
    api POST /api/v1/appointments "$MEMBER_TOKEN" \
      "{\"clientId\":$MEMBER_ID,\"scheduledAt\":\"$at\",\"appointmentTier\":\"QUINZE_STANDARD\",\"serviceType\":\"Smoke\"}"
    if [ "$STATUS" = "201" ]; then APPT_ID="$(field '.id')"; return 0; fi
  done
  fail "não consegui agendar (último status $STATUS): $(body)"
}

step "Sem a chave de acesso a homologação responde 404"
code="$(curl -sS -m 30 -o /dev/null -w '%{http_code}' "$BASE/api/v1/plans")"
[ "$code" = "404" ] || fail "sem chave deveria ser 404, veio $code"
echo "✔ porta de entrada fechada"

step "Saúde (/health): aplicação, versão, build e conexões"
api GET /health; expect 200 "GET /health"
version="$(field '.version // "?"')"
echo "  app: $(field '.app') | versão: $version | build: $(field '.build') | ambiente: $(field '.environment')"
echo "  banco: $(field '.checks.database.status') | fila: $(field '.checks.rabbitmq.status') | status geral: $(field '.status')"
[ "$(field '.checks.database.status')" = "UP" ] || fail "o banco deveria estar UP"
[ "$(field '.checks.rabbitmq.status')" = "UP" ] || fail "a fila (RabbitMQ) deveria estar UP"
if [ -n "$EXPECTED_VERSION" ] && [ "$version" != "$EXPECTED_VERSION" ]; then
  fail "versão esperada $EXPECTED_VERSION, no ar $version"
fi

step "Planos públicos"
api GET /api/v1/plans; expect 200 "GET /api/v1/plans"

step "Login do admin (seed)"
api POST /api/v1/auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
expect 200 "login admin"
ADMIN_TOKEN="$(field '.accessToken // empty')"
[ -n "$ADMIN_TOKEN" ] || fail "sem accessToken do admin"

step "Garantir a confirmação ligada (em produção ela começa desligada; na homologação testamos ligada)"
api GET /api/v1/admin/settings/appointments "$ADMIN_TOKEN"; expect 200 "GET configurações da confirmação"
if [ "$(field '.enabled')" != "true" ]; then
  api PUT /api/v1/admin/settings/appointments "$ADMIN_TOKEN" "$(jq -c '.enabled = true' "$BODY_FILE")"
  expect 200 "liga a confirmação na homologação"
fi

step "Cadastro de um cliente de teste"
email="smoke_$(date +%s)_$RANDOM@teste.local"
api POST /api/v1/auth/register "" "{\"name\":\"Cliente Smoke\",\"email\":\"$email\",\"password\":\"Senha@1234\",\"membershipTier\":\"QUINZE_STANDARD\"}"
expect 201 "registro do cliente"
MEMBER_TOKEN="$(field '.accessToken')"
api GET /api/v1/users/me "$MEMBER_TOKEN"; expect 200 "GET /users/me"
MEMBER_ID="$(field '.id')"

step "Agendar e confirmar"
book 10; appt1="$APPT_ID"; echo "✔ agendamento $appt1 criado"
api POST "/api/v1/appointments/$appt1/confirm" "$MEMBER_TOKEN" "{}"; expect 200 "cliente confirma"
[ "$(field '.status')" = "CONFIRMED" ] || fail "status deveria ser CONFIRMED"

step "Propor remarcação e o admin aprovar"
book 11; appt2="$APPT_ID"
new_at="$(slot $((RANDOM % 250 + 340)) 15)"
api POST "/api/v1/appointments/$appt2/reschedule-request" "$MEMBER_TOKEN" "{\"newDate\":\"$new_at\"}"
expect 200 "cliente propõe remarcação"
[ "$(field '.status')" = "RESCHEDULE_REQUESTED" ] || fail "status deveria ser RESCHEDULE_REQUESTED"
# A homologação acumula agendamentos de execuções anteriores: se o horário proposto já estiver ocupado (422 "Conflito"),
# o admin aprova em outro horário livre (newDate), como o app faz.
api POST "/api/v1/appointments/$appt2/reschedule-approve" "$ADMIN_TOKEN" "{\"durationMinutes\":45}"
for _ in 1 2 3 4 5 6; do
  if [ "$STATUS" = "422" ] && [[ "$(body)" == *Conflito* ]]; then
    echo "  horário proposto ocupado; aprovando em outro horário"
    api POST "/api/v1/appointments/$appt2/reschedule-approve" "$ADMIN_TOKEN" \
      "{\"durationMinutes\":45,\"newDate\":\"$(slot $((RANDOM % 250 + 340)) 15)\"}"
  else
    break
  fi
done
expect 200 "admin aprova com duração de 45 min"
[ "$(field '.status')" = "CONFIRMED" ] || fail "após aprovar deveria ser CONFIRMED"

step "Links de calendário (Google Agenda e .ics)"
api GET "/api/v1/appointments/$appt2/calendar-links" "$MEMBER_TOKEN"; expect 200 "GET calendar-links"
google_url="$(field '.googleCalendarUrl')"
case "$google_url" in https://calendar.google.com/*) echo "✔ link do Google Agenda gerado" ;; *) fail "link do Google inesperado: $google_url" ;; esac
ics_url="$(field '.icsUrl')"
ics_token="${ics_url#*token=}"
ics_code="$(curl -sS -m 30 -o "$BODY_FILE" -w '%{http_code}' "$BASE/appointment-calendar?token=$ics_token")"
[ "$ics_code" = "200" ] || fail "download público do .ics deveria dar 200, deu $ics_code"
grep -q "BEGIN:VEVENT" "$BODY_FILE" || fail "o .ics não tem VEVENT"
echo "✔ .ics público baixa sem chave e traz o evento"

step "Cancelamento pelo cliente"
book 16; appt3="$APPT_ID"
code="$(curl -sS -m 30 -o /dev/null -w '%{http_code}' -X DELETE -H "X-Staging-Key: $KEY" -H "Authorization: Bearer $MEMBER_TOKEN" "$BASE/api/v1/appointments/$appt3")"
[ "$code" = "204" ] || fail "cancelar deveria dar 204, deu $code"
echo "✔ cliente cancela"

step "Configurações do fluxo de confirmação (admin)"
api GET /api/v1/admin/settings/appointments "$ADMIN_TOKEN"; expect 200 "GET configurações"
[ "$(field '.confirmationRequestOffset')" = "90" ] || fail "padrão de 90 min esperado"

printf '\n✅ Homologação OK (versão %s)\n' "$version"
