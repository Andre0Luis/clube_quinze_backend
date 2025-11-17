#!/bin/bash
# Adiciona (ou atualiza) entrada no crontab para rodar o backup periodicamente.
# Uso: ./scripts/schedule-backup-cron.sh [intervalo_horas] [quantidade_para_manter]

set -euo pipefail

INTERVAL_HOURS=${1:-6}
KEEP_COUNT=${2:-3}

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
LOG="$ROOT/backups/backup-cron.log"

mkdir -p "$ROOT/backups"

CRON_LINE="0 */$INTERVAL_HOURS * * * cd \"$ROOT\" && ./scripts/backup-rotate.sh $KEEP_COUNT >> \"$LOG\" 2>&1"

( crontab -l 2>/dev/null | grep -v "scripts/backup-rotate.sh" ; echo "$CRON_LINE" ) | crontab -

echo "[OK] Crontab atualizado: executará a cada $INTERVAL_HOURS h mantendo $KEEP_COUNT backups. Logs em $LOG"

