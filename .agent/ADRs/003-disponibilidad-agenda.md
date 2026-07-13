# ADR 003: Disponibilidad Horaria, Agenda y Generación de Turnos

## Estado
Aceptado

## Contexto
Necesitamos implementar el algoritmo para calcular los bloques horarios de atención de un médico (de 45 minutos de duración) basándonos en su disponibilidad horaria semanal y cruzando los datos con los turnos agendados en una fecha dada. Además, los turnos exitosos deben sincronizarse con Google Calendar (generando un enlace de Google Meet), y cualquier fallo en esta sincronización externa debe disparar un rollback de la base de datos local para mantener la consistencia.

## Decisiones
1. **Entidades de BD**:
   - `Disponibilidad`: Define las horas de inicio/fin y el día de la semana (`1` Lunes a `7` Domingo) configurado por el psiquiatra.
   - `Turno`: Almacena el estado de la reserva (`PENDIENTE_PAGO`, `CONFIRMADO`, etc.), fecha, franja horaria, tipo (`PARTICULAR`, `OSDE`), metadata del afiliado y la URL de Google Meet.

2. **Algoritmo de Bloques (45 Minutos)**:
   - Implementado en `AgendaService.calcularBloquesDisponibles`. Toma los rangos semanales del médico correspondientes al día de la fecha de reserva y los divide en iteraciones fijas de 45 minutos.
   - Filtra y omite cualquier franja horaria que colisione con el intervalo de algún turno ya agendado (`comprobarChoqueTurno`).

3. **Zona Horaria Unificada**:
   - Para evitar desfases horarios en las citas clínicas, se unifica el tratamiento temporal a la zona horaria de Argentina (`America/Argentina/Cordoba`, UTC-3) de acuerdo a las directrices de la habilidad `appointment-scheduler`.

4. **Sincronización Transaccional con Google Calendar**:
   - `GoogleCalendarService` simula la creación del evento en Google Calendar y la obtención del enlace de Meet. Para fines de testing y robustez de interrupciones, si se proporciona la clave `"FAIL_CALENDAR"`, el servicio arroja un error controlado.
   - `TurnoService` confirma los turnos marcados con cobertura OSDE e inyecta la URL de Meet. Al estar marcado como `@Transactional`, cualquier error de sincronización de la API de Google revierte la confirmación en la base de datos local.

5. **Pruebas de Calidad (Min. 90% Cobertura)**:
   - `AgendaServiceTest`: Comprueba el correcto fraccionamiento en bloques de 45 minutos y el filtrado por colisiones de turnos existentes.
   - `TurnoServiceTest`: Valida la confirmación exitosa con inyección de Meet, y verifica el **rollback transaccional completo** (manteniendo el turno en estado `PENDIENTE_PAGO` sin metadata) si falla el servicio del calendario.

## Consecuencias
- El sistema particiona las agendas médicas correctamente y previene las colisiones horarias.
- El flujo transaccional garantiza la consistencia de datos ante interrupciones de servicios externos (Google Calendar).
- Toda la lógica del incremento cuenta con cobertura automatizada por encima de la regla obligatoria del 90%.
