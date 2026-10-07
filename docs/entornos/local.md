# Entorno local

> **Estado (2026-10-07):** el entorno local dedicado (perfil `local`, datos de demo realistas,
> Mailpit y un solo comando) está planificado en el sprint 01, tarea 3. Mientras tanto se usa la
> forma actual que se describe abajo. Cuando la tarea esté hecha, este documento se reemplaza.

## Forma actual

El mismo `docker-compose.yml` de producción, con un `.env` local (copiado de `.env.template`).

1. En `.env`, para no tocar servicios reales:
   - `SEED_TEST_ACCOUNTS=true`: crea las cuentas de prueba (contraseña `admin123`).
   - `PAYMENT_SIMULATION_ENABLED=true`: los pagos usan un simulador en lugar de Mercado Pago.
   - `MERCADOPAGO_ENABLED=false`, `GOOGLE_CALENDAR_ENABLED=false`, `QBI2_RECIPE_ENABLED=false`,
     `ARCA_ENABLED=false`.
   - Sin `RESEND_API_KEY`: los mails no se envían, quedan en el log del backend.
2. `docker compose up -d --build` levanta base, backend (`localhost:8081`) y frontend.
3. Para cambios de frontend, conviene `cd frontend && npm run dev` (`localhost:5173`), que usa el
   backend de `localhost:8081`.

**Cuidado:** si el `.env` local tiene credenciales reales de Mercado Pago o Resend y los flags de
arriba no están puestos, las acciones locales pueden generar cobros o mails reales.

### Cuentas de prueba (solo con `SEED_TEST_ACCOUNTS=true`)

| Cuenta | Rol |
|---|---|
| `admin@tranqui.com` | Administrador |
| `medico.verificado@gmail.com`, `profesional.test@tranqui.com` | Profesionales verificados con suscripción activa y agenda |
| `medico.sinverificar@gmail.com` | Profesional sin verificar |
| `paciente.completo@gmail.com`, `paciente.test@tranqui.com` | Pacientes con perfil completo |
| `paciente.sindatos@gmail.com` | Paciente sin datos |
| `admin.test@tranqui.com` | Segundo administrador |

### Mails y códigos de verificación

Sin `RESEND_API_KEY`, los códigos de verificación de email y de recuperación de contraseña se ven
en el log del backend (`docker logs tranqui-backend`) o en la columna `codigo_verificacion` /
`reset_password_code` de la tabla `usuario`.

## Forma planificada (sprint 01, tarea 3)

- Perfil de Spring `local` con todas las integraciones simuladas por defecto.
- Datos de demo: psicólogos y psiquiatras de distinto tipo, turnos pasados, de hoy y futuros
  (pagos, pendientes, cancelados), chats, documentos pedidos, notas y suscripciones en distintos
  estados.
- Mailpit como bandeja local para ver todos los mails (`localhost:8025`).
- Login con Google oculto en entornos de prueba.
- Base, volumen y puertos propios, para no chocar con otros contenedores.
- Nada de esto se activa en producción: todo apagado por defecto y el seeder exige el perfil
  `local` más un flag explícito.
