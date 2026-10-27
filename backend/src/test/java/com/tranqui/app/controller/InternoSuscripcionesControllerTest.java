package com.tranqui.app.controller;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 8, S1: rutas internas del monolito para la carga inicial de suscripciones en el servicio de pagos.
 * Solo con JWT de servicio, paginadas por id y con fechas en formato ISO.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoSuscripcionesControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private PlanRepository planRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private PlanFeatureRepository planFeatureRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionEventRepository subscriptionEventRepository;
    @Autowired private SubscriptionPaymentRepository subscriptionPaymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceSequenceRepository invoiceSequenceRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private ResponseEntity<String> get(String ruta, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        if (conToken) {
            headers.setBearerAuth(new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("pagos"));
        }
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Usuario crearUsuario(String nombre, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .apellido("Test")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@mail.com")
                .rol(rol)
                .profession("Psiquiatría")
                .verificadoAdmin(true)
                .cuit("20123456789")
                .dni("12345678")
                .legalName(nombre + " Test")
                .taxIdType("CUIT")
                .taxId("20123456789")
                .fiscalAddress("Av Corrientes 1234")
                .ivaConditionId(1)
                .build());
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(get("/internal/monolito/suscripciones/planes", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/features", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/plan-features", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/suscripciones", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/eventos", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/pagos", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/facturas", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscripciones/secuencias", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscriptores", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/suscriptores/1", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void planesYFeatures_paginacion() {
        Plan plan = planRepository.save(Plan.builder()
                .code("TEST_PLAN_" + System.nanoTime())
                .name("Plan Test Interno")
                .description("Descripción test")
                .priceArs(new BigDecimal("9900.00"))
                .billingPeriod("MONTHLY")
                .minSeats(1)
                .requiresPrescriber(false)
                .isActive(true)
                .build());

        ResponseEntity<String> rPlanes = get("/internal/monolito/suscripciones/planes?pagina=0&tamanio=1000", true);
        assertThat(rPlanes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rPlanes.getBody())
                .contains("\"id\":" + plan.getId())
                .contains("\"code\":\"" + plan.getCode() + "\"")
                .contains("\"priceArs\":9900.00");

        Feature feature = featureRepository.save(Feature.builder()
                .key("feat_test_" + System.nanoTime())
                .name("Feature Test")
                .description("Desc feat")
                .build());

        ResponseEntity<String> rFeat = get("/internal/monolito/suscripciones/features?pagina=0&tamanio=1000", true);
        assertThat(rFeat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rFeat.getBody())
                .contains("\"key\":\"" + feature.getKey() + "\"")
                .contains("\"name\":\"Feature Test\"");

        PlanFeature pf = planFeatureRepository.save(PlanFeature.builder()
                .plan(plan)
                .featureKey(feature.getKey())
                .limitValue(50)
                .build());

        ResponseEntity<String> rPf = get("/internal/monolito/suscripciones/plan-features?pagina=0&tamanio=1000", true);
        assertThat(rPf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rPf.getBody())
                .contains("\"id\":" + pf.getId())
                .contains("\"planId\":" + plan.getId())
                .contains("\"featureKey\":\"" + feature.getKey() + "\"")
                .contains("\"limitValue\":50");
    }

    @Test
    void suscripcionesYEventos_paginacion() {
        Usuario prof = crearUsuario("DocSub", Rol.PSIQUIATRA);
        Plan plan = planRepository.findAll().stream().findFirst().orElseGet(() -> planRepository.save(Plan.builder()
                .code("PLAN_SUB_TEST")
                .name("Plan Sub")
                .priceArs(BigDecimal.TEN)
                .billingPeriod("MONTHLY")
                .isActive(true)
                .build()));

        Subscription sub = subscriptionRepository.save(Subscription.builder()
                .professional(prof)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .seats(1)
                .billingSource(BillingSource.MERCADOPAGO)
                .amountArs(new BigDecimal("12000.00"))
                .billingCycle("MONTHLY")
                .currentPeriodStart(LocalDateTime.of(2026, 10, 1, 0, 0))
                .currentPeriodEnd(LocalDateTime.of(2026, 10, 31, 23, 59))
                .build());

        ResponseEntity<String> rSub = get("/internal/monolito/suscripciones/suscripciones?pagina=0&tamanio=1000", true);
        assertThat(rSub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rSub.getBody())
                .contains("\"id\":" + sub.getId())
                .contains("\"professionalId\":" + prof.getId())
                .contains("\"planId\":" + plan.getId())
                .contains("\"status\":\"ACTIVE\"");

        SubscriptionEvent event = subscriptionEventRepository.save(SubscriptionEvent.builder()
                .subscriptionId(sub.getId())
                .eventType("STATUS_CHANGE")
                .previousStatus("INACTIVE")
                .newStatus("ACTIVE")
                .actorType("SYSTEM")
                .actorId("1")
                .payloadJson("{\"reason\":\"verified\"}")
                .build());

        ResponseEntity<String> rEvt = get("/internal/monolito/suscripciones/eventos?pagina=0&tamanio=1000", true);
        assertThat(rEvt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rEvt.getBody())
                .contains("\"id\":" + event.getId())
                .contains("\"subscriptionId\":" + sub.getId())
                .contains("\"newStatus\":\"ACTIVE\"");
    }

    @Test
    void facturas_paginacionYFechasIso() {
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .cbteTipo(11)
                .puntoVenta(1)
                .cbteNumero(42L)
                .cae("12345678901234")
                .caeVencimiento(LocalDate.of(2026, 11, 15))
                .receptorDocTipo(80)
                .receptorDocNro(20123456789L)
                .receptorNombre("Doctor Facturado")
                .receptorCondicionIva(1)
                .importeTotal(new BigDecimal("25000.00"))
                .moneda("PES")
                .cotizacion(BigDecimal.ONE)
                .fechaEmision(LocalDate.of(2026, 10, 26))
                .status(InvoiceStatus.ISSUED)
                .attempts(1)
                .build());

        ResponseEntity<String> r = get("/internal/monolito/suscripciones/facturas?pagina=0&tamanio=20", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"id\":" + invoice.getId())
                .contains("\"cbteNumero\":42")
                .contains("\"cae\":\"12345678901234\"")
                .contains("\"fechaEmision\":\"2026-10-26\"")
                .doesNotContain("26/10/2026")
                .contains("\"caeVencimiento\":\"2026-11-15\"")
                .doesNotContain("15/11/2026")
                .contains("\"importeTotal\":25000.00");
    }

    @Test
    void secuencias_paginacion() {
        InvoiceSequence seq = invoiceSequenceRepository.save(InvoiceSequence.builder()
                .puntoVenta(1)
                .cbteTipo(11)
                .lastNumber(105L)
                .build());

        ResponseEntity<String> r = get("/internal/monolito/suscripciones/secuencias?pagina=0&tamanio=500", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"puntoVenta\":1")
                .contains("\"cbteTipo\":11")
                .contains("\"lastNumber\":105");
    }

    @Test
    void suscriptores_porIdYLista() {
        Usuario prof = crearUsuario("SuscriptorTest", Rol.PSIQUIATRA);

        ResponseEntity<String> rId = get("/internal/monolito/suscriptores/" + prof.getId(), true);
        assertThat(rId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rId.getBody())
                .contains("\"id\":" + prof.getId())
                .contains("\"nombre\":\"SuscriptorTest\"")
                .contains("\"rol\":\"PSIQUIATRA\"")
                .contains("\"cuit\":\"20123456789\"")
                .contains("\"taxId\":\"20123456789\"");

        ResponseEntity<String> rLista = get("/internal/monolito/suscriptores?pagina=0&tamanio=500", true);
        assertThat(rLista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rLista.getBody())
                .contains("\"id\":" + prof.getId())
                .contains("\"nombre\":\"SuscriptorTest\"");

        ResponseEntity<String> rNotFound = get("/internal/monolito/suscriptores/999999", true);
        assertThat(rNotFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
