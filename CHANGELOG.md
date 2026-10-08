# Changelog

Todos los cambios relevantes de Tranqui App. Formato basado en
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versiones
[SemVer](https://semver.org/lang/es/) (ver `docs/flujo/git.md`).

## [Sin publicar]

### CI y deploy

- CI en cada PR a `develop` y `main`: tests del backend, typecheck, tests y build del frontend, y lint
  incremental (solo falla si el PR agrega problemas).
- Deploy automático al crear el tag `vX.Y.Z` en `main`: imágenes en GHCR, el VPS solo las baja,
  verificación del sitio y la API, y vuelta automática a la versión anterior si falla.
- Las variables de producción viven en `/srv/tranqui/.env` del VPS; ningún deploy sube un `.env`.
- Deploy y scripts por clave SSH con verificación del servidor; sin contraseñas en el repo.
  `deploy.js` queda como deploy manual de emergencia.
- Botón "Mantenimiento" en GitHub Actions (el link de administrador no se muestra en los logs:
  `npm run mantenimiento -- acceso`).

### Mail de novedades

- Al apagar el mantenimiento se manda a todos los usuarios un mail con lo que cambió (Broadcasts de
  Resend: no consume el cupo de los mails de la app). Un solo texto dividido en "Para todos",
  "Pacientes" y "Profesionales"; vista previa, prueba solo para administradores e idempotencia.
- Link de baja en cada mail (Ley 25.326) e interruptor "Novedades de Tranqui" en Mi Cuenta y en
  Configuración del profesional. Al eliminar una cuenta se borra su contacto en Resend.
- Política de Privacidad actualizada.

### Modo mantenimiento

- `npm run mantenimiento -- on ["mensaje"] | off | estado`: mientras está activo, el sitio muestra
  solo una página de mantenimiento (503), aunque el backend esté apagado. El estado vive fuera de
  `/app`, así que un deploy no lo cambia.
- Acceso de administrador con un link de un solo uso por mantenimiento, para ver el sitio real
  mientras el resto ve el aviso.

### Entorno local

- `npm run local` levanta la app completa en local (`docker-compose.local.yml`): base propia,
  backend con perfil `local`, frontend con Vite y Mailpit para ver los mails. Antes de cada
  rebuild verifica los puertos y pregunta antes de frenar lo que los ocupe. También
  `local:down`, `local:reset` y `local:logs`.
- Perfil `local` del backend: Mercado Pago, Google Calendar, QBI2 y ARCA apagados o simulados;
  mails por SMTP a Mailpit (en producción se sigue usando Resend).
- Datos de demo realistas (`LocalDemoSeeder`): 5 profesionales de distinto tipo, 8 pacientes,
  ~45 turnos, pagos, chats, documentos, notificaciones y tickets. Solo con el perfil `local`.
- Login con Google oculto en local (`VITE_GOOGLE_LOGIN=false`) y vinculación simulada de Google
  Calendar (solo con el perfil `local`).

### Documentación y flujo de trabajo

- `AGENTS.md` y `CLAUDE.md` con las reglas para agentes de IA: SDD obligatorio antes de tocar
  código, nada de commits directos a `main`/`develop`, no tocar producción sin aprobación, sin
  secretos en el repo, sin emojis en la UI.
- Carpeta `docs/`: flujo de Git (ramas `tipo/area-descripcion`, Conventional Commits en español,
  etiquetas, SemVer), SDD con plantilla de spec, releases, hotfix, sprints, entornos, secretos,
  centro de ayuda y bugs conocidos. Se migró el contenido de `.agent/` (ADRs, etapas, diseño de
  base, especificación del MVP, guías técnicas).
- Plantilla de Pull Request.
- Repositorio: quedan solo `main` y `develop`. Se borraron las ramas viejas ya integradas y la
  rama `whatsapp-bot` (bot de WhatsApp, implementación a futuro).

## [v1.0.0] - 2026-10-07

Primera versión numerada: lo que está en producción al 2026-10-07. Resume el desarrollo de junio a
octubre de 2026.

### Pacientes

- Registro con email y contraseña (verificación por código de 6 dígitos) o con Google;
  recuperación de contraseña.
- Buscador de profesionales con filtros por profesión, especialidad, modalidad y motivo de
  consulta, con próxima disponibilidad.
- Reserva de turnos (particular u obra social, online o presencial) con bloqueo del horario por 5
  minutos mientras se paga con Mercado Pago.
- Mis Turnos: pago pendiente, link de Google Meet, dirección con mapa y cancelación con reembolso
  automático si faltan más de 48 horas.
- Confirmación de asistencia desde el mail de recordatorio, sin iniciar sesión.
- Pedido de documentos (certificados, informes) pagos sin ocupar turno.
- Chat con el profesional, Mi Cuenta (datos, obra social, contraseña, notificaciones, copia de
  datos, eliminación de cuenta) y tickets de soporte.
- Centro de ayuda con 43 preguntas frecuentes por rol y 5 videos explicativos narrados y
  subtitulados (ver `docs/centro-de-ayuda.md`).

### Profesionales

- Registro con matrícula y datos fiscales, plan sugerido según la profesión
  (psicólogos/psiquiatras).
- Perfil público, agenda por modalidad con duración e intervalo configurables, notas y pendientes.
- Honorarios y servicios: consultas, tarifas por obra social, documentos, ajuste de precios.
- Inicio con próximo turno, Meet, ficha del turno, reprogramación y cancelación, documentos
  solicitados.
- Integraciones: Mercado Pago (cobros directos, sin comisión) y Google Calendar (sincronización en
  los dos sentidos).
- Suscripciones mensuales o anuales con débito automático de Mercado Pago, cancelación de la
  renovación con acceso hasta el fin del período y pantalla para reactivar.

### Administración

- Panel de administración: verificación de profesionales, suscripciones (pagos manuales,
  cortesías, cambios de estado) y tickets de soporte.

### Cambios de octubre de 2026 incluidos en esta versión

- Centro de ayuda con videos y guías; base de datos con migraciones Flyway; mejoras en
  suscripciones y deploy (`423ab07`).
- Mercado Pago sin modo sandbox para checkouts y suscripciones en producción (`50b1792`).
- Centro de ayuda reducido a los 5 videos más usados, en formato escritorio (`0d1ded1`).
- nginx sirve los subtítulos como `text/vtt` y responde 404 a videos inexistentes (`7d3c0ff`).
- La foto de perfil se reduce a 400 px JPEG antes de guardarse: la respuesta de `/api/medicos`
  bajó de ~506 KB a ~28 KB y la landing carga en menos de 1 s (`28cb217`). La foto que ya estaba
  guardada en producción se convirtió a mano.
- Encabezado público optimizado en celular y calendario al 100% en escritorio (`7de94ae`).
- `deploy.js` compatible con Windows (`3462fde`).
- Agenda responsive y submenú de Configuración unificado (`dcc658f`).
- Se eliminó la pantalla de acceso restringido al sitio (`e08435f`).
- Arranque del backend sin `LazyInitializationException` en el reconciliador de suscripciones
  (`06642fd`).
- Se eliminó WhatsApp (Twilio) de la app; los pacientes reciben por mail las confirmaciones,
  cancelaciones, reprogramaciones y recordatorios.
