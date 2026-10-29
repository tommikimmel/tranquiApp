package com.tranqui.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.interno.Copias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Tests de integración de la Fase 8 (S2a): esquema, copias, carga inicial de suscripciones
 * y replicación de repositorios sobre PostgreSQL y RabbitMQ reales.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "management.server.port=0",
        "pagos.carga-inicial.habilitada=false",
        "pagos.suscripciones.habilitada=false",
        "mercadopago.enabled=false",
        "payment.simulation.enabled=false",
        "app.public-url=https://tranqui.test",
        "app.frontend-url=https://front.test",
        "jwt.secret=clave-de-test-de-al-menos-32-bytes-ok",
        "tranqui.jwt.service-secret=clave-servicio-test-de-32-bytes-ok",
        "security.encryption.key=clave-de-cifrado-de-test-de-32-bytes",
        "tranqui.gateway.url=http://localhost:1"
})
class SuscripcionesIntegracionTest {

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
    @Autowired Facturas facturas;
    @Autowired SecuenciasFactura secuenciasFactura;
    @Autowired Suscriptores suscriptores;
    @Autowired Copias copias;
    @Autowired CargaInicialSuscripciones cargaInicialSuscripciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    @BeforeEach
    void preparar() throws InterruptedException {
        RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
        esperar(() -> {
            var info = admin.getQueueInfo(RabbitMQConstantes.colaEventos("pagos"));
            return info != null && info.getConsumerCount() > 0;
        });

        for (String tabla : List.of("invoices", "subscription_payments", "subscription_events",
                "subscriptions", "plan_features", "plans", "features", "invoice_sequences",
                "suscriptor", "eventos_procesados", "versiones_aplicadas")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        reset(interno);
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

    @Test
    void migracion_creaLasTablasYSecuencias() {
        Integer tablas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('plans', 'features', 'plan_features', 'subscriptions', "
                        + "'subscription_events', 'subscription_payments', 'invoices', 'invoice_sequences', 'suscriptor')",
                Integer.class);
        assertThat(tablas).isEqualTo(9);

        Integer secuencias = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.sequences WHERE sequence_schema = 'public' "
                        + "AND sequence_name IN ('plans_id_seq', 'plan_features_id_seq', 'subscriptions_id_seq', "
                        + "'subscription_events_id_seq', 'subscription_payments_id_seq', 'invoices_id_seq', 'invoice_sequences_id_seq')",
                Integer.class);
        assertThat(secuencias).isEqualTo(7);
    }

    @Test
    void cargaInicialSuscripciones_copiaTalCualYSecuenciasQuedanArribaDelMaximo() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = LocalDate.now();

        // 1. Simulación de los 8 conjuntos de datos del monolito
        ClienteInterno.PlanMonolito plan = new ClienteInterno.PlanMonolito(
                10L, "clinico", "Plan Clínico", "Descripción clínica",
                new BigDecimal("60000.00"), new BigDecimal("50.00"), new BigDecimal("600000.00"),
                "monthly", 1, "mp-plan-10", true, true, ahora, null);
        when(interno.planes(eq(0), anyInt())).thenReturn(List.of(plan));

        ClienteInterno.FeatureMonolito feature = new ClienteInterno.FeatureMonolito(
                "recetario", "Recetario digital", "Emisión de recetas QBI2");
        when(interno.features(eq(0), anyInt())).thenReturn(List.of(feature));

        ClienteInterno.PlanFeatureMonolito planFeature = new ClienteInterno.PlanFeatureMonolito(
                15L, 10L, "recetario", 50);
        when(interno.planFeatures(eq(0), anyInt())).thenReturn(List.of(planFeature));

        ClienteInterno.SubscriptionMonolito sub = new ClienteInterno.SubscriptionMonolito(
                100L, 2, 25L, 10L, "ACTIVE", 1, "MERCADOPAGO", "preapproval-100",
                new BigDecimal("60000.00"), "monthly", ahora.plusMonths(1), ahora,
                ahora.plusMonths(1), ahora.plusMonths(1).plusDays(5), false, null, false,
                ahora.minusMonths(1), ahora);
        when(interno.suscripciones(eq(0), anyInt())).thenReturn(List.of(sub));

        ClienteInterno.SubscriptionEventMonolito evento = new ClienteInterno.SubscriptionEventMonolito(
                200L, 100L, "SUBSCRIPTION_RENEWED", "ACTIVE", "ACTIVE",
                "mp_webhook", "wh-1", "Detalles renovación", ahora);
        when(interno.eventosSuscripcion(eq(0), anyInt())).thenReturn(List.of(evento));

        ClienteInterno.SubscriptionPaymentMonolito pago = new ClienteInterno.SubscriptionPaymentMonolito(
                300L, 100L, 25L, "mp-pay-300", "idem-300", "MERCADOPAGO", "APPROVED",
                new BigDecimal("60000.00"), ahora, ahora, ahora.plusMonths(1), 400L,
                null, "rec-300", "Pago mensual", ahora);
        when(interno.pagosSuscripcion(eq(0), anyInt())).thenReturn(List.of(pago));

        ClienteInterno.InvoiceMonolito factura = new ClienteInterno.InvoiceMonolito(
                400L, 300L, 11, 1, 1055L, "74125896301234", hoy.plusDays(10),
                80, 20300400506L, "Dra. Ruiz", 6, new BigDecimal("60000.00"),
                "PES", BigDecimal.ONE, hoy, "https://arca.pdf/400", "JVBERi0xLjQK...",
                "ISSUED", 1, null, "{\"req\":1}", "{\"res\":1}", null, ahora, ahora);
        when(interno.facturas(eq(0), anyInt())).thenReturn(List.of(factura));

        ClienteInterno.InvoiceSequenceMonolito secuencia = new ClienteInterno.InvoiceSequenceMonolito(
                500L, 1, 11, 1055L, ahora);
        when(interno.secuenciasFactura(eq(0), anyInt())).thenReturn(List.of(secuencia));

        // 2. Ejecutar carga
        int total = cargaInicialSuscripciones.cargar();
        assertThat(total).isEqualTo(8);

        // 3. Verificación de copia fiel de ids, estados, montos, números, CAE y versiones
        Planes.Plan planGuardado = planes.porId(10L).orElseThrow();
        assertThat(planGuardado.code()).isEqualTo("clinico");
        assertThat(planGuardado.priceArs()).isEqualByComparingTo("60000.00");
        assertThat(planGuardado.requiresPrescriber()).isTrue();

        Planes.Feature featGuardada = planes.featurePorKey("recetario").orElseThrow();
        assertThat(featGuardada.name()).isEqualTo("Recetario digital");

        List<Planes.PlanFeature> pfList = planes.featuresPorPlanId(10L);
        assertThat(pfList).hasSize(1);
        assertThat(pfList.get(0).id()).isEqualTo(15L);
        assertThat(pfList.get(0).limitValue()).isEqualTo(50);

        Suscripciones.Suscripcion subGuardada = suscripciones.porId(100L).orElseThrow();
        assertThat(subGuardada.professionalId()).isEqualTo(25L);
        assertThat(subGuardada.status()).isEqualTo("ACTIVE");
        assertThat(subGuardada.version()).isEqualTo(2);
        assertThat(subGuardada.amountArs()).isEqualByComparingTo("60000.00");

        EventosSuscripcion.Evento eventoGuardado = eventosSuscripcion.porId(200L).orElseThrow();
        assertThat(eventoGuardado.eventType()).isEqualTo("SUBSCRIPTION_RENEWED");
        assertThat(eventoGuardado.actorType()).isEqualTo("mp_webhook");

        PagosSuscripcion.PagoSuscripcion pagoGuardado = pagosSuscripcion.porId(300L).orElseThrow();
        assertThat(pagoGuardado.mpPaymentId()).isEqualTo("mp-pay-300");
        assertThat(pagoGuardado.idempotencyKey()).isEqualTo("idem-300");
        assertThat(pagoGuardado.amountArs()).isEqualByComparingTo("60000.00");

        Facturas.Factura facturaGuardada = facturas.porId(400L).orElseThrow();
        assertThat(facturaGuardada.cbteNumero()).isEqualTo(1055L);
        assertThat(facturaGuardada.cae()).isEqualTo("74125896301234");
        assertThat(facturaGuardada.pdfBase64()).isEqualTo("JVBERi0xLjQK...");
        assertThat(facturaGuardada.status()).isEqualTo("ISSUED");

        SecuenciasFactura.Secuencia secuenciaGuardada = secuenciasFactura.porPuntoVentaYTipo(1, 11).orElseThrow();
        assertThat(secuenciaGuardada.lastNumber()).isEqualTo(1055L);

        // 4. Verificación de que las secuencias quedan arriba del máximo y un insert nuevo no choca
        Long nuevoPlanId = planes.insertarNuevo(new Planes.Plan(
                null, "equipo", "Plan Equipo", null, new BigDecimal("100000.00"),
                null, null, "monthly", 3, null, false, true, ahora, null));
        assertThat(nuevoPlanId).isGreaterThan(10L);

        Long nuevoPfId = planes.insertarNuevoPlanFeature(new Planes.PlanFeature(
                null, nuevoPlanId, "recetario", 100));
        assertThat(nuevoPfId).isGreaterThan(15L);

        Long nuevaSubId = suscripciones.insertarNuevo(new Suscripciones.Suscripcion(
                null, 0, 99L, nuevoPlanId, "ACTIVE", 1, "MANUAL_CASH", null,
                new BigDecimal("100000.00"), "monthly", ahora.plusMonths(1), ahora,
                ahora.plusMonths(1), null, false, null, false, ahora, ahora));
        assertThat(nuevaSubId).isGreaterThan(100L);

        Long nuevoEvId = eventosSuscripcion.insertarNuevo(new EventosSuscripcion.Evento(
                null, nuevaSubId, "CREATED", null, "ACTIVE", "admin", "admin-1", "Alta", ahora));
        assertThat(nuevoEvId).isGreaterThan(200L);

        Long nuevoPagoId = pagosSuscripcion.insertarNuevo(new PagosSuscripcion.PagoSuscripcion(
                null, nuevaSubId, 99L, null, "idem-nuevo", "CASH", "APPROVED",
                new BigDecimal("100000.00"), ahora, ahora, ahora.plusMonths(1), null,
                1L, "rec-nuevo", null, ahora));
        assertThat(nuevoPagoId).isGreaterThan(300L);

        Long nuevaFacturaId = facturas.insertarNuevo(new Facturas.Factura(
                null, nuevoPagoId, 11, 1, 1056L, "cae-nueva", hoy,
                80, 20300400506L, "Dr. Nuevo", 6, new BigDecimal("100000.00"),
                "PES", BigDecimal.ONE, hoy, null, null, "ISSUED", 1, null,
                null, null, null, ahora, ahora));
        assertThat(nuevaFacturaId).isGreaterThan(400L);

        Long nuevaSecuenciaId = secuenciasFactura.insertarNuevo(new SecuenciasFactura.Secuencia(
                null, 2, 11, 1L, ahora));
        assertThat(nuevaSecuenciaId).isGreaterThan(500L);
    }

    @Test
    void cargaInicialSuscripciones_conDuplicadosPorProfesional_abortaSinInsertar() {
        LocalDateTime ahora = LocalDateTime.now();

        ClienteInterno.SubscriptionMonolito sub1 = new ClienteInterno.SubscriptionMonolito(
                10L, 0, 50L, 1L, "ACTIVE", 1, "MERCADOPAGO", "preapproval-10",
                new BigDecimal("60000.00"), "monthly", ahora, ahora, ahora, null, false, null, false, ahora, ahora);

        ClienteInterno.SubscriptionMonolito sub2 = new ClienteInterno.SubscriptionMonolito(
                11L, 0, 50L, 1L, "PENDING", 1, "MERCADOPAGO", "preapproval-11",
                new BigDecimal("60000.00"), "monthly", ahora, ahora, ahora, null, false, null, false, ahora, ahora);

        when(interno.suscripciones(eq(0), anyInt())).thenReturn(List.of(sub1, sub2));

        assertThatThrownBy(() -> cargaInicialSuscripciones.cargar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suscripciones duplicadas");

        // Verifica que no se insertó nada
        assertThat(suscripciones.cantidad()).isZero();
        assertThat(planes.cantidad()).isZero();
        assertThat(facturas.cantidad()).isZero();
    }

    @Test
    void cargaInicialSuscripciones_deshabilitada_noCopiaNada() {
        assertThat(cargaInicialSuscripciones.listo()).isTrue();
        assertThat(suscripciones.cantidad()).isZero();
        assertThat(planes.cantidad()).isZero();
        assertThat(pagosSuscripcion.cantidad()).isZero();
        assertThat(facturas.cantidad()).isZero();
        assertThat(secuenciasFactura.cantidad()).isZero();
    }

    @Test
    void secuenciasFactura_proximoNumero_incrementaYBloquea() throws Exception {
        int hilos = 5;
        int llamadasPorHilo = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch latch = new CountDownLatch(1);
        List<Long> numerosObtenidos = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futuros = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < llamadasPorHilo; j++) {
                        numerosObtenidos.add(secuenciasFactura.proximoNumero(1, 11));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futuros) {
            f.get();
        }
        pool.shutdown();

        // 50 llamadas deben generar números correlativos del 1 al 50 sin repeticiones
        assertThat(numerosObtenidos).hasSize(hilos * llamadasPorHilo);
        assertThat(numerosObtenidos).doesNotHaveDuplicates();
        assertThat(numerosObtenidos).contains(1L, 50L);

        SecuenciasFactura.Secuencia secuencia = secuenciasFactura.porPuntoVentaYTipo(1, 11).orElseThrow();
        assertThat(secuencia.lastNumber()).isEqualTo(50L);
    }

    @Test
    void suscripciones_controlVersionOptimista() {
        LocalDateTime ahora = LocalDateTime.now();
        suscripciones.insertar(new Suscripciones.Suscripcion(
                1L, 0, 77L, 1L, "ACTIVE", 1, "MANUAL_CASH", null,
                new BigDecimal("60000.00"), "monthly", ahora, ahora, ahora, null, false, null, false, ahora, ahora));

        Suscripciones.Suscripcion inicial = suscripciones.porId(1L).orElseThrow();
        assertThat(inicial.version()).isEqualTo(0);

        // Actualización válida con version=0
        suscripciones.actualizar(new Suscripciones.Suscripcion(
                1L, 0, 77L, 1L, "CANCELLED", 1, "MANUAL_CASH", null,
                new BigDecimal("60000.00"), "monthly", ahora, ahora, ahora, null, true, ahora, false, ahora, ahora));

        Suscripciones.Suscripcion modificada = suscripciones.porId(1L).orElseThrow();
        assertThat(modificada.version()).isEqualTo(1);
        assertThat(modificada.status()).isEqualTo("CANCELLED");

        // Intento de actualizar con versión desactualizada (version=0) debe fallar
        assertThatThrownBy(() -> suscripciones.actualizar(new Suscripciones.Suscripcion(
                1L, 0, 77L, 1L, "ACTIVE", 1, "MANUAL_CASH", null,
                new BigDecimal("60000.00"), "monthly", ahora, ahora, ahora, null, false, null, false, ahora, ahora)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // La versión sigue en 1
        assertThat(suscripciones.porId(1L).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void suscriptores_cargaInicial_refrescoPorEvento_version_y_eliminacion() throws Exception {
        ClienteInterno.SuscriptorMonolito suscriptorInicial = new ClienteInterno.SuscriptorMonolito(
                88L, "profesional@mail.com", "Laura", "Gomez", "PSIQUIATRA", "Psiquiatra",
                true, 1, "CUIT", "27334445556", "Laura Gomez", 6,
                "Av. Santa Fe 1234", "27334445556", 27334445556L, "33444555", 33444555);

        when(interno.suscriptores(eq(0), anyInt())).thenReturn(List.of(suscriptorInicial));

        int cargados = copias.copiarSuscriptores();
        assertThat(cargados).isEqualTo(1);

        Suscriptores.Suscriptor guardado = suscriptores.porId(88L).orElseThrow();
        assertThat(guardado.nombre()).isEqualTo("Laura");
        assertThat(guardado.cuit()).isEqualTo("27334445556");
        assertThat(guardado.version()).isEqualTo(1);

        // 1. Evento identidad.usuario.actualizado refresca desde el monolito pidiendo /internal/monolito/suscriptores/{id}
        ClienteInterno.SuscriptorMonolito suscriptorModificado = new ClienteInterno.SuscriptorMonolito(
                88L, "profesional@mail.com", "Laura Beatriz", "Gomez", "PSIQUIATRA", "Psiquiatra",
                true, 2, "CUIT", "27334445556", "Laura Beatriz Gomez", 6,
                "Av. Santa Fe 4321", "27334445556", 27334445556L, "33444555", 33444555);
        when(interno.suscriptor(88L)).thenReturn(suscriptorModificado);

        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<UsuarioActualizadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("identidad.usuario.actualizado")
                                .producer("monolito").aggregateType("usuario").aggregateId("88").aggregateVersion(2)
                                .data(new UsuarioActualizadoData(88L, "profesional@mail.com", "Laura Beatriz", "Gomez", "PSIQUIATRA")).build()))
                        .setContentType("application/json").build());

        esperar(() -> "Laura Beatriz".equals(suscriptores.porId(88L).map(Suscriptores.Suscriptor::nombre).orElse(null)));
        Suscriptores.Suscriptor actualizado = suscriptores.porId(88L).orElseThrow();
        assertThat(actualizado.fiscalAddress()).isEqualTo("Av. Santa Fe 4321");
        assertThat(actualizado.version()).isEqualTo(2);

        // 2. Respeto de versión: un dato con versión menor (1) no debe sobreescribir la versión 2
        suscriptores.guardar(suscriptorInicial.aSuscriptor());
        Suscriptores.Suscriptor retenido = suscriptores.porId(88L).orElseThrow();
        assertThat(retenido.version()).isEqualTo(2);
        assertThat(retenido.nombre()).isEqualTo("Laura Beatriz");

        // 3. Evento identidad.usuario.eliminado borra al suscriptor
        rabbit.send(RabbitMQConstantes.EXCHANGE_EVENTOS, Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1,
                MessageBuilder.withBody(mapper.writeValueAsBytes(EventEnvelope.<UsuarioEliminadoData>builder()
                                .eventId(UUID.randomUUID()).eventType("identidad.usuario.eliminado")
                                .producer("monolito").aggregateType("usuario").aggregateId("88").aggregateVersion(3)
                                .data(new UsuarioEliminadoData(88L, "Baja")).build()))
                        .setContentType("application/json").build());

        esperar(() -> suscriptores.porId(88L).isEmpty());
    }
}
