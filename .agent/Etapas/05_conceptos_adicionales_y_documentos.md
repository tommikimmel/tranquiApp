# Etapa 5: Conceptos Adicionales (Recetas y Certificados fuera de Turno)

Esta etapa abarca el flujo transaccional para la solicitud y cobro digital directo de documentos clínicos especiales (Certificados de salud mental, recetas archivadas, etc.) que no requieren agendar un bloque horario de consulta.

---

## 1. Objetivos de la Etapa
1.  Permitir que el paciente solicite un "Concepto de Pago" (ej. Receta, Certificado).
2.  Procesar el cobro a través de Mercado Pago (sin registrar bloque horario en Google Calendar).
3.  Emitir una alerta de WebSocket interna al psiquiatra cuando el cobro sea acreditado.
4.  Mostrar la solicitud en la sección *"Documentos pendientes de emisión (Abonados)"* en el dashboard del médico.
5.  Habilitar el envío seguro del documento firmado a través del chat integrado de la aplicación.

---

## 2. Definición Técnica y Código de Soporte

### A. Endpoint para Crear Solicitudes de Documentos (`ConceptoController.java`)
```java
@RestController
@RequestMapping("/api/conceptos")
public class ConceptoController {

    @Autowired
    private ConceptoService conceptoService;

    @PostMapping("/solicitar")
    public ResponseEntity<?> solicitarDocumento(@RequestBody SolicitarConceptoDto dto, @AuthenticationPrincipal UserDetails userDetails) {
        // Genera la solicitud bajo estado PENDIENTE_PAGO
        SolicitudDocumento solicitud = conceptoService.crearSolicitud(dto, userDetails.getUsername());
        
        // Retorna la URL de checkout de Mercado Pago
        String checkoutUrl = conceptoService.generarCheckoutUrl(solicitud);
        return ResponseEntity.ok(new PagoResponseDto(solicitud.getId(), checkoutUrl));
    }
}
```

### B. Notificación de Webhook para Solicitudes sin Agenda
Al recibir la aprobación del pago, el backend comprueba que se trata de un concepto de tipo `RECETA` o `CERTIFICADO`. En lugar de agendar en Google Calendar, publica una notificación por WebSocket al canal privado del médico.

```java
@Service
public class PagoWebhookHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    @Transactional
    public void procesarAprobacionConcepto(Long solicitudId, String transactionId) {
        SolicitudDocumento solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));

        solicitud.setEstado(EstadoPago.APROBADO);
        solicitudRepository.save(solicitud);

        // Payload JSON para enviar vía WebSocket
        NotificacionDocDto payload = new NotificacionDocDto(
                solicitud.getId(),
                solicitud.getPaciente().getNombre(),
                solicitud.getTipoConcepto().toString(),
                LocalDateTime.now()
        );

        // Publicar al canal WebSocket del médico asignado
        String destino = "/topic/notificaciones/" + solicitud.getMedico().getId();
        messagingTemplate.convertAndSend(destino, payload);
    }
}
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba de Integración WebSocket (Mocking de STOMP Session)
Verificar que la notificación viaja y es interceptada correctamente por un cliente STOMP emulado.
*   **Prueba de Integración (`WebSocketNotificationTest.java`):**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketNotificationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setup() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void shouldReceiveDocumentNotificationOnPaidWebhook() throws Exception {
        BlockingQueue<NotificacionDocDto> blockingQueue = new ArrayBlockingQueue<>(1);
        
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-tranqui", 
                new StompSessionHandlerAdapter() {}
        ).get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/notificaciones/1", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificacionDocDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.add((NotificacionDocDto) payload);
            }
        });

        // Simular webhook de aprobación
        // ... llamada de simulación ...
        
        NotificacionDocDto received = blockingQueue.poll(3, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("RECETA_CONTROL", received.getTipo());
    }
}
```

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Expiración de Solicitudes:** A diferencia de los turnos (que bloquean la agenda), las solicitudes de recetas pendientes de pago no tienen un límite estricto de 10 minutos de expiración, pero se recomienda auto-cancelarlas pasadas las 48 horas si el paciente abandona la pasarela de pagos.
*   **Integridad de Archivos:** Las recetas y certificados digitales emitidos por el psiquiatra deben firmarse y cargarse en un sistema de almacenamiento seguro (ej. volumen Docker montado o AWS S3 con URLs firmadas con vencimiento) para garantizar la confidencialidad de la información clínica del paciente.
