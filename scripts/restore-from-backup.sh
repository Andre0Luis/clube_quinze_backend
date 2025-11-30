#!/bin/bash
set -euo pipefail

# Executado automaticamente pelo entrypoint do Postgres quando o diretório de dados está vazio.
# Procura pelo dump mais recente em /backups e restaura antes da aplicação subir.

BACKUP_DIR="/backups"
DEFAULT_DB=${POSTGRES_DB:-postgres}
DEFAULT_USER=${POSTGRES_USER:-postgres}

if [ ! -d "$BACKUP_DIR" ]; then
  echo "[restore-from-backup] Diretório de backups não está montado. Pulando restauração."
  exit 0
fi

mapfile -t backups < <(ls -1t "$BACKUP_DIR"/backup_*.sql "$BACKUP_DIR"/backup_*.sql.gz 2>/dev/null || true)

if [ ${#backups[@]} -eq 0 ]; then
  echo "[restore-from-backup] Nenhum backup encontrado. Nada a restaurar."
  exit 0
fi

LATEST="${backups[0]}"

echo "[restore-from-backup] Restaurando do arquivo: $LATEST"

if [[ "$LATEST" == *.gz ]]; then
  gunzip -c "$LATEST" | psql -v ON_ERROR_STOP=1 -U "$DEFAULT_USER" "$DEFAULT_DB"
else
  psql -v ON_ERROR_STOP=1 -U "$DEFAULT_USER" "$DEFAULT_DB" < "$LATEST"
fi

echo "[restore-from-backup] Restauração concluída."
