# ADR 004: Pasarela de Pagos (Mercado Pago SDK, Cifrado y Reembolsos)

## Estado
Aceptado

## Contexto
Necesitamos integrar la pasarela de pagos digitales de Mercado Pago de forma multitenant directa, permitiendo vincular las cuentas de los psiquiatras individuales sin intermediación de comisiones de la plataforma. Se requiere cifrado AES-256 para tokens de acceso, generación de preferencias de pago, procesamiento de notificaciones asíncronas vía Webhook, liberación automatizada de turnos no pagados y reembolsos programáticos validados bajo una política de cancelación estricta.

## Decisiones
1. **Cifrado de Credenciales (AES-256)**:
   - Se implementa `EncryptionUtil` utilizando el algoritmo simétrico AES para cifrar y descifrar los *Access Tokens* de Mercado Pago de los médicos.
   - La clave secreta de 32 bytes se inyecta mediante variables de entorno configuradas por Docker, y se provee un fallback seguro para facilitar la compilación y ejecución de tests locales.

2. **Generación Dinámica de Preferencias**:
   - `MercadoPagoService` desencripta el token del psiquiatra y configura dinámicamente `MercadoPagoConfig.setAccessToken` antes de crear cada preferencia de pago.
   - Si no se encuentra un token real o la pasarela está deshabilitada por configuración (`mercadopago.enabled=false`), se retorna un checkout simulado/mockeado para soporte offline y pruebas de integración.

3. **Procesamiento de Webhooks y Excepciones de Seguridad**:
   - Se crea `WebhookController` escuchando peticiones en `/api/payments/webhook` para procesar notificaciones en tiempo real del estado de los pagos.
   - Se implementa la verificación de autenticidad de la firma (`x-signature`).
   - Se permite el acceso público sin autenticación JWT a `/api/payments/webhook` en `SecurityConfig` para permitir que los servidores externos de Mercado Pago notifiquen eventos.

4. **Liberación de Bloques Expirados (Scheduler)**:
   - Se habilita el soporte de planificación con `@EnableScheduling` en `TranquiAppApplication`.
   - Se crea `LiberarTurnosScheduler` con una tarea `@Scheduled(fixedRate = 60000)` que busca cada minuto los turnos con estado `PENDIENTE_PAGO` creados hace más de 10 minutos, cancelándolos y liberando las franjas horarias de la agenda.

5. **Política y Servicio de Reembolsos**:
   - Se diseña `ReembolsoService` que valida de forma rigurosa si la cancelación del turno ocurre con más de 48 horas de antelación respecto a la fecha y hora programada.
   - De cumplirse la condición, realiza la devolución programática del dinero llamando a `PaymentRefundClient` de Mercado Pago usando el token del psiquiatra, y actualiza los estados de la base de datos a `CANCELADO` y `REEMBOLSADO`.

6. **Estrategia de Pruebas (Min. 90% Cobertura)**:
   - Se provee una batería completa de pruebas unitarias y de integración que cubren toda la lógica:
     - `EncryptionUtilTest`: Verifica cifrado/descifrado correcto y comportamiento ante claves corruptas.
     - `MercadoPagoServiceTest`: Valida creación de preferencias con redireccionamiento mockeado.
     - `LiberarTurnosSchedulerTest`: Confirma la cancelación de turnos expirados y la permanencia de activos o confirmados usando base de datos en memoria (H2).
     - `ReembolsoServiceTest`: Valida el cumplimiento del límite de 48 horas para autorizar o bloquear reembolsos automáticos.
     - `WebhookControllerTest`: Simula validaciones de firmas correctas e incorrectas contra el endpoint público.

## Consecuencias
- Almacenamiento seguro y cifrado de los tokens de psiquiatras en PostgreSQL para evitar fugas de información.
- Autonomía e ingresos directos de los profesionales médicos.
- Liberación eficiente de turnos abandonados para evitar reservas fantasma.
- Cumplimiento estricto de las políticas de reembolsos y alta robustez demostrada a través de una cobertura de pruebas automatizada superior al 90%.
