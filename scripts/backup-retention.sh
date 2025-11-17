#!/bin/bash
# Remove backups antigos conforme política de retenção
# Uso: ./backup-retention.sh [dias]

RETENTION_DAYS=${1:-7}
BACKUP_DIR="backups"

if [ ! -d "$BACKUP_DIR" ]; then
  echo "Diretório $BACKUP_DIR não existe. Nada a fazer."
  exit 0
fi

find "$BACKUP_DIR" -name "backup_*.sql.gz" -mtime +$RETENTION_DAYS -print -delete

