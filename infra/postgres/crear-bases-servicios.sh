#!/bin/sh
# Crea la base y el usuario de cada servicio en la instancia de PostgreSQL compartida
# (sección 3 del plan: una instancia, una base y un usuario por servicio).
#
# Es idempotente: si el usuario o la base ya existen, solo actualiza la contraseña. Por eso se
# puede correr en cada arranque (contenedor db-servicios del compose) y sirve también para
# volúmenes que ya existían, donde los scripts de /docker-entrypoint-initdb.d no vuelven a correr.
#
# Variables:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE  conexión como dueño de la instancia
#   SERVICIOS                                      lista separada por espacios (ej. "notificaciones")
#   DB_PASSWORD_<SERVICIO>                         contraseña de cada uno (ej. DB_PASSWORD_NOTIFICACIONES),
#                                                  sin comillas simples
set -eu

for servicio in ${SERVICIOS:-}; do
  variable="DB_PASSWORD_$(echo "$servicio" | tr '[:lower:]' '[:upper:]')"
  eval "clave=\${$variable:-}"
  if [ -z "$clave" ]; then
    echo "Falta $variable para la base de $servicio" >&2
    exit 1
  fi
  case "$clave" in
    *"'"*) echo "$variable no puede tener comillas simples" >&2; exit 1 ;;
  esac

  psql -v ON_ERROR_STOP=1 -q <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$servicio') THEN
    CREATE ROLE $servicio LOGIN PASSWORD '$clave';
  ELSE
    ALTER ROLE $servicio WITH LOGIN PASSWORD '$clave';
  END IF;
END
\$\$;
SQL

  if [ "$(psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$servicio'")" != "1" ]; then
    psql -v ON_ERROR_STOP=1 -q -c "CREATE DATABASE $servicio OWNER $servicio"
    echo "Base $servicio creada"
  else
    echo "Base $servicio ya existía"
  fi
  # Ningún otro usuario se conecta a la base de un servicio (sección 6.7).
  psql -v ON_ERROR_STOP=1 -q -c "REVOKE ALL ON DATABASE $servicio FROM PUBLIC"
done
