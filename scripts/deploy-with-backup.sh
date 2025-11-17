#!/bin/bash
# Configura o agendamento dos backups e sobe os servicos via Docker Compose.
# Uso: ./scripts/deploy-with-backup.sh [intervalo_horas] [quantidade_para_manter]

set -euo pipefail

INTERVAL_HOURS=${1:-6}
KEEP_COUNT=${2:-3}

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"

# Aciona o agendamento adequado ao sistema operacional
case "$(uname -s)" in
    Linux*)
        "$DIR/schedule-backup-cron.sh" "$INTERVAL_HOURS" "$KEEP_COUNT"
        ;;
    Darwin*)
    echo "[WARN] macOS detectado; configure o agendamento manualmente ou instale launchd plist equivalente."
        ;;
    MINGW*|MSYS*|CYGWIN*)
        printf '%s\n' "[INFO] Ambiente Windows detectado. Execute 'scripts\\schedule-backup-windows.bat ${INTERVAL_HOURS} ${KEEP_COUNT}' em um prompt elevado." \
                       "[INFO] Prosseguindo apenas com 'docker compose up -d'."
        ;;
    *)
    echo "[WARN] Sistema nao reconhecido. Pulei configuracao automatica do agendamento."
        ;;
esac

# Verifica comando docker compose ou docker-compose
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
else
    echo "[ERRO] docker compose ou docker-compose nao disponivel no PATH."
    exit 1
fi

cd "$ROOT"
"${DOCKER_COMPOSE[@]}" -f compose.yaml up -d

echo "[OK] Servicos iniciados com backups agendados (quando suportado)."
