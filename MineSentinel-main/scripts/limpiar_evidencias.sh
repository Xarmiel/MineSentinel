#!/usr/bin/env bash
# Borra snapshots (evidencias) más antiguos que la retención. Correr DESPUÉS de backup_bd.sh,
# porque la alerta en BD sigue apuntando al archivo y su evidencia queda solo en el respaldo.
set -euo pipefail

SNAPSHOTS_DIR="${SNAPSHOTS_DIR:-./uploads/snapshots}"
RETENCION_DIAS="${RETENCION_DIAS_EVIDENCIAS:-90}"

[ -d "$SNAPSHOTS_DIR" ] || { echo "No existe $SNAPSHOTS_DIR"; exit 1; }

borrados=$(find "$SNAPSHOTS_DIR" -maxdepth 1 -type f \( -name '*.jpg' -o -name '*.jpeg' -o -name '*.png' \) \
  -mtime +"$RETENCION_DIAS" -print -delete | wc -l)

echo "$(date '+%F %T') evidencias borradas: $borrados (más de $RETENCION_DIAS días)"
