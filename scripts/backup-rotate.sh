#!/bin/bash
set -euo pipefail

# Executa o backup e mantém somente os N arquivos mais recentes.
# Uso: ./scripts/backup-rotate.sh [quantidade_para_manter]

KEEP_COUNT=${1:-3}

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
BACKUP_DIR="$ROOT/backups"

mkdir -p "$BACKUP_DIR"

"$DIR/backup-database.sh"

declare -a files=()

if command -v python3 >/dev/null 2>&1; then
	mapfile -t files < <(python3 - "$BACKUP_DIR" <<'PY'
import sys
import glob
import os

root = sys.argv[1]
patterns = ("backup_*.sql", "backup_*.sql.gz", "backup_*.zip")
files = []
for pattern in patterns:
		files.extend(glob.glob(os.path.join(root, pattern)))

files.sort(key=os.path.getmtime, reverse=True)

for path in files:
		print(path)
PY
	)
else
	if find "$BACKUP_DIR" -maxdepth 1 -type f -name 'backup_*' -printf '' >/dev/null 2>&1; then
		mapfile -t files < <(find "$BACKUP_DIR" -maxdepth 1 -type f \
			\( -name "backup_*.sql" -o -name "backup_*.sql.gz" -o -name "backup_*.zip" \) \
			-printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
	else
		set +e
		mapfile -t files < <(ls -1t "$BACKUP_DIR"/backup_*.sql "$BACKUP_DIR"/backup_*.sql.gz "$BACKUP_DIR"/backup_*.zip 2>/dev/null)
		status=$?
		set -e
		if [ ${status:-0} -ne 0 ]; then
			files=()
		fi
	fi
fi

total=${#files[@]}

if (( total > KEEP_COUNT )); then
	echo "[INFO] Mantendo os $KEEP_COUNT backups mais recentes e removendo $((total-KEEP_COUNT)) antigos"
	for f in "${files[@]:KEEP_COUNT}"; do
		echo "[DEL] $f"
		rm -f -- "$f"
	done
else
	echo "[INFO] Nada para rotacionar. Total de backups: $total (limite $KEEP_COUNT)"
fi
