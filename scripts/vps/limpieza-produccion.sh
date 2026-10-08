#!/usr/bin/env bash
# Corre EN EL VPS. Limpieza única de la base de producción antes de abrir la app al público
# (spec: docs/specs/2026-10-07-limpieza-produccion.md). IRREVERSIBLE y sin backup, por decisión
# del dueño.
#
# Uso:
#   limpieza-produccion.sh inventario          solo lectura: qué hay y qué se borraría
#   limpieza-produccion.sh ejecutar BORRAR     cancela los débitos de Mercado Pago y vacía la base
#
# Se conservan: el usuario admin@tranquisalud.com y las tablas plans, features, plan_features y
# flyway_schema_history. Todo lo demás se vacía.
set -euo pipefail

MODO="${1:-}"
DIR="${DIR:-/srv/tranqui}"
ENV_FILE="$DIR/.env"
COMPOSE=(docker compose -p app --env-file "$ENV_FILE" -f "$DIR/docker-compose.yml")
DB="${DB:-tranqui-db}"
ADMIN="${ADMIN:-admin@tranquisalud.com}"
CONSERVADAS="'plans','features','plan_features','flyway_schema_history','usuario'"
MP_API=https://api.mercadopago.com/preapproval

psql_db() {
  docker exec -i "$DB" sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F "|"'
}

mp_token() {
  { grep -E '^MP_ADMIN_ACCESS_TOKEN=' "$ENV_FILE" || true; } | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'
}

# Preapprovals de Mercado Pago guardados en la base (sin filtrar por estado local: el estado que
# vale es el de Mercado Pago).
preapprovals() {
  echo "select distinct mp_preapproval_id from subscriptions where coalesce(mp_preapproval_id, '') <> '';" | psql_db
}

estado_mp() {
  local r
  r="$(curl -fsS --max-time 20 -H "Authorization: Bearer $TOKEN" "$MP_API/$1" 2>/dev/null)" || { echo "error"; return; }
  echo "$r" | grep -o '"status":"[a-z_]*"' | head -1 | cut -d'"' -f4
}

inventario() {
  echo "=== Filas por tabla ==="
  echo "select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' order by 1;" \
    | psql_db | while read -r t; do
      printf '%-32s %s\n' "$t" "$(echo "select count(*) from \"$t\";" | psql_db)"
    done
  echo
  echo "=== Usuarios que se borran (por rol) ==="
  echo "select rol, count(*) from usuario where email <> '$ADMIN' group by rol order by rol;" | psql_db
  echo "=== Usuario que queda ==="
  echo "select email || ' (' || rol || ')' from usuario where email = '$ADMIN';" | psql_db
  echo
  echo "=== Débitos automáticos en Mercado Pago ==="
  local ids
  ids="$(preapprovals)"
  if [ -z "$ids" ]; then echo "ninguno"; return; fi
  while read -r id; do
    echo "$id: $(estado_mp "$id")"
  done <<< "$ids"
}

cancelar_preapprovals() {
  local ids estado
  ids="$(preapprovals)"
  [ -n "$ids" ] || { echo "Sin débitos automáticos para cancelar."; return; }
  while read -r id; do
    estado="$(estado_mp "$id")"
    case "$estado" in
      cancelled) echo "$id: ya estaba cancelado" ;;
      error) echo "!!! $id: no se pudo consultar en Mercado Pago. No se borra nada." >&2; exit 1 ;;
      *)
        curl -fsS --max-time 20 -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
          -d '{"status":"cancelled"}' "$MP_API/$id" > /dev/null \
          || { echo "!!! $id: Mercado Pago rechazó la cancelación. No se borra nada." >&2; exit 1; }
        [ "$(estado_mp "$id")" = cancelled ] \
          || { echo "!!! $id: sigue sin figurar cancelado. No se borra nada." >&2; exit 1; }
        echo "$id: cancelado (estaba $estado)" ;;
    esac
  done <<< "$ids"
}

vaciar_base() {
  psql_db <<SQL
begin;
do \$\$
declare
  tablas text;
begin
  if (select count(*) from usuario where email = '$ADMIN') <> 1 then
    raise exception 'No existe $ADMIN: se cancela.';
  end if;
  select string_agg(format('%I', table_name), ', ') into tablas
    from information_schema.tables
   where table_schema = 'public' and table_type = 'BASE TABLE'
     and table_name not in ($CONSERVADAS);
  if tablas is not null then
    execute 'truncate table ' || tablas || ' restart identity cascade';
  end if;
  delete from usuario where email <> '$ADMIN';
  if (select count(*) from usuario) <> 1 then
    raise exception 'Quedaría más de un usuario: se cancela.';
  end if;
  if (select count(*) from plans) = 0 then
    raise exception 'plans quedó vacía: se cancela.';
  end if;
end
\$\$;
commit;
SQL
}

# Permite cargar las funciones sin ejecutar nada (pruebas en local).
[ "${BASH_SOURCE[0]}" = "$0" ] || return 0

case "$MODO" in
  inventario)
    TOKEN="$(mp_token)"
    [ -n "$TOKEN" ] || echo "Aviso: falta MP_ADMIN_ACCESS_TOKEN, no se consultan los débitos."
    inventario
    ;;
  ejecutar)
    [ "${2:-}" = BORRAR ] || { echo "Para ejecutar: $0 ejecutar BORRAR" >&2; exit 2; }
    TOKEN="$(mp_token)"
    [ -n "$TOKEN" ] || { echo "Falta MP_ADMIN_ACCESS_TOKEN en $ENV_FILE: no se pueden cancelar los débitos." >&2; exit 2; }
    [ -f "$DIR/mantenimiento/activo" ] || { echo "El modo mantenimiento no está activo: prendelo antes." >&2; exit 2; }

    echo "=== 1/4 Cancelando débitos automáticos en Mercado Pago ==="
    cancelar_preapprovals
    echo "=== 2/4 Frenando el backend ==="
    "${COMPOSE[@]}" stop tranqui-backend
    echo "=== 3/4 Vaciando la base (una transacción) ==="
    if ! vaciar_base; then
      echo "!!! Falló la transacción: la base quedó como estaba. Levantando el backend." >&2
      "${COMPOSE[@]}" start tranqui-backend
      exit 1
    fi
    echo "=== 4/4 Levantando el backend ==="
    "${COMPOSE[@]}" start tranqui-backend
    for _ in $(seq 1 36); do
      if curl -fsS -o /dev/null --max-time 10 https://tranquisalud.com/api/health; then
        echo "=== Limpieza terminada. Backend arriba. El modo mantenimiento sigue prendido. ==="
        inventario
        exit 0
      fi
      sleep 5
    done
    echo "!!! El backend no respondió: revisar con docker compose -p app logs tranqui-backend" >&2
    exit 1
    ;;
  *)
    echo "Uso: $0 inventario | $0 ejecutar BORRAR" >&2
    exit 2
    ;;
esac
