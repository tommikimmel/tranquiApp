# ADR 005: Conceptos Adicionales (Recetas y Certificados fuera de Turno)

## Estado
Aceptado

## Contexto
Necesitamos implementar un flujo de trabajo transaccional separado de la reserva de turnos, el cual permita al paciente solicitar y pagar de forma directa documentos clínicos de interés (tales como recetas archivadas, recetas de control o certificados de salud mental). Dicho flujo no debe reservar espacios en la agenda médica de Google Calendar, pero debe cobrar el importe a través de la cuenta de Mercado Pago del psiquiatra correspondiente y alertarle en tiempo real vía WebSockets al acreditarse el pago.

## Decisiones
1. **Entidades de BD y Relaciones**:
   - Se crea la entidad `SolicitudDocumento` con el enum `TipoConcepto` (`RECETA`, `RECETA_CONTROL`, `CERTIFICADO`, etc.).
   - Mantiene campos como `estado` (tipo `EstadoPago`), `emitido` (booleano), `urlDescarga` y `fechaEmision` para registrar el momento en que el médico expide el documento.
   - Las relaciones lazy loading se anotan con `@JsonIgnoreProperties` para prevenir excepciones de serialización de Jackson en los endpoints REST de consulta.

2. **Configuración de WebSocket Broker (STOMP)**:
   - Se implementa `WebSocketConfig` activando `@EnableWebSocketMessageBroker` con el prefijo `/topic` de suscripción.
   - Registra el endpoint `/ws-tranqui` con soporte dual (WebSocket directo para pruebas y SockJS para compatibilidad en producción).

3. **Excepción de Seguridad y Webhook Unificado**:
   - El endpoint `/api/payments/webhook` se actualiza para parsear el JSON de Mercado Pago, extrayendo el campo `external_reference`.
   - Se añade lógica de ruteo: referencias que comiencen con `"doc-"` corresponden a solicitudes de documentos (`doc-{id}`), lo cual delega la acreditación en `PagoWebhookHandler` y actualiza la base de datos sin disparar flujos de Google Calendar.

4. **Alertas en Tiempo Real**:
   - `PagoWebhookHandler` publica en tiempo real notificaciones hacia el canal privado del médico (`/topic/notificaciones/{medicoId}`) al acreditarse un cobro válido.
   - Las fechas en el DTO de notificación (`NotificacionDocDto`) se unifican como cadenas de caracteres para optimizar la compatibilidad de deserialización de Jackson.

5. **Emisión Segura y Aislamiento por Token**:
   - Se exponen endpoints en `ConceptoController` para solicitar documentos (`/api/conceptos/solicitar`), recuperar listados de abonados no emitidos (`/api/conceptos/pendientes`), y emitir el archivo firmado (`/api/conceptos/{id}/emitir`).
   - El endpoint de emisión valida que el correo del médico autenticado (`UserDetails`) coincida estrictamente con el asignado a la solicitud antes de autorizar la emisión, previniendo vulnerabilidades de IDOR.

6. **Estrategia de Testing (Mín. 90% Cobertura)**:
   - `ConceptoServiceTest`: Valida creación de solicitudes, control de excepciones por usuarios no válidos y validaciones estrictas de seguridad al emitir.
   - `ConceptoControllerTest`: Comprueba el funcionamiento de las peticiones REST simulando usuarios autenticados con diferentes roles.
   - `WebSocketNotificationTest`: Prueba de integración extremo a extremo que simula la conexión STOMP, la subscripción de tópicos, el procesamiento asíncrono del webhook de pagos y el envío y recepción del payload correspondiente sobre el broker.

## Consecuencias
- Desacoplamiento del flujo de documentos respecto al calendario médico, eliminando ruidos y colisiones en la agenda de Google.
- Tiempos de respuesta inmediatos gracias al sistema de notificaciones instantáneas STOMP.
- Seguridad en la descarga de archivos mediante enlaces seguros validados.
