# Etapa 7: Notificaciones de Agenda (WhatsApp Saliente + Tareas Cron)

Esta etapa detalla el motor de notificaciones asíncronas de la aplicación. Se encarga de buscar diariamente los turnos confirmados para el día siguiente y despachar un mensaje automatizado por WhatsApp con los detalles de la consulta y el enlace de telemedicina.

---

## 1. Objetivos de la Etapa
1.  Configurar y activar el soporte de tareas programadas (`@EnableScheduling`) en Spring Boot.
2.  Desarrollar una tarea Cron diaria que se ejecute a una hora fija (ej. 20:00 hs) para buscar los turnos de la jornada siguiente.
3.  Integrar el cliente HTTP para interactuar con la API de WhatsApp Business (Twilio o Meta Cloud API).
4.  Formatear y sanitizar plantillas oficiales de WhatsApp requeridas por Meta.

---

## 2. Definición Técnica y Código de Soporte

### A. Tarea Programada para Recordatorios (`NotificationScheduler.java`)
```java
@Component
public class NotificationScheduler {

    @Autowired
    private TurnoRepository turnoRepository;
    @Autowired
    private WhatsAppService whatsappService;

    // Cron se ejecuta todos los días a las 20:00:00 (Zona Horaria Argentina)
    @Scheduled(cron = "0 0 20 * * ?", zone = "America/Argentina/Cordoba")
    @Transactional(readOnly = true)
    public void enviarRecordatoriosTurnosSiguienteDia() {
        LocalDate mañana = LocalDate.now().plusDays(1);
        List<Turno> turnosMañana = turnoRepository.findByEstadoAndFecha(
                EstadoTurno.CONFIRMADO, mañana
        );

        for (Turno turno : turnosMañana) {
            try {
                whatsappService.enviarMensajeRecordatorio(turno);
            } catch (Exception e) {
                // Capturar excepción para que un fallo en un mensaje no bloquee el resto de los envíos
                log.error("Fallo al enviar recordatorio de WhatsApp para el turno ID: " + turno.getId(), e);
            }
        }
    }
}
```

### B. Servicio de Envío de WhatsApp (`WhatsAppService.java`)
Este código muestra la integración utilizando el SDK de Twilio para despachar mensajes basados en plantillas pre-aprobadas.

```java
@Service
public class WhatsAppService {

    @Value("${twilio.account.sid}")
    private String accountSid;
    @Value("${twilio.auth.token}")
    private String authToken;
    @Value("${twilio.whatsapp.number}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    public void enviarMensajeRecordatorio(Turno turno) {
        String para = "whatsapp:" + turno.getPaciente().getTelefono();
        String de = "whatsapp:" + fromNumber;

        // Construcción del mensaje a partir de la plantilla aprobada por Meta
        String cuerpoMensaje = String.format(
            "Hola %s, recordatorio de tu turno con el Dr. %s mañana %s a las %s hs. Enlace de la videollamada: %s",
            turno.getPaciente().getNombre(),
            turno.getMedico().getNombre(),
            turno.getFecha().toString(),
            turno.getHoraInicio().toString(),
            turno.getTelemedicinaUrl()
        );

        Message.creator(
            new PhoneNumber(para),
            new PhoneNumber(de),
            cuerpoMensaje
        ).create();
    }
}
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba Unitaria de Construcción de Mensaje
Verificar que la plantilla formatea las variables del turno correctamente y arroja una excepción si falta algún dato indispensable (ej. número de teléfono o URL de Google Meet).

### B. Test de Integración con MockRestServiceServer
*   Si se consume la API de Meta directamente mediante un `RestTemplate` o `WebClient`, utilizar `MockRestServiceServer` para simular las respuestas HTTP del servidor de Meta (`200 OK` con un ID de mensaje exitoso o `400 Bad Request` en caso de token expirado).

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Políticas de Plantillas de Meta:** WhatsApp Business no permite enviar texto libre en la primera comunicación de salida iniciada por la empresa. Es obligatorio registrar previamente el "Template" en el portal de Meta y usar las variables exactas (`{{1}}`, `{{2}}`).
*   **Idempotencia del Cron:** Asegúrate de guardar una bandera en la tabla de turnos (`recordatorio_enviado = true`) una vez que el mensaje se envíe correctamente. Esto evita volver a notificar por duplicado si el proceso de backend se reinicia de forma inesperada o choca con otra instancia del servidor en alta disponibilidad.
