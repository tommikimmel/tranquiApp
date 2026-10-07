# Etapa 4: Pasarela de Pagos (Mercado Pago SDK, Cifrado y Reembolsos)

Esta etapa implementa la pasarela de pagos digitales directa, vinculando la cuenta del psiquiatra para cobrar al 0% de comisión de la plataforma. Cubre el cifrado AES-256 de credenciales, generación de preferencias, webhooks y la política de reembolso programático antes de 48 hs.

---

## 1. Objetivos de la Etapa
1.  Implementar el cifrado AES-256 para el almacenamiento seguro de los *Access Tokens* de los psiquiatras en PostgreSQL.
2.  Generar una preferencia de pago en Mercado Pago utilizando la credencial específica del médico asociado al turno.
3.  Desarrollar un Webhook para recibir notificaciones en tiempo real desde Mercado Pago (`IPN` / `Webhook`).
4.  Crear una tarea programada (`@Scheduled`) para liberar turnos no abonados tras 10 minutos de bloqueo.
5.  Automatizar los reembolsos programáticos en cancelaciones con más de 48 horas de anticipación.

---

## 2. Definición Técnica y Código de Soporte

### A. Cifrado AES-256 de Access Tokens (`EncryptionUtil.java`)
```java
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    
    @Value("${security.encryption.key}") // Clave de 32 bytes inyectada
    private String secretKey;

    public String encrypt(String value) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public String decrypt(String encryptedValue) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
```

### B. Generación de Preferencias con el Access Token del Psiquiatra
```java
@Service
public class MercadoPagoService {

    @Autowired
    private EncryptionUtil encryptionUtil;

    public String crearPreferenciaPago(Turno turno, Usuario medico) throws Exception {
        // Desencriptar token de Mercado Pago del médico
        String rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());

        // Inicializar SDK de MP con credenciales de ese médico específico (SaaS Multitenant Directo)
        MercadoPagoConfig.setAccessToken(rawToken);

        PreferenceClient client = new PreferenceClient();

        PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                .title("Consulta Psiquiátrica - " + medico.getNombre())
                .quantity(1)
                .unitPrice(turno.getPrecio())
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(itemRequest))
                .externalReference(turno.getId().toString())
                .notificationUrl("https://tranquiapp.com/api/payments/webhook")
                .build();

        Preference preference = client.create(request);
        return preference.getInitPoint(); // URL de Checkout
    }
}
```

### C. Tarea Programada para Liberar Turnos (`SchedulerConfig.java`)
Ejecutado cada 1 minuto para buscar turnos en `PENDIENTE_PAGO` creados hace más de 10 minutos y cambiar su estado a `CANCELADO` (liberando el bloque horario).

```java
@Component
public class LiberarTurnosScheduler {

    @Autowired
    private TurnoRepository turnoRepository;

    @Scheduled(fixedRate = 60000) // Cada minuto
    @Transactional
    public void liberarTurnosExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(10);
        List<Turno> turnosExpirados = turnoRepository.findByEstadoAndFechaCreacionBefore(
                EstadoTurno.PENDIENTE_PAGO, limite
        );

        for (Turno turno : turnosExpirados) {
            turno.setEstado(EstadoTurno.CANCELADO);
            turnoRepository.save(turno);
        }
    }
}
```

### D. Política de Cancelación y Reembolso
```java
@Service
public class ReembolsoService {

    @Autowired
    private EncryptionUtil encryptionUtil;

    public boolean procesarReembolso(Turno turno, Usuario medico) throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaTurno = LocalDateTime.of(turno.getFecha(), turno.getHoraInicio());

        // Validar política de 48 horas
        if (ahora.plusHours(48).isAfter(fechaTurno)) {
            return false; // Bloquear reembolso automático
        }

        String rawToken = encryptionUtil.decrypt(medico.getMpAccessTokenEncrypted());
        MercadoPagoConfig.setAccessToken(rawToken);

        RefundClient refundClient = new RefundClient();
        refundClient.create(Long.parseLong(turno.getPago().getTransactionId()));

        turno.setEstado(EstadoTurno.CANCELADO);
        turno.getPago().setEstado(EstadoPago.REEMBOLSADO);
        return true;
    }
}
```

---

## 3. Estrategia de Testing y Verificación

### A. Simulación de Webhooks (Integración)
*   **Test de Integración (`WebhookControllerTest.java`):**
    Simular una petición POST de Mercado Pago en `/api/payments/webhook` con una firma falsa. El controlador de webhooks debe rechazar la llamada si la firma no pasa la validación de autenticidad provista por el SDK de Mercado Pago.

### B. Pruebas Unitarias de Cifrado
*   Verificar que cifrar un texto y volver a descifrarlo retorne el valor original.
*   Asegurar que si se intenta descifrar usando una llave corrupta o alterada, arroje una excepción de criptografía (`BadPaddingException`).

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Firma del Webhook:** Mercado Pago envía un header `x-signature` o un token de verificación. Es crítico implementar la validación de firma para evitar que atacantes externos simulen pagos de turnos enviando payloads JSON falsos a la API.
*   **Límites de Reembolsos:** El psiquiatra debe tener fondos suficientes en su saldo de Mercado Pago para poder efectuar un reembolso. Si el reembolso programático falla por falta de saldo, se debe notificar vía email al psiquiatra para resolver la situación de manera manual.
