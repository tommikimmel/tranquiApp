# Etapa 6: Mensajería por WebSockets (Chat Seguro y Priorización Clínica)

Esta etapa implementa la comunicación interactiva en tiempo real mediante WebSockets STOMP y clasifica dinámicamente la bandeja de entrada del psiquiatra en base a la urgencia de los turnos de los pacientes.

---

## 1. Objetivos de la Etapa
1.  Configurar la comunicación WebSocket con el protocolo STOMP en Spring Boot.
2.  Implementar la mensajería segura punto a punto entre psiquiatras y pacientes.
3.  Desarrollar la lógica de ordenamiento de canales (Prioridad Alta vs Prioridad Baja) en el dashboard del médico.
4.  Garantizar el envío de notificaciones en tiempo real (pop-ups) ante eventos de cobro o nuevos turnos.

---

## 2. Definición Técnica y Código de Soporte

### A. Configuración de WebSockets STOMP (`WebSocketConfig.java`)
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tranqui")
                .setAllowedOriginPatterns("*") // En producción, restringir al dominio de la app
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic"); // Mensajería privada y notificaciones generales
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user"); // Canal privado por sesión
    }
}
```

### B. Controlador de Mensajes (`ChatController.java`)
```java
@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private MensajeService mensajeService;

    @MessageMapping("/chat.enviar")
    public void procesarMensaje(@Payload MensajeDto mensajeDto, Principal principal) {
        // Guardar mensaje en base de datos
        Mensaje mensaje = mensajeService.guardarMensaje(mensajeDto, principal.getName());

        // Enviar al canal del destinatario
        String destinatarioId = mensaje.getDestinatario().getId().toString();
        messagingTemplate.convertAndSendToUser(destinatarioId, "/queue/mensajes", mensajeDto);
    }
}
```

### C. Consulta SQL para la Priorización Clínica (Dashboard del Psiquiatra)
Esta consulta clasifica los canales en tiempo real: los pacientes con turnos en las próximas 72 horas o del día corriente se etiquetan como `ALTA_PRIORIDAD` (🔴), mientras que el resto pasa a `BAJA_PRIORIDAD` (⚪).

```sql
SELECT 
    u.id AS paciente_id,
    u.nombre AS paciente_nombre,
    u.email AS paciente_email,
    -- Prioridad alta si tiene turno confirmado en las próximas 72 hs
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM turno t 
            WHERE t.paciente_id = u.id 
              AND t.medico_id = :medicoId 
              AND t.estado = 'CONFIRMADO'
              AND (t.fecha + t.hora_inicio) >= NOW() 
              AND (t.fecha + t.hora_inicio) <= (NOW() + INTERVAL '72 hours')
        ) THEN 'PRIORIDAD_ALTA'
        ELSE 'PRIORIDAD_BAJA'
    END AS prioridad_clinica
FROM usuario u
INNER JOIN mensaje m ON (m.remitente_id = u.id OR m.destinatario_id = u.id)
WHERE (m.remitente_id = :medicoId OR m.destinatario_id = :medicoId)
  AND u.rol = 'PACIENTE'
GROUP BY u.id, u.nombre, u.email
ORDER BY prioridad_clinica ASC, MAX(m.fecha_envio) DESC;
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba de Lógica de Priorización
*   **Prueba Unitaria de Consulta SQL (`ChatPriorizacionRepositoryTest.java`):**
    Utilizar un repositorio JPA test para persistir tres pacientes:
    1.  Paciente A: Tiene turno confirmado mañana.
    2.  Paciente B: Tiene turno confirmado en 5 días.
    3.  Paciente C: Sin turnos.
    El test debe llamar a la consulta SQL y verificar que en el orden de los canales, el Paciente A aparezca con el tag `PRIORIDAD_ALTA` primero en la lista.

### B. Pruebas de Autorización WebSocket
Verificar que un paciente no pueda escuchar el canal `/topic/notificaciones/{medicoId}` de un psiquiatra. El interceptor de canal (`ChannelInterceptor`) en Spring Security debe interceptar el mensaje de suscripción STOMP y validar el JWT antes de otorgar el acceso.

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Límite de Carga de Historial:** Al abrir el chat, el frontend no debe cargar todo el historial histórico del canal de una sola vez. Implementar paginación en el backend (ej. `Pageable` de Spring con lotes de 20 mensajes).
*   **Reconexión Automática:** El frontend en React debe detectar la caída de la conexión por WebSocket (ej. corte de internet o sleep del dispositivo) y programar reintentos exponenciales amortiguados para reconectarse de manera transparente al usuario.
