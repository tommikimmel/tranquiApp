package com.tranqui.pagos.facturacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.ClienteInterno;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de integración para facturación ARCA y endpoints de administración (/api/admin/subscriptions/invoices).
 * Valida roles ADMIN (403 para otros), JSON idéntico al monolito, comportamiento con ARCA apagada
 * y emisión de facturas y notas de crédito con CAE simulado (Fase 8, S2c).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "pagos.carga-inicial.habilitada=false",
        "pagos.suscripciones.habilitada=true",
        "jwt.secret=" + FacturacionAdminIntegracionTest.CLAVE_JWT,
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "security.encryption.key=clave-de-cifrado-de-test-de-32-bytes",
        "arca.enabled=false"
})
class FacturacionAdminIntegracionTest {

    static final String CLAVE_JWT = "clave-de-test-de-al-menos-32-bytes-ok";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @MockitoBean
    ClienteInterno interno;

    @MockitoBean
    ArcaWsfeService arcaWsfeService;

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Facturas facturas;

    @Autowired
    SecuenciasFactura secuenciasFactura;

    @Autowired
    PagosSuscripcion pagosSuscripcion;

    @Autowired
    Suscripciones suscripciones;

    @Autowired
    Planes planes;

    @Autowired
    Suscriptores suscriptores;

    @Autowired
    InvoiceService invoiceService;

    @Autowired
    ArcaConfig arcaConfig;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void limpiarBase() {
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM invoice_sequences");
        jdbc.execute("DELETE FROM subscription_payments");
        jdbc.execute("DELETE FROM subscription_events");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("DELETE FROM suscriptor");
        jdbc.execute("DELETE FROM plan_features");
        jdbc.execute("DELETE FROM plans");
        jdbc.execute("DELETE FROM features");

        LocalDateTime ahora = LocalDateTime.now();

        // Sembrar plan y features
        planes.insertar(new Planes.Plan(1L, "inicial", "Plan Inicial", "Descripción",
                new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("100000.00"),
                "monthly", 1, null, false, true, ahora, null));

        // Sembrar usuarios
        suscriptores.guardar(new Suscriptores.Suscriptor(10L, "dr.gonzalez@example.com", "Juan", "Gonzalez",
                "PSIQUIATRA", "Psiquiatra", true, 1, "CUIT", "20304050607", "Juan Gonzalez", 6,
                "Av. Corrientes 500", "20304050607", 20304050607L, "30405060", 30405060, ahora));

        suscriptores.guardar(new Suscriptores.Suscriptor(99L, "admin@example.com", "Admin", "User",
                "ADMIN", null, true, 1, null, null, null, null, null, null, null, null, null, ahora));

        // Sembrar suscripción
        suscripciones.insertar(new Suscripciones.Suscripcion(100L, 0, 10L, 1L, "ACTIVE",
                1, "MERCADO_PAGO", "preapproval-1", new BigDecimal("10000.00"), "monthly",
                ahora.plusMonths(1), ahora, ahora.plusMonths(1), null, false, null, false, ahora, ahora));

        // Sembrar pago
        pagosSuscripcion.insertar(new PagosSuscripcion.PagoSuscripcion(500L, 100L, 10L, "mp-pay-1",
                "idemp-1", "MERCADO_PAGO", "APPROVED", new BigDecimal("10000.00"), ahora,
                ahora, ahora.plusMonths(1), null, null, "REF-1", "Pago aprobado", ahora));
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

    @Test
    void testGetInvoices_seguridadYFormatoJson() throws Exception {
        // 1. Sin token -> 401
        ResponseEntity<String> rSinToken = pedir(HttpMethod.GET, "/api/admin/subscriptions/invoices", null, null);
        assertThat(rSinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Rol PSIQUIATRA -> 403
        ResponseEntity<String> rNoAdmin = pedir(HttpMethod.GET, "/api/admin/subscriptions/invoices",
                token("dr.gonzalez@example.com", "PSIQUIATRA"), null);
        assertThat(rNoAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Insertar factura de prueba
        Facturas.Factura f = new Facturas.Factura(
                1L, 500L, 11, 1, 101L, "74123456789012", LocalDate.now().plusDays(10),
                80, 20304050607L, "Juan Gonzalez", 6, new BigDecimal("10000.00"),
                "PES", BigDecimal.ONE, LocalDate.now(), "/api/subscriptions/invoices/1/pdf",
                "BASE64_DUMMY", "ISSUED", 1, null, "<req/>", "<resp/>", null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        facturas.insertar(f);

        // 4. Con rol ADMIN -> 200 OK y JSON completo
        ResponseEntity<String> rAdmin = pedir(HttpMethod.GET, "/api/admin/subscriptions/invoices",
                token("admin@example.com", "ADMIN"), null);
        assertThat(rAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode json = objectMapper.readTree(rAdmin.getBody());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(1);

        JsonNode item = json.get(0);
        assertThat(item.get("id").asLong()).isEqualTo(1L);
        assertThat(item.get("paymentId").asLong()).isEqualTo(500L);
        assertThat(item.get("cbteTipo").asInt()).isEqualTo(11);
        assertThat(item.get("cbteTipoNombre").asText()).isEqualTo("Factura C");
        assertThat(item.get("puntoVenta").asInt()).isEqualTo(1);
        assertThat(item.get("cbteNumero").asLong()).isEqualTo(101L);
        assertThat(item.get("cae").asText()).isEqualTo("74123456789012");
        assertThat(item.get("receptorNombre").asText()).isEqualTo("Juan Gonzalez");
        assertThat(item.get("receptorDocNro").asLong()).isEqualTo(20304050607L);
        assertThat(item.get("receptorCondicionIva").asInt()).isEqualTo(6);
        assertThat(item.get("importeTotal").asDouble()).isEqualTo(10000.00);
        assertThat(item.get("status").asText()).isEqualTo("ISSUED");
        assertThat(item.get("pdfUrl").asText()).isEqualTo("/api/subscriptions/invoices/1/pdf");
    }

    @Test
    void testCreditNote_seguridadYArcaApagada() throws Exception {
        // 1. Sin token -> 401
        ResponseEntity<String> rSinToken = pedir(HttpMethod.POST, "/api/admin/subscriptions/invoices/1/credit-note",
                null, Map.of("reason", "Anulación"));
        assertThat(rSinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Rol no ADMIN -> 403
        ResponseEntity<String> rNoAdmin = pedir(HttpMethod.POST, "/api/admin/subscriptions/invoices/1/credit-note",
                token("dr.gonzalez@example.com", "PSIQUIATRA"), Map.of("reason", "Anulación"));
        assertThat(rNoAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Con ADMIN y ARCA apagada (default) -> 400 con mensaje explicativo
        arcaConfig.setEnabled(false);
        ResponseEntity<String> rArcaOff = pedir(HttpMethod.POST, "/api/admin/subscriptions/invoices/1/credit-note",
                token("admin@example.com", "ADMIN"), Map.of("reason", "Anulación"));
        assertThat(rArcaOff.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode errJson = objectMapper.readTree(rArcaOff.getBody());
        assertThat(errJson.get("error").asText()).contains("facturación electrónica ARCA todavía no está habilitada");
    }

    @Test
    void testEmisionFacturaYNotaDeCreditoConArcaHabilitada() throws Exception {
        arcaConfig.setEnabled(true);
        try {
            // Mock de respuesta aprobada de WSFE
            when(arcaWsfeService.solicitarCae(any())).thenAnswer(inv -> {
                ArcaWsfeService.SolicitudCaeRequest req = inv.getArgument(0);
                return ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("A")
                        .cae("74987654321012")
                        .caeFchVto(LocalDate.now().plusDays(10))
                        .cbteNumero(req.getCbteNumero())
                        .rawRequestXml("<xmlReq/>")
                        .rawResponseXml("<xmlResp/>")
                        .build();
            });

            // 1. Emitir Factura C para el pago 500
            Facturas.Factura facturaEmitida = invoiceService.generateInvoiceForPayment(500L);
            assertThat(facturaEmitida).isNotNull();
            assertThat(facturaEmitida.cbteTipo()).isEqualTo(11);
            assertThat(facturaEmitida.status()).isEqualTo("ISSUED");
            assertThat(facturaEmitida.cae()).isEqualTo("74987654321012");
            assertThat(facturaEmitida.cbteNumero()).isEqualTo(1L); // primer comprobante
            assertThat(facturaEmitida.pdfBase64()).isNotBlank();
            assertThat(facturaEmitida.pdfUrl()).isEqualTo("/api/subscriptions/invoices/" + facturaEmitida.id() + "/pdf");

            // Verificar que el pago se actualizó con la factura
            PagosSuscripcion.PagoSuscripcion pagoActualizado = pagosSuscripcion.porId(500L).orElseThrow();
            assertThat(pagoActualizado.invoiceId()).isEqualTo(facturaEmitida.id());

            // 2. Emitir Nota de Crédito C para anular la Factura C
            ResponseEntity<String> rNc = pedir(HttpMethod.POST,
                    "/api/admin/subscriptions/invoices/" + facturaEmitida.id() + "/credit-note",
                    token("admin@example.com", "ADMIN"),
                    Map.of("reason", "Anulación por reclamo"));
            assertThat(rNc.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode ncJson = objectMapper.readTree(rNc.getBody());
            assertThat(ncJson.get("cbteTipo").asInt()).isEqualTo(13);
            assertThat(ncJson.get("status").asText()).isEqualTo("ISSUED");
            assertThat(ncJson.get("comprobanteAsociadoId").asLong()).isEqualTo(facturaEmitida.id());
            assertThat(ncJson.get("cbteNumero").asLong()).isEqualTo(1L); // primer comprobante tipo 13

            // Verificar que la factura original quedó en VOIDED
            Facturas.Factura originalActualizada = facturas.porId(facturaEmitida.id()).orElseThrow();
            assertThat(originalActualizada.status()).isEqualTo("VOIDED");
        } finally {
            arcaConfig.setEnabled(false);
        }
    }

    @Test
    void testEmisionFactura_caeFallido_quemaNumeroYQuedaFailed() {
        // Decisión 4: ARCA falla -> factura en FAILED, número consumido, payment.invoiceId queda null
        arcaConfig.setEnabled(true);
        try {
            when(arcaWsfeService.solicitarCae(any())).thenReturn(
                    ArcaWsfeService.ArcaCaeResponse.builder()
                            .resultado("R")
                            .errores(List.of("10015: CUIT no autorizado"))
                            .rawRequestXml("<xmlReq/>")
                            .rawResponseXml("<xmlRespErr/>")
                            .build()
            );

            Facturas.Factura facturaFallida = invoiceService.generateInvoiceForPayment(500L);
            assertThat(facturaFallida).isNotNull();
            assertThat(facturaFallida.status()).isEqualTo("FAILED");
            assertThat(facturaFallida.lastError()).contains("10015: CUIT no autorizado");
            assertThat(facturaFallida.cae()).isNull();
            assertThat(facturaFallida.cbteNumero()).isEqualTo(1L); // El número 1 quedó asignado a la factura fallida

            // El pago NO debe apuntar a la factura fallida
            PagosSuscripcion.PagoSuscripcion pago = pagosSuscripcion.porId(500L).orElseThrow();
            assertThat(pago.invoiceId()).isNull();

            // La secuencia quedó en 1
            long prox = secuenciasFactura.proximoNumero(1, 11);
            assertThat(prox).isEqualTo(2L);
        } finally {
            arcaConfig.setEnabled(false);
        }
    }

    @Test
    void testEmisionFactura_arcaApagada_noConsumeNumeroNiGeneraFactura() {
        arcaConfig.setEnabled(false);

        Facturas.Factura res = invoiceService.generateInvoiceForPayment(500L);
        assertThat(res).isNull();

        // No hay facturas ni secuencias consumidas
        assertThat(facturas.cantidad()).isEqualTo(0);
        assertThat(secuenciasFactura.cantidad()).isEqualTo(0);

        PagosSuscripcion.PagoSuscripcion pago = pagosSuscripcion.porId(500L).orElseThrow();
        assertThat(pago.invoiceId()).isNull();
    }
}
