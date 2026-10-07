# ADR 007: Motor de Recordatorios de Agenda mediante WhatsApp Saliente y Tarea Programada Cron

## Estado
Aceptado

## Contexto
Se requiere implementar un motor de notificaciones salientes automatizadas para enviar recordatorios de turnos por WhatsApp a los pacientes que tienen consultas programadas para el día siguiente. Las consideraciones clave son:
1. **Idempotencia:** Evitar envíos duplicados en caso de reinicios de la aplicación o colisión de tareas.
2. **Aislamiento de Errores:** Si el envío del recordatorio para un turno específico falla (debido a problemas de red, teléfono inválido, etc.), esto no debe impedir ni interrumpir el envío de los recordatorios de los demás turnos.
3. **Validación:** Validar los campos críticos del turno (ej. número de teléfono y URL de Google Meet/telemedicina) antes de realizar el envío.
4. **Independencia en Entornos de Test:** El envío a través del SDK de Twilio debe estar desacoplado para evitar llamadas reales de red y credenciales faltantes durante la ejecución de las pruebas unitarias y de integración.

## Decisiones
1. **Banderas de Control de Envío:**
   Se incorporó el atributo `recordatorioEnviado` (columna `recordatorio_enviado`) a la entidad `Turno.java`. Se inicializa en `false` y se actualiza a `true` únicamente después de que el envío del mensaje de WhatsApp se haya realizado con éxito.

2. **Acceso Seguro a Datos:**
   Se definió la firma `findByEstadoAndFechaAndRecordatorioEnviado` en `TurnoRepository.java` para filtrar las citas candidatas de forma exacta (Estado: `CONFIRMADO`, Fecha: Mañana, Recordatorio Enviado: `false`).

3. **Scheduler de Notificaciones:**
   Se creó `NotificationScheduler.java` con la anotación `@Scheduled(cron = "0 0 20 * * ?", zone = "America/Argentina/Cordoba")` para ejecutar la tarea todos los días a las 20:00:00 hs (Horario de Argentina).
   - Se procesa cada turno de forma individual dentro de un bloque `try-catch`, asegurando que cualquier excepción en `enviarMensajeRecordatorio` quede registrada en los logs sin interrumpir la iteración.
   - Tras el envío exitoso, la propiedad `recordatorioEnviado` se establece en `true` y el turno es guardado.

4. **Integración con Twilio y Soporte Offline:**
   Se diseñó `WhatsAppService.java` encapsulando la inicialización del SDK de Twilio y la llamada a `Message.creator().create()`.
   - Se implementó un método protegido `createTwilioMessage` que valida las credenciales inyectadas. Si se detecta el valor por defecto `ACmockaccount`, el servicio opera en modo Mock/Offline imprimiendo el mensaje en los logs del sistema, lo cual previene llamadas HTTP reales durante las pruebas automatizadas.
   - El formateador de plantillas valida mediante excepciones `IllegalArgumentException` que existan todos los campos obligatorios del turno.

5. **Pruebas Automatizadas:**
   - **Prueba Unitaria (`WhatsAppServiceTest.java`):** Valida la lógica de formato de plantillas con datos completos, verifica que se arrojen excepciones si faltan datos indispensables (paciente, médico, teléfono, URL, etc.), y espía `createTwilioMessage` para comprobar el correcto pasaje de parámetros.
   - **Prueba de Integración (`NotificationSchedulerTest.java`):** Emplea `@SpringBootTest` con H2 en memoria y un mock bean (`@MockBean`) de `WhatsAppService` para simular y verificar la consulta de turnos, confirmación de filtros temporales y de estado, comportamiento del flag de idempotencia y el aislamiento de errores (tolerancia a fallos por turno).

## Consecuencias
* Se garantiza el envío correcto e idempotente de las notificaciones, impidiendo notificaciones duplicadas por reintentos fallidos.
* La cobertura de código para los nuevos componentes de software supera el 90% obligatorio.
* El entorno de integración y desarrollo no requiere credenciales reales de Twilio funcionales para ejecutar la suite de pruebas o levantar la aplicación localmente.
