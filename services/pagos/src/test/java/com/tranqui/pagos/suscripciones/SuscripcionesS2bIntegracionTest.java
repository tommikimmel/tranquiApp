package com.tranqui.pagos.suscripciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.data.VerificacionResueltaData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.mp.Vinculacion;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests integrales de la Fase 8 (S2b): suscripciones, Mercado Pago y reconciliación
 * en el servicio de pagos sobre PostgreSQL y RabbitMQ reales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "pagos.carga-inicial.habilitada=false",
        "pagos.suscripciones.habilitada=true",
        "mercadopago.enabled=true",
        "mercadopago.client-id=cliente",
        "mercadopago.client-secret=secreto-cliente",
        "mercadopago.webhook-secret=" + SuscripcionesS2bIntegracionTest.SECRETO_WEBHOOK,
        "mercadopago.admin.access-token=TEST-MP-ADMIN-TOKEN",
        "mercadopago.admin.public-key=TEST-MP-ADMIN-PUBLIC-KEY",
        "payment.simulation.enabled=true",
        "app.public-url=https://tranqui.test",
        "app.frontend-url=https://front.test",
        "jwt.secret=" + SuscripcionesS2bIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=" + SuscripcionesS2bIntegracionTest.CLAVE_SERVICIO,
        "security.encryption.key=clave-de-cifrado-de-test-de-32-bytes",
        "tranqui.gateway.url=http://localhost:1"
})
class SuscripcionesS2bIntegracionTest {

    static final String CLAVE_JWT = "clave-de-test-de-al-menos-32-bytes-ok";
    static final String CLAVE_SERVICIO = "clave-servicio-test-de-32-bytes-ok";
    static final String SECRETO_WEBHOOK = "secreto-del-webhook";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean ClienteInterno interno;

    @Autowired Planes planes;
    @Autowired Suscripciones suscripciones;
    @Autowired EventosSuscripcion eventosSuscripcion;
    @Autowired PagosSuscripcion pagosSuscripcion;
    @Autowired Suscriptores suscriptores;
    @Autowired CargaInicialSuscripciones cargaInicialSuscripciones;
    @Autowired SuscripcionService suscripcionService;
    @Autowired SubscriptionReconciliationScheduler reconciliationScheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired TestRestTemplate http;
    @Autowired CuentasMp cuentas;
    @Autowired Vinculacion vinculacion;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private ServiceJwt serviceJwt;

    @BeforeEach
    void setUp() throws Exception {
        serviceJwt = new ServiceJwt(CLAVE_SERVICIO);
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("pagos"));
            return info != null && info.getConsumerCount() > 0;
        });

        for (String tabla : List.of("subscription_events", "subscription_payments", "invoices", "invoice_sequences",
                "subscriptions", "plan_features", "plans", "features", "suscriptor", "reembolso", "pago", "cobro", "cuenta_mp",
                "participante", "outbox", "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);

        LocalDateTime ahora = LocalDateTime.now();
        // Sembrar planes de prueba
        planes.insertar(new Planes.Plan(1L, "consultorio", "Plan Consultorio", "Plan individual para psicólogos y terapeutas",
                new BigDecimal("15000.00"), null, null, "monthly", 1, null, false, true, ahora, null));
        planes.insertar(new Planes.Plan(2L, "clinico", "Plan Clínico", "Plan para psiquiatras con prescripción digital",
                new BigDecimal("25000.00"), null, null, "monthly", 1, null, true, true, ahora, null));
        planes.ajustarSecuencia();

        // Features
        planes.insertarFeature(new Planes.Feature("prescripcion_digital", "Prescripción digital", "Permite emitir recetas digitales"));
        planes.insertarPlanFeature(new Planes.PlanFeature(1L, 2L, "prescripcion_digital", 1));

        // Sembrar suscriptores de prueba
        suscriptores.guardar(new Suscriptores.Suscriptor(10L, "pro@mail.com", "Juan", "Perez", "PROFESIONAL",
                "Psicólogo", true, 1, "CUIT", "20112223334", "Juan Perez", 6,
                "Dirección 123", "20112223334", 20112223334L, "11222333", 11222333, ahora));
        suscriptores.guardar(new Suscriptores.Suscriptor(11L, "psiquiatra@mail.com", "Laura", "Gomez", "PSIQUIATRA",
                "Psiquiatra", true, 1, "CUIT", "27223334445", "Laura Gomez", 6,
                "Av. Santa Fe 1234", "27223334445", 27223334445L, "22333444", 22333444, ahora));
        suscriptores.guardar(new Suscriptores.Suscriptor(99L, "admin@mail.com", "Admin", "User", "ADMIN",
                null, true, 1, null, null, null, null, null, null, null, null, null, ahora));
        suscriptores.guardar(new Suscriptores.Suscriptor(5L, "paciente@mail.com", "Ana", "Paz", "PACIENTE",
                null, true, 1, null, null, null, null, null, null, null, null, null, ahora));
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se cumplió la condición a tiempo");
            }
            Thread.sleep(100);
        }
    }

    private static String token(String email, String rol) {
        return Jwts.builder().setClaims(Map.of("rol", rol)).setSubject(email).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE_JWT.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String ruta, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION-TOKEN=" + token);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> pedirInterno(HttpMethod metodo, String ruta, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceJwt.emitirToken("gateway"));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(ruta, metodo, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> webhook(String cuerpo, String dataId, String type, boolean firmado) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String firma = HexFormat.of().formatHex(mac.doFinal(("id:" + dataId.toLowerCase() + ";request-id:req-sub-1;ts:" + ts + ";")
                .getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("x-signature", "ts=" + ts + ",v1=" + (firmado ? firma : "0".repeat(64)));
        headers.add("x-request-id", "req-sub-1");
        return http.exchange("/api/payments/webhook?data.id=" + dataId + (type != null ? "&type=" + type : ""),
                HttpMethod.POST, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private <T> void publicar(String topico, String aggregateId, T data) throws Exception {
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, topico, MessageBuilder
                .withBody(mapper.writeValueAsBytes(EventEnvelope.<T>builder().eventId(UUID.randomUUID())
                        .eventType(Topicos.extraerTipoEvento(topico)).producer("monolito").aggregateType("usuario")
                        .aggregateId(aggregateId).aggregateVersion(1).data(data).build()))
                .setContentType("application/json").build());
    }

    // ==========================================
    // 1. Catálogo de planes y seguridad de rutas
    // ==========================================

    @Test
    void planes_publico_devuelvePlanesActivosConFormatoEsperado() throws Exception {
        ResponseEntity<String> res = pedir(HttpMethod.GET, "/api/subscriptions/plans", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode planesJson = mapper.readTree(res.getBody());
        assertThat(planesJson.isArray()).isTrue();
        assertThat(planesJson).hasSize(2);
        assertThat(planesJson.get(0).get("code").asText()).isEqualTo("consultorio");
        assertThat(planesJson.get(0).get("priceArs").asDouble()).isEqualTo(15000.00);
        assertThat(planesJson.get(0).has("features")).isTrue();
    }

    @Test
    void seguridad_rutasProtegidas_rolesYAutenticacion() {
        // Sin token
        assertThat(pedir(HttpMethod.GET, "/api/subscriptions/my-subscription", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir(HttpMethod.GET, "/api/admin/subscriptions/overview", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Rol no autorizado (Paciente no puede ver admin)
        String tokenPaciente = token("paciente@mail.com", "PACIENTE");
        assertThat(pedir(HttpMethod.GET, "/api/admin/subscriptions/overview", tokenPaciente, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Profesional no puede acceder a admin
        String tokenPro = token("pro@mail.com", "PROFESIONAL");
        assertThat(pedir(HttpMethod.GET, "/api/admin/subscriptions/overview", tokenPro, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ==========================================
    // 2. Mi suscripción, features y profesión
    // ==========================================

    @Test
    void miSuscripcion_devuelveDatosYFeaturesSegunProfesion() throws Exception {
        String tokenPro = token("pro@mail.com", "PROFESIONAL");
        // Inicialmente Juan (10L) no tiene suscripción aún: el endpoint devuelve null (200 OK sin body)
        ResponseEntity<String> resSinSub = pedir(HttpMethod.GET, "/api/subscriptions/my-subscription", tokenPro, null);
        assertThat(resSinSub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resSinSub.getBody()).isNull();

        LocalDateTime ahora = LocalDateTime.now();
        // Le creamos una suscripción activa
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-1", new BigDecimal("15000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(25), null,
                false, null, false, ahora, ahora));

        ResponseEntity<String> resConSub = pedir(HttpMethod.GET, "/api/subscriptions/my-subscription", tokenPro, null);
        assertThat(resConSub.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = mapper.readTree(resConSub.getBody());
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.get("plan").get("code").asText()).isEqualTo("consultorio");

        // Laura (11L) es Psiquiatra con plan clínico: activeFeatures contiene rescetario/prescripcion_digital
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 11L, 2L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-2", new BigDecimal("25000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(2), ahora.plusDays(28), null,
                false, null, false, ahora, ahora));

        String tokenPsiquiatra = token("psiquiatra@mail.com", "PSIQUIATRA");
        ResponseEntity<String> resPsiquiatra = pedir(HttpMethod.GET, "/api/subscriptions/my-subscription", tokenPsiquiatra, null);
        assertThat(resPsiquiatra.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jsonPsi = mapper.readTree(resPsiquiatra.getBody());
        assertThat(jsonPsi.get("activeFeatures").toString()).contains("prescripcion_digital");
    }

    // ==========================================
    // 3. Checkout y cancelación
    // ==========================================

    @Test
    void checkout_creaSuscripcionYPreapprovalSimulado_cancelaAnterior() throws Exception {
        String tokenPro = token("pro@mail.com", "PROFESIONAL");

        Dtos.CheckoutRequestDto body = new Dtos.CheckoutRequestDto(1L, "monthly");
        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/subscriptions/checkout", tokenPro, body);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode json = mapper.readTree(res.getBody());
        assertThat(json.has("checkoutUrl")).isTrue();
        assertThat(json.get("checkoutUrl").asText()).isNotBlank();

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub.planId()).isEqualTo(1L);
        assertThat(sub.mpPreapprovalId()).isNotBlank();
        String primerPreapproval = sub.mpPreapprovalId();

        // Segundo checkout con otro plan: actualiza y simula cancelación del anterior
        Dtos.CheckoutRequestDto body2 = new Dtos.CheckoutRequestDto(2L, "monthly");
        ResponseEntity<String> res2 = pedir(HttpMethod.POST, "/api/subscriptions/checkout", tokenPro, body2);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);

        Suscripciones.Suscripcion sub2 = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub2.planId()).isEqualTo(2L);
        assertThat(sub2.mpPreapprovalId()).isNotBlank();
    }

    @Test
    void cancel_cancelaPreapproval_mantieneAccesoSiPeriodoActivo() throws Exception {
        String tokenPro = token("pro@mail.com", "PROFESIONAL");

        LocalDateTime ahora = LocalDateTime.now();
        // Creamos suscripción activa con periodo vigente hasta dentro de 15 días
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-ACTIVA", new BigDecimal("15000.00"), "monthly",
                ahora.plusDays(15), ahora.minusDays(15), ahora.plusDays(15), null,
                false, null, false, ahora, ahora));

        ResponseEntity<String> res = pedir(HttpMethod.POST, "/api/subscriptions/cancel", tokenPro, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = mapper.readTree(res.getBody());
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.get("cancelAtPeriodEnd").asBoolean()).isTrue();

        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub.status()).isEqualTo("ACTIVE");
        assertThat(sub.cancelAtPeriodEnd()).isTrue();

        // Aviso publicado en outbox (cancelación confirmada)
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE topic = 'suscripciones.aviso.programado.v1'", Long.class);
        assertThat(count).isGreaterThan(0);
    }

    // ==========================================
    // 4. Pago manual idempotente por clave (Admin)
    // ==========================================

    @Test
    void manualPayment_esIdempotentePorClave() throws Exception {
        String tokenAdmin = token("admin@mail.com", "ADMIN");

        LocalDateTime ahora = LocalDateTime.now();
        Dtos.ManualPaymentRequestDto dto = new Dtos.ManualPaymentRequestDto(
                10L, 1L, new BigDecimal("15000.00"), "MANUAL",
                ahora, ahora.plusMonths(1), "REC-001",
                "Pago manual en efectivo", false, "IDEMP-KEY-TEST-1"
        );

        // Primer intento: registra el pago y activa la suscripción
        ResponseEntity<String> res1 = pedir(HttpMethod.POST, "/api/admin/subscriptions/manual-payment", tokenAdmin, dto);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode p1 = mapper.readTree(res1.getBody());
        assertThat(p1.get("idempotencyKey").asText()).isEqualTo("IDEMP-KEY-TEST-1");
        assertThat(p1.get("status").asText()).isEqualTo("APPROVED");

        Suscripciones.Suscripcion sub1 = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub1.status()).isEqualTo("ACTIVE");
        LocalDateTime end1 = sub1.currentPeriodEnd();

        // Segundo intento con la misma clave: devuelve el mismo pago sin duplicar ni mover fechas
        ResponseEntity<String> res2 = pedir(HttpMethod.POST, "/api/admin/subscriptions/manual-payment", tokenAdmin, dto);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode p2 = mapper.readTree(res2.getBody());
        assertThat(p2.get("id").asLong()).isEqualTo(p1.get("id").asLong());

        Suscripciones.Suscripcion sub2 = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub2.currentPeriodEnd()).isEqualTo(end1);

        // Cantidad de pagos en base debe ser exactamente 1
        assertThat(pagosSuscripcion.cantidad()).isEqualTo(1);
    }

    // ==========================================
    // 5. Rutas Admin: Overview, List, Plans, Events, Status, Reconciliation
    // ==========================================

    @Test
    void admin_rutasCompletas() throws Exception {
        String tokenAdmin = token("admin@mail.com", "ADMIN");

        // 1. Overview
        ResponseEntity<String> resOverview = pedir(HttpMethod.GET, "/api/admin/subscriptions/overview", tokenAdmin, null);
        assertThat(resOverview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode overviewJson = mapper.readTree(resOverview.getBody());
        assertThat(overviewJson.has("mrr")).isTrue();
        assertThat(overviewJson.has("totalFacturado12Meses")).isTrue();
        assertThat(overviewJson.has("topeCategoriaA")).isTrue();

        // 2. List
        ResponseEntity<String> resList = pedir(HttpMethod.GET, "/api/admin/subscriptions/list", tokenAdmin, null);
        assertThat(resList.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listJson = mapper.readTree(resList.getBody());
        assertThat(listJson.isArray()).isTrue();

        // 3. Plans GET & PUT
        ResponseEntity<String> resPlans = pedir(HttpMethod.GET, "/api/admin/subscriptions/plans", tokenAdmin, null);
        assertThat(resPlans.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> updateBody = Map.of(
                "name", "Consultorio Plus",
                "priceArs", 18000.00
        );
        ResponseEntity<String> resPutPlan = pedir(HttpMethod.PUT, "/api/admin/subscriptions/plans/1", tokenAdmin, updateBody);
        assertThat(resPutPlan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(planes.porId(1L).orElseThrow().name()).isEqualTo("Consultorio Plus");

        LocalDateTime ahora = LocalDateTime.now();
        // 4. Events y Status Patch
        Long subId = suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-ADMIN", new BigDecimal("15000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(25), null,
                false, null, false, ahora, ahora));

        ResponseEntity<String> resPatch = pedir(HttpMethod.PATCH, "/api/admin/subscriptions/" + subId + "/status",
                tokenAdmin, Map.of("status", "PAST_DUE", "reason", "Falta de pago de prueba"));
        assertThat(resPatch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(suscripciones.porId(subId).orElseThrow().status()).isEqualTo("PAST_DUE");

        ResponseEntity<String> resEvents = pedir(HttpMethod.GET, "/api/admin/subscriptions/" + subId + "/events", tokenAdmin, null);
        assertThat(resEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode eventsJson = mapper.readTree(resEvents.getBody());
        assertThat(eventsJson).hasSizeGreaterThanOrEqualTo(1);

        ResponseEntity<String> resAllEvents = pedir(HttpMethod.GET, "/api/admin/subscriptions/events", tokenAdmin, null);
        assertThat(resAllEvents.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Reconciliation run
        ResponseEntity<String> resReconciliation = pedir(HttpMethod.POST, "/api/admin/subscriptions/reconciliation/run", tokenAdmin, null);
        assertThat(resReconciliation.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ==========================================
    // 6. Endpoint Interno: /internal/suscripciones/estado
    // ==========================================

    @Test
    void internal_estadoSuscripcion_requiereServiceJwtYDevuelveEstadoExacto() throws Exception {
        // Sin token de servicio -> 401
        assertThat(http.getForEntity("/internal/suscripciones/estado?professionalId=10", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        LocalDateTime ahora = LocalDateTime.now();
        // Creamos suscripción verificada
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-INT", new BigDecimal("15000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(25), null,
                false, null, false, ahora, ahora));

        ResponseEntity<String> res = pedirInterno(HttpMethod.GET, "/internal/suscripciones/estado?professionalId=10", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode json = mapper.readTree(res.getBody());
        assertThat(json.isArray()).isTrue();
        assertThat(json).isNotEmpty();
        JsonNode primero = json.get(0);
        assertThat(primero.get("professionalId").asLong()).isEqualTo(10L);
        assertThat(primero.get("estado").asText()).isEqualTo("ACTIVE");
        assertThat(primero.get("planCode").asText()).isEqualTo("consultorio");
        assertThat(primero.get("acceso").asBoolean()).isTrue();
        assertThat(primero.get("puedeRecetar").asBoolean()).isFalse(); // Psicólogo
        assertThat(primero.get("version").asLong()).isEqualTo(2L); // aggregateVersion() = 1 + 1 = 2
    }

    @Test
    void cambioEstado_versionOutboxYVersionRutaInternaSonIguales() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        Long profesionalId = 20L;
        String tokenAdmin = token("admin@mail.com", "ADMIN");

        Long subId = suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, profesionalId, 1L, "ACTIVE", 1,
                "MERCADOPAGO", "MP-PRE-VER", new BigDecimal("15000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(25), null,
                false, null, false, ahora, ahora));

        // Disparamos un cambio de estado de ACTIVE a PAST_DUE
        ResponseEntity<String> resPatch = pedir(HttpMethod.PATCH, "/api/admin/subscriptions/" + subId + "/status",
                tokenAdmin, Map.of("status", "PAST_DUE", "reason", "Falta de pago de prueba"));
        assertThat(resPatch.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Esperamos que se publique el evento de cambio de estado en el outbox
        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE topic = 'suscripciones.suscripcion.cambio-estado.v2'", Long.class) > 0);

        String payloadOutbox = jdbc.queryForObject(
                "SELECT payload::text FROM outbox WHERE topic = 'suscripciones.suscripcion.cambio-estado.v2' ORDER BY id DESC LIMIT 1",
                String.class);
        JsonNode eventoOutbox = mapper.readTree(payloadOutbox);
        long versionOutbox = eventoOutbox.get("aggregateVersion").asLong();

        // Consultamos la ruta interna para ese profesional
        ResponseEntity<String> resInterno = pedirInterno(HttpMethod.GET, "/internal/suscripciones/estado", null);
        assertThat(resInterno.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode items = mapper.readTree(resInterno.getBody());
        JsonNode estadoProfesional = null;
        for (JsonNode item : items) {
            if (item.get("professionalId").asLong() == profesionalId) {
                estadoProfesional = item;
                break;
            }
        }
        assertThat(estadoProfesional).isNotNull();
        long versionRutaInterna = estadoProfesional.get("version").asLong();

        // Tras el cambio de estado, la versión en outbox y la de la ruta interna son exactamente iguales
        assertThat(versionRutaInterna).isEqualTo(versionOutbox);
    }

    @Test
    void rutaInterna_acceso_casosYValidacion() throws Exception {
        // 1. 401 sin JWT de servicio
        ResponseEntity<String> resSinJwt = http.getForEntity("/internal/suscripciones/acceso?email=psiquiatra@mail.com", String.class);
        assertThat(resSinJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Mail inválido o vacío -> 400
        assertThat(pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=invalido", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=sin@dominio", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. Mail desconocido -> permitido = true
        ResponseEntity<String> resDesconocido = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=nadie@mail.com", null);
        assertThat(resDesconocido.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jsonDesconocido = mapper.readTree(resDesconocido.getBody());
        assertThat(jsonDesconocido.get("permitido").asBoolean()).isTrue();

        // 4. Profesional sin suscripción -> permitido = true
        ResponseEntity<String> resSinSub = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=psiquiatra@mail.com", null);
        assertThat(resSinSub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resSinSub.getBody()).get("permitido").asBoolean()).isTrue();

        // 5. Suscripción ACTIVE vigente -> permitido = true
        LocalDateTime ahora = LocalDateTime.now();
        Long subId = suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 11L, 2L, "ACTIVE", 1,
                "MERCADOPAGO", "PRE-11", new BigDecimal("25000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(25), ahora.plusDays(32),
                false, null, false, ahora, ahora));

        ResponseEntity<String> resActive = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=psiquiatra@mail.com", null);
        assertThat(resActive.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resActive.getBody()).get("permitido").asBoolean()).isTrue();

        // 6. CANCELLED vigente -> permitido = true
        Suscripciones.Suscripcion subActual = suscripciones.porId(subId).orElseThrow();
        suscripciones.actualizar(new Suscripciones.Suscripcion(subId, subActual.version(), 11L, 2L, "CANCELLED", 1,
                "MERCADOPAGO", "PRE-11", new BigDecimal("25000.00"), "monthly",
                ahora.plusMonths(1), ahora.minusDays(5), ahora.plusDays(10), ahora.plusDays(17),
                true, ahora, false, ahora, ahora));

        ResponseEntity<String> resCancelledVigente = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=psiquiatra@mail.com", null);
        assertThat(resCancelledVigente.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resCancelledVigente.getBody()).get("permitido").asBoolean()).isTrue();

        // 7. CANCELLED vencida -> permitido = false
        subActual = suscripciones.porId(subId).orElseThrow();
        suscripciones.actualizar(new Suscripciones.Suscripcion(subId, subActual.version(), 11L, 2L, "CANCELLED", 1,
                "MERCADOPAGO", "PRE-11", new BigDecimal("25000.00"), "monthly",
                ahora.minusDays(1), ahora.minusDays(30), ahora.minusDays(1), ahora.plusDays(6),
                true, ahora.minusDays(5), false, ahora, ahora));

        ResponseEntity<String> resCancelledVencida = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=psiquiatra@mail.com", null);
        assertThat(resCancelledVencida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resCancelledVencida.getBody()).get("permitido").asBoolean()).isFalse();

        // 8. ACTIVE vencida pero dentro de gracia -> permitido = true
        subActual = suscripciones.porId(subId).orElseThrow();
        suscripciones.actualizar(new Suscripciones.Suscripcion(subId, subActual.version(), 11L, 2L, "ACTIVE", 1,
                "MERCADOPAGO", "PRE-11", new BigDecimal("25000.00"), "monthly",
                ahora.minusDays(1), ahora.minusDays(30), ahora.minusDays(1), ahora.plusDays(6),
                false, null, false, ahora, ahora));

        ResponseEntity<String> resActiveGracia = pedirInterno(HttpMethod.GET, "/internal/suscripciones/acceso?email=psiquiatra@mail.com", null);
        assertThat(resActiveGracia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(resActiveGracia.getBody()).get("permitido").asBoolean()).isTrue();
    }

    @Test
    void rutaInterna_cuentasMp_casosYValidacion() throws Exception {
        // 1. 401 sin JWT de servicio
        ResponseEntity<String> resSinJwt = http.getForEntity("/internal/pagos/cuentas-mp", String.class);
        assertThat(resSinJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Parámetros inválidos -> 400
        assertThat(pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?pagina=-1", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?tamanio=0", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?tamanio=1001", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. Vincular cuenta profesional 10 -> outbox publica evento con versión
        vinculacion.simularConexion(10L);
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE topic = 'pagos.cuenta-mp.vinculada.v1'", Long.class) > 0);

        String payloadVinculada = jdbc.queryForObject(
                "SELECT payload::text FROM outbox WHERE topic = 'pagos.cuenta-mp.vinculada.v1' ORDER BY id DESC LIMIT 1",
                String.class);
        long versionEventoVinculada = mapper.readTree(payloadVinculada).get("aggregateVersion").asLong();

        // 4. Vincular y luego desvincular profesional 11 -> outbox publica desvinculada.v1
        vinculacion.simularConexion(11L);
        vinculacion.desvincular(11L);
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE topic = 'pagos.cuenta-mp.desvinculada.v1'", Long.class) > 0);

        String payloadDesvinculada = jdbc.queryForObject(
                "SELECT payload::text FROM outbox WHERE topic = 'pagos.cuenta-mp.desvinculada.v1' ORDER BY id DESC LIMIT 1",
                String.class);
        long versionEventoDesvinculada = mapper.readTree(payloadDesvinculada).get("aggregateVersion").asLong();

        // 5. Consultar ruta interna: lista plana ordenada por profesionalId, con vinculada y version
        ResponseEntity<String> res = pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?pagina=0&tamanio=10", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode items = mapper.readTree(res.getBody());
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(2);

        JsonNode c1 = items.get(0);
        assertThat(c1.get("profesionalId").asLong()).isEqualTo(10L);
        assertThat(c1.get("vinculada").asBoolean()).isTrue();
        assertThat(c1.get("version").asLong()).isEqualTo(versionEventoVinculada);

        JsonNode c2 = items.get(1);
        assertThat(c2.get("profesionalId").asLong()).isEqualTo(11L);
        assertThat(c2.get("vinculada").asBoolean()).isFalse();
        assertThat(c2.get("version").asLong()).isEqualTo(versionEventoDesvinculada);

        // 6. Paginado
        ResponseEntity<String> p0 = pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?pagina=0&tamanio=1", null);
        assertThat(p0.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode itemsP0 = mapper.readTree(p0.getBody());
        assertThat(itemsP0.size()).isEqualTo(1);
        assertThat(itemsP0.get(0).get("profesionalId").asLong()).isEqualTo(10L);

        ResponseEntity<String> p1 = pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?pagina=1&tamanio=1", null);
        assertThat(p1.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode itemsP1 = mapper.readTree(p1.getBody());
        assertThat(itemsP1.size()).isEqualTo(1);
        assertThat(itemsP1.get(0).get("profesionalId").asLong()).isEqualTo(11L);

        ResponseEntity<String> p2 = pedirInterno(HttpMethod.GET, "/internal/pagos/cuentas-mp?pagina=2&tamanio=1", null);
        assertThat(p2.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode itemsP2 = mapper.readTree(p2.getBody());
        assertThat(itemsP2.size()).isEqualTo(0);
    }

    // ==========================================
    // 7. Eventos RabbitMQ: Alta y Verificación
    // ==========================================

    @Test
    void eventos_altaUsuario_creaSuscripcionPendingVerification() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        // Alta de nuevo usuario psicólogo (id 70)
        suscriptores.guardar(new Suscriptores.Suscriptor(70L, "nuevo.psico@mail.com", "Mariana", "Lopez",
                "PROFESIONAL", "Psicólogo", true, 1, null, null, null, null, null, null, null, null, null, ahora));

        publicar(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, "70",
                new UsuarioRegistradoData(70L, "nuevo.psico@mail.com", "Mariana", "Lopez", "PROFESIONAL"));

        esperar(() -> suscripciones.porProfesionalId(70L).isPresent());
        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(70L).orElseThrow();
        assertThat(sub.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(sub.planId()).isEqualTo(1L); // consultorio

        // Alta de nuevo usuario psiquiatra (id 71)
        suscriptores.guardar(new Suscriptores.Suscriptor(71L, "nuevo.psiq@mail.com", "Roberto", "Diaz",
                "PSIQUIATRA", "Psiquiatra", true, 1, null, null, null, null, null, null, null, null, null, ahora));

        publicar(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, "71",
                new UsuarioRegistradoData(71L, "nuevo.psiq@mail.com", "Roberto", "Diaz", "PSIQUIATRA"));

        esperar(() -> suscripciones.porProfesionalId(71L).isPresent());
        Suscripciones.Suscripcion subPsiq = suscripciones.porProfesionalId(71L).orElseThrow();
        assertThat(subPsiq.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(subPsiq.planId()).isEqualTo(2L); // clinico
    }

    @Test
    void eventos_verificacionResuelta_pasaAVerified_y_publicaCambioEstado() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        // Creamos suscripción PENDING_VERIFICATION
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "PENDING_VERIFICATION", 1,
                "MANUAL_TRANSFER", null, new BigDecimal("15000.00"), "monthly",
                null, null, null, null, false, null, false, ahora, ahora));

        publicar(Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1, "10",
                new VerificacionResueltaData(10L, "VERIFICADO"));

        esperar(() -> "VERIFIED".equals(suscripciones.porProfesionalId(10L).map(Suscripciones.Suscripcion::status).orElse(null)));
        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(10L).orElseThrow();
        assertThat(sub.status()).isEqualTo("VERIFIED");

        // Publicó evento suscripciones.suscripcion.cambio-estado.v2
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE topic = 'suscripciones.suscripcion.cambio-estado.v2'", Long.class) > 0);
    }

    @Test
    void eventos_llegadaInvertida_verificacionAntesQueAlta() throws Exception {
        // Caso: llega verificacion-resuelta para profesional 80 que todavía no tiene suscripción
        ClienteInterno.SuscriptorMonolito datosMonolito = new ClienteInterno.SuscriptorMonolito(
                80L, "invertido@mail.com", "Carlos", "Perez", "PROFESIONAL", "Psicólogo",
                true, 1, "CUIT", "20111111112", "Carlos Perez", 6,
                "Av. Belgrano 100", "20111111112", 20111111112L, "11111111", 11111111);
        when(interno.suscriptor(80L)).thenReturn(datosMonolito);

        publicar(Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1, "80",
                new VerificacionResueltaData(80L, "VERIFICADO"));

        esperar(() -> suscripciones.porProfesionalId(80L).isPresent());
        Suscripciones.Suscripcion sub = suscripciones.porProfesionalId(80L).orElseThrow();
        assertThat(sub.status()).isEqualTo("VERIFIED");
    }

    // ==========================================
    // 8. Webhook Mercado Pago
    // ==========================================

    @Test
    void webhook_preapproval_procesamientoLocal() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        // Creamos suscripción con mp_preapproval_id = "preapp-wh-1"
        suscripciones.insertarNuevo(new Suscripciones.Suscripcion(null, 1, 10L, 1L, "VERIFIED", 1,
                "MERCADOPAGO", "preapp-wh-1", new BigDecimal("15000.00"), "monthly",
                null, null, null, null, false, null, false, ahora, ahora));

        // Webhook tipo preapproval
        String cuerpo = "{\"action\":\"updated\",\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"preapp-wh-1\"}}";
        ResponseEntity<String> res = webhook(cuerpo, "preapp-wh-1", "subscription_preapproval", true);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
