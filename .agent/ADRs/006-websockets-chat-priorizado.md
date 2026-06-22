# ADR 006: Mensajería por WebSockets (Chat Seguro y Priorización Clínica)

## Estado
Aceptado

## Contexto
Necesitamos implementar un canal de comunicación seguro y en tiempo real entre el psiquiatra y el paciente. Además de la mensajería instantánea, la bandeja de entrada del psiquiatra debe estar clasificada clínicamente de forma dinámica: los pacientes que cuenten con una cita confirmada en las próximas 72 horas deben ser resaltados con alta prioridad (`PRIORIDAD_ALTA`) y aparecer al principio de la lista de chats. Finalmente, se requiere asegurar los canales de WebSockets para impedir que usuarios maliciosos escuchen notificaciones o chats ajenos.

## Decisiones
1. **Entidades de BD y Paginación**:
   - Se crea la entidad `Mensaje` con referencias lazy hacia el remitente y destinatario (anotadas con `@JsonIgnoreProperties`), contenido y fecha de envío.
   - El historial de chat se recupera a través del método `findChatHistory` en `MensajeRepository` utilizando `Pageable` de Spring para retornar los mensajes en lotes paginados (evitando sobrecargas de memoria).

2. **Bandeja de Entrada Priorizada por SQL**:
   - Se añade la consulta nativa `findPrioritizedChannels` en `MensajeRepository` mapeada al DTO de proyección `CanalPrioritarioDto`.
   - La consulta evalúa de manera dinámica si el paciente posee algún turno en estado `CONFIRMADO` dentro de una franja de 72 horas a partir de la fecha y hora actual (parámetros provistos desde la capa de servicio).
   - Para garantizar la compatibilidad entre H2 (entorno de pruebas) y PostgreSQL (producción), la lógica se escribe en SQL ANSI estándar cruzando operadores lógicos de comparación de fecha y hora, omitiendo sintaxis de intervalos propietarias de un solo motor.
   - Los resultados se ordenan colocando `PRIORIDAD_ALTA` primero, y ordenando de forma secundaria por el momento de envío del último mensaje.

3. **Arquitectura STOMP Punto a Punto**:
   - Se actualiza `WebSocketConfig` activando el broker para `/queue` y el prefijo de usuario `/user`.
   - En `ChatController`, el método `@MessageMapping("/chat.enviar")` procesa el mensaje enviado desde el cliente, lo persiste, y lo rutea de forma segura a la cola privada del destinatario (`/user/{destinatarioId}/queue/mensajes`) y del remitente (para confirmación de entrega).

4. **Interceptor de Autorización en WebSockets**:
   - Se diseña `WebSocketChannelInterceptor` implementando `ChannelInterceptor` de Spring Messaging.
   - **Durante CONNECT**: Extrae el token JWT del encabezado `Authorization` y realiza la autenticación contra `JwtService`, inyectando el principal en la sesión STOMP.
   - **Durante SUBSCRIBE**: Intercepta subscripciones al canal privado `/topic/notificaciones/{medicoId}`. Verifica que el principal autenticado corresponda estrictamente con el correo del médico asignado a ese canal. Si no coincide o la sesión no está autenticada, arroja una excepción `MessageDeliveryException`, bloqueando la subscripción de forma inmediata.

5. **Estrategia de Testing (Mín. 90% Cobertura)**:
   - `ChatPriorizacionRepositoryTest`: Inicializa y persiste escenarios clínicos cruzando citas lejanas, cercanas y sin citas, asegurando que la consulta ordene de forma correcta los canales según la prioridad.
   - `ChatControllerTest`: Valida el retorno paginado de historiales de chat y el formato de salida JSON de los canales priorizados.
   - `WebSocketAuthorizationTest`: Simula un cliente STOMP que intenta subscribirse a su propio canal de notificaciones (exitoso) y a otro canal ajeno (rechazado por el servidor, disparando el cierre seguro de la conexión por error de transporte).
   - `WebSocketNotificationTest`: Actualizado para conectarse autenticando la sesión STOMP con el token del doctor titular, lo cual valida de manera integral el flujo del webhook bajo el nuevo esquema de seguridad.

## Consecuencias
- Interfaz del médico limpia y priorizada, permitiendo dar atención inmediata a pacientes con citas inminentes.
- Canal de WebSocket blindado frente a intrusiones de suscripción cruzada.
- Historiales de chat eficientes gracias al soporte de paginación a nivel de base de datos.
