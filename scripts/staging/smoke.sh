#!/usr/bin/env bash
# Teste de fumaça da homologação: exercita o fluxo real pela API (agendar, confirmar, propor remarcação,
# aprovar, cancelar e ler as configurações). Sai com erro na primeira falha.
#
# Uso: scripts/staging/smoke.sh <URL_BASE> <CHAVE_DE_ACESSO> [versão-esperada]
# Variáveis opcionais: ADMIN_EMAIL, ADMIN_PASSWORD (padrão: admin criado pelo seed da aplicação).
set -euo pipefail

BASE="${1:?uso: smoke.sh <URL_BASE> <CHAVE_DE_ACESSO> [versão-esperada]}"
KEY="${2:?informe a chave de acesso}"
EXPECTED_VERSION="${3:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-aluis283@gmail.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-luizinho@01}"

command -v jq >/dev/null || { echo "ERRO: jq é necessário" >&2; exit 1; }

step() { printf '\n▶ %s\n' "$*"; }
fail() { printf '✖ %s\n' "$*" >&2; exit 1; }

# api METHOD PATH [TOKEN] [JSON] -> imprime o corpo; guarda o status em $STATUS
api() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-sS -m 30 -X "$method" -H "X-Staging-Key: $KEY" -H "Content-Type: application/json" -o /tmp/smoke.body -w '%{http_code}')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  STATUS="$(curl "${args[@]}" "$BASE$path")"
  cat /tmp/smoke.body
}

expect() { # expect <status esperado> <descrição>
  [ "$STATUS" = "$1" ] || fail "$2 — esperado HTTP $1, veio $STATUS: $(cat /tmp/smoke.body)"
  echo "✔ $2"
}

step "Sem a chave de acesso a homologação responde 404"
code="$(curl -sS -m 30 -o /dev/null -w '%{http_code}' "$BASE/api/v1/plans")"
[ "$code" = "404" ] || fail "sem chave deveria ser 404, veio $code"
echo "✔ porta de entrada fechada"

step "Versão publicada"
info="$(api GET /actuator/info)"; expect 200 "GET /actuator/info"
version="$(echo "$info" | jq -r '.app.version // "?"')"
echo "  versão no ar: $version"
[ -z "$EXPECTED_VERSION" ] || [ "$version" = "$EXPECTED_VERSION" ] || fail "versão esperada $EXPECTED_VERSION, no ar $version"

step "Planos públicos"
api GET /api/v1/plans >/dev/null; expect 200 "GET /api/v1/plans"

step "Login do admin (seed)"
admin_token="$(api POST /api/v1/auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.accessToken // empty')"
expect 200 "login admin"; [ -n "$admin_token" ] || fail "sem accessToken do admin"

step "Cadastro de um cliente de teste"
email="smoke_$(date +%s)_$RANDOM@teste.local"
member_json="$(api POST /api/v1/auth/register "" "{\"name\":\"Cliente Smoke\",\"email\":\"$email\",\"password\":\"Senha@1234\",\"membershipTier\":\"QUINZE_STANDARD\"}")"
expect 201 "registro do cliente"
member_token="$(echo "$member_json" | jq -r '.accessToken')"
api GET /api/v1/users/me "$member_token" >/dev/null; expect 200 "GET /users/me"
member_id="$(jq -r '.id' /tmp/smoke.body)"

# Dia aleatório distante e hora cheia, tentando outros se já estiver ocupado.
slot() { python3 - "$1" "$2" <<'PY'
import datetime, sys
days, hour = int(sys.argv[1]), int(sys.argv[2])
d = datetime.date.today() + datetime.timedelta(days=days)
print(f"{d.isoformat()}T{hour:02d}:00:00")
PY
}
book() { # book <hora> -> imprime o id do agendamento
  for _ in 1 2 3 4 5 6; do
    local at; at="$(slot $((RANDOM % 250 + 90)) "$1")"
    api POST /api/v1/appointments "$member_token" \
      "{\"clientId\":$member_id,\"scheduledAt\":\"$at\",\"appointmentTier\":\"QUINZE_STANDARD\",\"serviceType\":\"Smoke\"}" >/dev/null
    if [ "$STATUS" = "201" ]; then jq -r '.id' /tmp/smoke.body; return 0; fi
  done
  fail "não consegui agendar (último status $STATUS)"
}

step "Agendar e confirmar"
appt1="$(book 10)"; echo "✔ agendamento $appt1 criado"
api POST "/api/v1/appointments/$appt1/confirm" "$member_token" "{}" >/dev/null; expect 200 "cliente confirma"
[ "$(jq -r '.status' /tmp/smoke.body)" = "CONFIRMED" ] || fail "status deveria ser CONFIRMED"

step "Propor remarcação e o admin aprovar"
appt2="$(book 11)"
new_at="$(slot $((RANDOM % 250 + 340)) 15)"
api POST "/api/v1/appointments/$appt2/reschedule-request" "$member_token" "{\"newDate\":\"$new_at\"}" >/dev/null
expect 200 "cliente propõe remarcação"
[ "$(jq -r '.status' /tmp/smoke.body)" = "RESCHEDULE_REQUESTED" ] || fail "status deveria ser RESCHEDULE_REQUESTED"
api POST "/api/v1/appointments/$appt2/reschedule-approve" "$admin_token" "{\"durationMinutes\":45}" >/dev/null
expect 200 "admin aprova com duração de 45 min"
[ "$(jq -r '.status' /tmp/smoke.body)" = "CONFIRMED" ] || fail "após aprovar deveria ser CONFIRMED"

step "Cancelamento pelo cliente"
appt3="$(book 16)"
curl -sS -m 30 -o /dev/null -w '%{http_code}' -X DELETE -H "X-Staging-Key: $KEY" -H "Authorization: Bearer $member_token" "$BASE/api/v1/appointments/$appt3" > /tmp/smoke.status
[ "$(cat /tmp/smoke.status)" = "204" ] || fail "cancelar deveria dar 204, deu $(cat /tmp/smoke.status)"
echo "✔ cliente cancela"

step "Configurações do fluxo de confirmação (admin)"
api GET /api/v1/admin/settings/appointments "$admin_token" >/dev/null; expect 200 "GET configurações"
[ "$(jq -r '.confirmationRequestOffset' /tmp/smoke.body)" = "90" ] || fail "padrão de 90 min esperado"

printf '\n✅ Homologação OK (versão %s)\n' "$version"
