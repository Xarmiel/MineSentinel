#!/usr/bin/env bash
# Respaldo diario: volcado de la BD PostgreSQL (Supabase) + evidencias (snapshots).
# Usa las mismas variables que application.properties: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD.
set -euo pipefail

: "${DB_HOST:?Falta DB_HOST}" "${DB_USERNAME:?Falta DB_USERNAME}" "${DB_PASSWORD:?Falta DB_PASSWORD}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/minesentinel-backups}"
SNAPSHOTS_DIR="${SNAPSHOTS_DIR:-./uploads/snapshots}"
RETENCION_DIAS="${RETENCION_DIAS:-14}"

fecha=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"

PGPASSWORD="$DB_PASSWORD" PGSSLMODE=require pg_dump \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
  --schema=public --no-owner -Fc -f "$BACKUP_DIR/bd-$fecha.dump"

if [ -d "$SNAPSHOTS_DIR" ]; then
  tar -czf "$BACKUP_DIR/snapshots-$fecha.tar.gz" -C "$SNAPSHOTS_DIR" .
fi

# Rotación: borra solo respaldos propios más antiguos que la retención.
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'bd-*.dump' -o -name 'snapshots-*.tar.gz' \) \
  -mtime +"$RETENCION_DIAS" -delete

echo "$(date '+%F %T') backup OK -> $BACKUP_DIR (bd-$fecha.dump)"
