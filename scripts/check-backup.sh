#!/bin/bash
set -euo pipefail

BACKUP_DIR=${1:-./backups}

if [ ! -d "$BACKUP_DIR" ]; then
    echo "Diretorio de backups '$BACKUP_DIR' nao encontrado" >&2
    exit 1
fi

shopt -s nullglob
files=(
    "$BACKUP_DIR"/*.sql
    "$BACKUP_DIR"/*.sql.gz
    "$BACKUP_DIR"/*.dump
    "$BACKUP_DIR"/*.backup
)
shopt -u nullglob

if [ ${#files[@]} -eq 0 ]; then
    echo "Nenhum backup encontrado em $BACKUP_DIR" >&2
    exit 2
fi

latest=$(ls -1t "${files[@]}" | head -n 1)
echo "Backup mais recente: $latest"
exit 0
