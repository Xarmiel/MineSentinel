#!/usr/bin/env bash
# Comprueba que el backend responde y que el cliente de visión sigue enviando frames.
# Sale con código 1 si algo falla, para que cron (MAILTO) o un monitor lo notifique.
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_MS_SIN_FRAME="${MAX_MS_SIN_FRAME:-30000}"
fallo=0
CURL_AUTH=()
[ -n "${MINESENTINEL_API_KEY:-}" ] && CURL_AUTH=(-H "X-API-Key: $MINESENTINEL_API_KEY")

if ! curl -fsS --max-time 5 "${CURL_AUTH[@]}" "$BASE_URL/api/v1/aforo/tiempo-real" > /dev/null; then
  echo "$(date '+%F %T') ERROR backend no responde en $BASE_URL"
  exit 1
fi

estado=$(curl -fsS --max-time 5 "${CURL_AUTH[@]}" "$BASE_URL/api/v1/vision/estado") || estado=""
ms=$(printf '%s' "$estado" | grep -oE '"msDesdeUltimoFrameStream":-?[0-9]+' | cut -d: -f2)

if [ -z "$ms" ] || [ "$ms" -lt 0 ] || [ "$ms" -gt "$MAX_MS_SIN_FRAME" ]; then
  echo "$(date '+%F %T') ERROR cliente de visión sin frames (msDesdeUltimoFrameStream=${ms:-?})"
  fallo=1
else
  echo "$(date '+%F %T') OK backend y visión (último frame hace ${ms} ms)"
fi
exit $fallo
