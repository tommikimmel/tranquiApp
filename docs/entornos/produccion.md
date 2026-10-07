# Producción

- **Dominio:** https://tranquisalud.com
- **Servidor:** un VPS con Docker. Los datos de acceso no se documentan acá (ver
  [secretos.md](secretos.md)).
- **Proxy y TLS:** Traefik, compartido con otros servicios del VPS. Enruta `/api` y `/ws-tranqui`
  al backend y el resto al frontend.

## Contenedores

| Contenedor | Qué es |
|---|---|
| `tranqui-frontend` | nginx que sirve el build de React (`frontend/nginx.conf`) |
| `tranqui-backend` | Spring Boot |
| `tranqui-db` | PostgreSQL 15 con volumen persistente |
| `tranqui-bot`, `tranqui-bot-worker`, `wsp-db`, `wsp-redis` | Bot de WhatsApp (proyecto aparte, fuera de este repositorio; implementación a futuro) |

## Configuración

- Variables en un `.env` (nombres en `.env.template`). Hoy `deploy.js` sube el `.env` de la
  máquina que despliega y fuerza `SEED_TEST_ACCOUNTS=false`. Está planificado que el `.env` viva
  solo en el VPS (sprint 01, tarea 5).
- Perfil de Spring: `prod` por defecto (`SPRING_PROFILES_ACTIVE`).
- Mercado Pago en modo real (no sandbox): cobros y débitos son reales.
- Recetas (QBI2) y facturación (ARCA) deshabilitadas.

## Base de datos

- Esquema: Hibernate `ddl-auto: update` crea lo nuevo; Flyway aplica las migraciones de
  `backend/src/main/resources/db/migration` (la base existente se tomó como versión 1).
- **Nadie toca la base de producción sin aprobación explícita del dueño para esa acción.**

## Deploy

Ver [docs/flujo/releases.md](../flujo/releases.md).

## Modo mantenimiento

Planificado (sprint 01, tarea 4): un flag que, mientras está activo, hace que el sitio muestre
solo una página de mantenimiento. Funciona aunque el backend esté apagado.
