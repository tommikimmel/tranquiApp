# Entorno local

Levanta la app completa en tu máquina, con datos de demo realistas y todas las integraciones
externas simuladas. Nada de lo que se haga acá genera cobros, mails reales ni llamadas a Google.

## Requisitos

- Docker (con Docker Compose v2).
- Node.js (solo para correr el script; el frontend corre dentro de Docker).

## Comandos (desde la raíz del repo)

| Comando | Qué hace |
|---|---|
| `npm run local` | Verifica los puertos, reconstruye y levanta todo |
| `npm run local:logs` | Logs del backend |
| `npm run local:down` | Frena el entorno (conserva la base local) |
| `npm run local:reset` | Frena el entorno, **borra la base local** y vuelve a levantar con datos de demo nuevos |

La primera vez tarda unos minutos (compila el backend e instala las dependencias del frontend).

### Verificación de puertos

Antes de cada rebuild, `npm run local` revisa los puertos 5433, 8081, 5173, 8025 y 1025. Si alguno
está ocupado por algo que no es este entorno (un contenedor de otro proyecto o un proceso), te
muestra qué es y **te pregunta si puede frenarlo** (`docker stop` para contenedores, que no borra
sus datos). Si respondés que no, aborta sin tocar nada. Sin una terminal interactiva, aborta y
lista los puertos ocupados.

## Servicios

| Servicio | URL | Qué es |
|---|---|---|
| App | http://localhost:5173 | Frontend con Vite en modo desarrollo (recarga en caliente al editar `frontend/src`) |
| Backend | http://localhost:8081 | Spring Boot con el perfil `local` |
| Mails | http://localhost:8025 | Mailpit: acá llegan todos los mails que manda la app |
| Base | `localhost:5433` | PostgreSQL (`tranqui` / `tranqui-local`, base `tranqui_local`) |

El entorno está definido en `docker-compose.local.yml` y **no lee el `.env`**: usa valores
ficticios propios. La configuración del backend está en
`backend/src/main/resources/application-local.yml`.

## Qué está simulado

| Integración | En local |
|---|---|
| Mercado Pago (cobros) | Simulador de pagos: en el checkout aparece "Simular Pago Exitoso" |
| Mercado Pago (vincular cuenta) | Vinculación simulada desde Configuración, Integraciones |
| Google Calendar | Vinculación simulada; los turnos online reciben un link de Meet ficticio |
| Login con Google | Oculto (`VITE_GOOGLE_LOGIN=false`); se ingresa con email y contraseña |
| Mails (Resend) | Se mandan por SMTP a Mailpit |
| Recetas (QBI2) y facturación (ARCA) | Apagadas |

## Datos de demo

Los crea `LocalDemoSeeder` la primera vez que se levanta (y después de cada `local:reset`). Las
fechas son relativas al día en que se siembran. **Contraseña de todas las cuentas: `admin123`.**

### Profesionales

| Cuenta | Perfil |
|---|---|
| `lucia.fernandez@demo.tranqui` | Psicóloga online y presencial, Mercado Pago y Google Calendar vinculados, suscripción mensual, la agenda más cargada |
| `mateo.suarez@demo.tranqui` | Psicólogo solo online, suscripción anual |
| `martin.rios@demo.tranqui` | Psiquiatra con obra social, recetas y documentos |
| `sofia.medina@demo.tranqui` | Suscripción vencida: al entrar ve la pantalla para reactivar y no aparece en el buscador |
| `tomas.ledesma@demo.tranqui` | Recién registrado: sin verificar y con el perfil incompleto |

### Pacientes

| Cuenta | Para qué sirve |
|---|---|
| `martina.lopez@demo.tranqui` | **Sin turnos: usala para probar una reserva** (la app no deja reservar con un turno activo) |
| `valentina.gomez@demo.tranqui` | Con obra social (OSDE) e historial |
| `nicolas.paz@demo.tranqui` | Con obra social (Swiss Medical), paciente del psiquiatra |
| `juan.martinez@demo.tranqui`, `camila.sosa@demo.tranqui`, `florencia.ruiz@demo.tranqui`, `agustin.vera@demo.tranqui` | Con turnos pasados y futuros |
| `pablo.sindatos@demo.tranqui` | Sin datos personales (flujo de completar perfil) |

### Otras cuentas

| Cuenta | Rol |
|---|---|
| `admin@tranqui.com` | Administrador |
| `profesional.test@tranqui.com`, `medico.verificado@gmail.com`, `paciente.test@tranqui.com`, etc. | Cuentas de prueba de siempre (`DataInitializer`) |

### Qué incluye

- ~45 turnos en las últimas 3 semanas y las próximas 2: atendidos, ausentes, cancelados con y sin
  reembolso, un turno de hoy ya atendido, uno dentro de la próxima hora (con botón de Meet) y uno
  pendiente de pago (se expira a los 5 minutos, como en producción).
- Pagos de esos turnos, certificados pedidos (pendientes y enviados), chats con mensajes leídos y
  sin leer, notificaciones del profesional y tickets de soporte (uno respondido).
- Las "Notas y pendientes" de la agenda no se siembran: se guardan en el navegador, no en la base.

## Problemas frecuentes

- **El navegador no muestra los cambios del frontend:** Vite recarga solo; si no, recargá la página.
  Si cambiaste dependencias (`package.json`), corré `npm run local:reset`.
- **Cambios en el backend:** `npm run local` reconstruye la imagen del backend.
- **Querés empezar de cero:** `npm run local:reset`.
