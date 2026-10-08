#!/usr/bin/env bash
# Corre EN EL VPS durante un deploy (lo invocan .github/workflows/deploy.yml y deploy.js).
# Baja las imágenes de la versión pedida desde GHCR, recrea los contenedores, verifica que el sitio
# y la API respondan y, si no, vuelve a la versión anterior.
#
# Uso: deploy-remoto.sh vX.Y.Z
#
# Importante:
# - El proyecto de Docker se llama "app" (-p app): así se llamaba cuando el deploy corría desde
#   /app, y de ese nombre depende el volumen de la base de producción (app_pgdata). Cambiarlo
#   crearía una base nueva y vacía.
# - Las variables de producción viven en /srv/tranqui/.env (fuera de /app y fuera del repo).
set -euo pipefail

VERSION="${1:?Falta la versión (ej. v1.1.0)}"
DIR=/srv/tranqui
ENV_FILE="$DIR/.env"
COMPOSE=(docker compose -p app --env-file "$ENV_FILE" -f "$DIR/docker-compose.yml")
SITIO=https://tranquisalud.com

[ -f "$ENV_FILE" ] || { echo "No existe $ENV_FILE: hace falta mover el .env de producción antes del primer deploy." >&2; exit 2; }
if grep -Eq '^SEED_TEST_ACCOUNTS=true' "$ENV_FILE"; then
  echo "SEED_TEST_ACCOUNTS=true en el .env de producción: se cancela el deploy (crearía cuentas con contraseña conocida)." >&2
  exit 2
fi
docker volume inspect app_pgdata >/dev/null 2>&1 || { echo "No existe el volumen app_pgdata: se cancela para no crear una base vacía." >&2; exit 2; }

ANTERIOR="$(cat "$DIR/version-actual" 2>/dev/null || true)"
echo "=== Deploy $VERSION (versión anterior: ${ANTERIOR:-ninguna registrada}) ==="

levantar() {
  TRANQUI_VERSION="$1" "${COMPOSE[@]}" pull tranqui-backend tranqui-frontend
  TRANQUI_VERSION="$1" "${COMPOSE[@]}" up -d --remove-orphans
}

verificar() {
  for _ in $(seq 1 36); do
    if curl -fsS -o /dev/null --max-time 10 "$SITIO/" && curl -fsS -o /dev/null --max-time 10 "$SITIO/api/health"; then
      return 0
    fi
    sleep 5
  done
  return 1
}

levantar "$VERSION"
if verificar; then
  echo "$VERSION" > "$DIR/version-actual"
  echo "$(date -Iseconds) $VERSION ok" >> "$DIR/deploys.log"
  docker image prune -f >/dev/null
  echo "=== $VERSION desplegada y verificada ==="
  exit 0
fi

echo "!!! $VERSION no respondió a tiempo." >&2
echo "$(date -Iseconds) $VERSION FALLÓ" >> "$DIR/deploys.log"
if [ -n "$ANTERIOR" ] && [ "$ANTERIOR" != "$VERSION" ]; then
  echo "=== Volviendo a $ANTERIOR ===" >&2
  levantar "$ANTERIOR"
  if verificar; then
    echo "=== Se volvió a $ANTERIOR ===" >&2
  else
    echo "!!! $ANTERIOR tampoco responde: revisar a mano (docker compose -p app logs)." >&2
  fi
fi
exit 1
