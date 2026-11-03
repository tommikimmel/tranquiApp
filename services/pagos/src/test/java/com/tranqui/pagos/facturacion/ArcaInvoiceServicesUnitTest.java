package com.tranqui.pagos.facturacion;

import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArcaInvoiceServicesUnitTest {

    @Mock
    private Facturas facturas;

    @Mock
    private SecuenciasFactura secuenciasFactura;

    @Mock
    private PagosSuscripcion pagosSuscripcion;

    @Mock
    private Suscripciones suscripciones;

    @Mock
    private Planes planes;

    @Mock
    private Suscriptores suscriptores;

    @Mock
    private ArcaWsfeService arcaWsfeService;

    @Mock
    private ArcaInvoicePdfService pdfService;

    @Mock
    private ArcaConfig arcaConfig;

    @InjectMocks
    private InvoiceService invoiceService;

    private Suscriptores.Suscriptor suscriptor;
    private Planes.Plan plan;
    private Suscripciones.Suscripcion sub;
    private PagosSuscripcion.PagoSuscripcion payment;
    private Facturas.Factura invoice;

    @BeforeEach
    void setUp() {
        suscriptor = new Suscriptores.Suscriptor(
                1L,
                "carlos@example.com",
                "Dr. Carlos",
                "Gomez",
                "PSIQUIATRA",
                "Psiquiatra",
                true,
                1,
                "CUIT",
                "20334455669",
                "Dr. Carlos Gomez",
                6,
                "Av. Corrientes 1234, CABA",
                "20-33445566-9",
                20334455669L,
                "33445566",
                33445566,
                LocalDateTime.now()
        );

        plan = new Planes.Plan(
                10L,
                "consultorio",
                "Consultorio",
                "Plan para consultorios",
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                new BigDecimal("150000.00"),
                "monthly",
                1,
                "MP-PLAN-1",
                true,
                true,
                LocalDateTime.now().minusMonths(1),
                null
        );

        sub = new Suscripciones.Suscripcion(
                100L,
                1,
                1L,
                10L,
                "ACTIVE",
                1,
                "MERCADO_PAGO",
                "preapproval-123",
                new BigDecimal("15000.00"),
                "monthly",
                LocalDateTime.now().plusMonths(1),
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1),
                null,
                false,
                null,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        payment = new PagosSuscripcion.PagoSuscripcion(
                50L,
                100L,
                1L,
                "mp-pay-123",
                "idemp-key-1",
                "MERCADO_PAGO",
                "APPROVED",
                new BigDecimal("15000.00"),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1),
                null,
                null,
                "REF-123",
                "Pago mensual",
                LocalDateTime.now()
        );

        invoice = new Facturas.Factura(
                200L,
                50L,
                11,
                1,
                1001L,
                "74123456789012",
                LocalDate.now().plusDays(10),
                80,
                20334455669L,
                "Dr. Carlos Gomez",
                6,
                new BigDecimal("15000.00"),
                "PES",
                BigDecimal.ONE,
                LocalDate.now(),
                "/api/subscriptions/invoices/200/pdf",
                "BASE64_PDF",
                "ISSUED",
                1,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    // --- ArcaInvoicePdfService tests ---

    @Test
    void testArcaInvoicePdfService_renderFacturaC_and_NotaCreditoC() {
        ArcaConfig config = new ArcaConfig();
        config.setCuitEmisor(20384910294L);
        config.setRazonSocial("Tranqui App S.A.S.");
        config.setDomicilioFiscal("Av. Santa Fe 1234, CABA");
        config.setCondicionIvaEmisor("Responsable Monotributo");
        config.setInicioActividades("01/01/2024");
        ArcaInvoicePdfService realPdfService = new ArcaInvoicePdfService(config);

        // Factura C
        byte[] pdfFactura = realPdfService.renderInvoicePdf(invoice, plan, "Período 01/10/2026 al 31/10/2026");
        assertNotNull(pdfFactura);
        assertTrue(pdfFactura.length > 500);

        // Nota de Crédito C
        Facturas.Factura ncInvoice = new Facturas.Factura(
                201L, 50L, 13, 1, 1002L, "74123456789013", LocalDate.now().plusDays(10),
                80, 20334455669L, "Dr. Carlos Gomez", 6, new BigDecimal("15000.00"),
                "PES", BigDecimal.ONE, LocalDate.now(), null, null, "ISSUED", 1, null,
                null, null, 200L, LocalDateTime.now(), LocalDateTime.now()
        );
        byte[] pdfNc = realPdfService.renderInvoicePdf(ncInvoice, plan, "Anulación administrativa");
        assertNotNull(pdfNc);
        assertTrue(pdfNc.length > 500);
    }

    // --- ArcaWsfeService & ArcaWsaaService tests ---

    @Test
    void testArcaWsfeService_mockMode() {
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ArcaWsaaService wsaa = new ArcaWsaaService(config);
        ArcaWsfeService wsfe = new ArcaWsfeService(config, wsaa);

        assertEquals(0L, wsfe.consultarUltimoComprobanteAutorizado(1, 11));

        ArcaWsfeService.SolicitudCaeRequest req = ArcaWsfeService.SolicitudCaeRequest.builder()
                .puntoVenta(1)
                .cbteTipo(11)
                .cbteNumero(500L)
                .importeTotal(new BigDecimal("10000.00"))
                .fechaEmision(LocalDate.now())
                .build();

        ArcaWsfeService.ArcaCaeResponse resp = wsfe.solicitarCae(req);
        assertNotNull(resp);
        assertTrue(resp.isAprobado());
        assertNotNull(resp.getCae());
        assertNotNull(resp.getCaeFchVto());
    }

    @Test
    void testArcaWsfeService_xmlParsingAndSoapGeneration() throws Exception {
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(true);
        config.setCuitEmisor(20384910294L);
        ArcaWsaaService wsaa = new ArcaWsaaService(config);
        ArcaWsfeService wsfe = new ArcaWsfeService(config, wsaa);

        // 1. parseUltimoAutorizadoResponse
        String xmlUltimo = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><FECompUltimoAutorizadoResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECompUltimoAutorizadoResult><CbteNro>1042</CbteNro></FECompUltimoAutorizadoResult></FECompUltimoAutorizadoResponse></soap:Body></soap:Envelope>";
        Long ultimo = ReflectionTestUtils.invokeMethod(wsfe, "parseUltimoAutorizadoResponse", xmlUltimo);
        assertEquals(1042L, ultimo);

        String xmlEmpty = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><EmptyResult/></soap:Body></soap:Envelope>";
        Long empty = ReflectionTestUtils.invokeMethod(wsfe, "parseUltimoAutorizadoResponse", xmlEmpty);
        assertEquals(0L, empty);

        // 2. parseCaeSolicitarResponse - Aprobado
        String xmlAprobado = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult><Resultado>A</Resultado><FeDetResp><FECAEDetResponse><CAE>74123456789012</CAE><CAEFchVto>20261115</CAEFchVto></FECAEDetResponse></FeDetResp></FECAESolicitarResult></FECAESolicitarResponse></soap:Body></soap:Envelope>";
        ArcaWsfeService.ArcaCaeResponse respAprobado = ReflectionTestUtils.invokeMethod(wsfe, "parseCaeSolicitarResponse", xmlAprobado, 501L);
        assertNotNull(respAprobado);
        assertTrue(respAprobado.isAprobado());
        assertEquals("74123456789012", respAprobado.getCae());
        assertEquals(LocalDate.of(2026, 11, 15), respAprobado.getCaeFchVto());
        assertEquals(501L, respAprobado.getCbteNumero());

        // 3. parseCaeSolicitarResponse - Rechazado con errores y observaciones
        String xmlRechazado = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult><Resultado>R</Resultado><Errors><Err><Code>10001</Code><Msg>CUIT invalido</Msg></Err></Errors><Events><Obs><Code>20002</Code><Msg>Fecha limite cercana</Msg></Obs></Events></FECAESolicitarResult></FECAESolicitarResponse></soap:Body></soap:Envelope>";
        ArcaWsfeService.ArcaCaeResponse respRechazado = ReflectionTestUtils.invokeMethod(wsfe, "parseCaeSolicitarResponse", xmlRechazado, 502L);
        assertNotNull(respRechazado);
        assertFalse(respRechazado.isAprobado());
        assertEquals(1, respRechazado.getErrores().size());
        assertTrue(respRechazado.getErrores().get(0).contains("CUIT invalido"));
        assertEquals(1, respRechazado.getObservaciones().size());
        assertTrue(respRechazado.getObservaciones().get(0).contains("Fecha limite"));

        // 4. buildCaeSolicitarSoapRequest - Factura C
        ArcaTicketAcceso ta = ArcaTicketAcceso.builder().token("TOK").sign("SIG").build();
        ArcaWsfeService.SolicitudCaeRequest reqFactura = ArcaWsfeService.SolicitudCaeRequest.builder()
                .puntoVenta(1)
                .cbteTipo(11)
                .cbteNumero(101L)
                .docTipo(80)
                .docNro(20334455669L)
                .importeTotal(new BigDecimal("15000.00"))
                .fechaEmision(LocalDate.now())
                .condicionIvaReceptorId(6)
                .build();
        String soapFactura = ReflectionTestUtils.invokeMethod(wsfe, "buildCaeSolicitarSoapRequest", ta, reqFactura);
        assertNotNull(soapFactura);
        assertTrue(soapFactura.contains("<ar:CbteTipo>11</ar:CbteTipo>"));
        assertTrue(soapFactura.contains("<ar:DocNro>20334455669</ar:DocNro>"));

        // 5. buildCaeSolicitarSoapRequest - Nota de Crédito C con comprobante asociado
        ArcaWsfeService.SolicitudCaeRequest reqNc = ArcaWsfeService.SolicitudCaeRequest.builder()
                .puntoVenta(1)
                .cbteTipo(13)
                .cbteNumero(12L)
                .docTipo(80)
                .docNro(20334455669L)
                .importeTotal(new BigDecimal("15000.00"))
                .fechaEmision(LocalDate.now())
                .comprobanteAsociadoNumero(101L)
                .comprobanteAsociadoTipo(11)
                .comprobanteAsociadoPtoVta(1)
                .build();
        String soapNc = ReflectionTestUtils.invokeMethod(wsfe, "buildCaeSolicitarSoapRequest", ta, reqNc);
        assertNotNull(soapNc);
        assertTrue(soapNc.contains("<ar:CbteTipo>13</ar:CbteTipo>"));
        assertTrue(soapNc.contains("<ar:CbtesAsoc>"));
        assertTrue(soapNc.contains("<ar:Nro>101</ar:Nro>"));

        // 6. Exception handling when wsaa fails
        ArcaWsaaService failingWsaa = mock(ArcaWsaaService.class);
        when(failingWsaa.getTicketAcceso()).thenThrow(new RuntimeException("WSAA fail"));
        ArcaWsfeService failingWsfe = new ArcaWsfeService(config, failingWsaa);
        assertThrows(RuntimeException.class, () -> failingWsfe.consultarUltimoComprobanteAutorizado(1, 11));
        assertThrows(RuntimeException.class, () -> failingWsfe.solicitarCae(reqFactura));
    }

    @Test
    void testArcaWsaaService_mockMode() {
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ArcaWsaaService wsaa = new ArcaWsaaService(config);

        ArcaTicketAcceso ticket = wsaa.getTicketAcceso();
        assertNotNull(ticket);
        assertTrue(ticket.isValid());
        assertNotNull(ticket.getToken());
        assertNotNull(ticket.getSign());

        // Repeated call should return cached ticket
        ArcaTicketAcceso cached = wsaa.getTicketAcceso();
        assertSame(ticket, cached);
    }

    @Test
    void testArcaWsaaService_parsingAndXmlGeneration() throws Exception {
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ArcaWsaaService wsaa = new ArcaWsaaService(config);

        // 1. buildLoginTicketRequestXml
        String ltrXml = ReflectionTestUtils.invokeMethod(wsaa, "buildLoginTicketRequestXml");
        assertNotNull(ltrXml);
        assertTrue(ltrXml.contains("<service>wsfe</service>"));
        assertTrue(ltrXml.contains("<loginTicketRequest"));

        // 2. parseLoginTicketResponse - Valid SOAP
        String soapResp = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><loginCmsResponse xmlns=\"http://wsaa.view.sua.dvad.infra.afip.gov.ar\"><loginCmsReturn>&lt;loginTicketResponse version=\"1.0\"&gt;&lt;header&gt;&lt;expirationTime&gt;2026-10-10T12:00:00-03:00&lt;/expirationTime&gt;&lt;/header&gt;&lt;credentials&gt;&lt;token&gt;TOKEN_123&lt;/token&gt;&lt;sign&gt;SIGN_456&lt;/sign&gt;&lt;/credentials&gt;&lt;/loginTicketResponse&gt;</loginCmsReturn></loginCmsResponse></soap:Body></soap:Envelope>";
        ArcaTicketAcceso parsed = ReflectionTestUtils.invokeMethod(wsaa, "parseLoginTicketResponse", soapResp);
        assertNotNull(parsed);
        assertEquals("TOKEN_123", parsed.getToken());
        assertEquals("SIGN_456", parsed.getSign());

        // 3. parseLoginTicketResponse - Invalid XML
        String invalidSoap = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><fault>Error</fault></soap:Body></soap:Envelope>";
        assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(wsaa, "parseLoginTicketResponse", invalidSoap));

        // 4. loadCertificate and loadPrivateKey when unconfigured
        assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(wsaa, "loadCertificate"));
        assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(wsaa, "loadPrivateKey"));
    }

    // --- InvoiceService tests ---

    @Test
    void testInvoiceService_generateInvoiceForPayment_existingOrCourtesyOrDisabled() {
        // 1. Existing invoice
        PagosSuscripcion.PagoSuscripcion conFactura = new PagosSuscripcion.PagoSuscripcion(
                50L, 100L, 1L, "mp-1", "idemp-1", "MERCADO_PAGO", "APPROVED",
                new BigDecimal("15000.00"), LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1), 200L, null, "REF-1", "Notas", LocalDateTime.now()
        );
        when(pagosSuscripcion.porId(50L)).thenReturn(Optional.of(conFactura));
        when(facturas.porId(200L)).thenReturn(Optional.of(invoice));

        Facturas.Factura resExistente = invoiceService.generateInvoiceForPayment(50L);
        assertNotNull(resExistente);
        assertEquals(200L, resExistente.id());

        // 2. Courtesy payment
        PagosSuscripcion.PagoSuscripcion cortesia = new PagosSuscripcion.PagoSuscripcion(
                51L, 100L, 1L, null, null, "COURTESY", "APPROVED",
                BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1), null, null, "REF-C", "Cortesía", LocalDateTime.now()
        );
        when(pagosSuscripcion.porId(51L)).thenReturn(Optional.of(cortesia));
        assertNull(invoiceService.generateInvoiceForPayment(51L));

        // 3. ARCA disabled: returns null, sequence is NOT touched
        when(pagosSuscripcion.porId(52L)).thenReturn(Optional.of(payment));
        when(arcaConfig.isEnabled()).thenReturn(false);
        assertNull(invoiceService.generateInvoiceForPayment(52L));
        verifyNoInteractions(secuenciasFactura);
    }

    @Test
    void testInvoiceService_generateInvoiceForPayment_createsNewInvoice_success() {
        when(pagosSuscripcion.porId(50L)).thenReturn(Optional.of(payment));
        when(arcaConfig.isEnabled()).thenReturn(true);
        when(arcaConfig.getPuntoVenta()).thenReturn(1);
        when(suscriptores.porId(1L)).thenReturn(Optional.of(suscriptor));
        when(suscripciones.porId(100L)).thenReturn(Optional.of(sub));
        when(planes.porId(10L)).thenReturn(Optional.of(plan));

        when(secuenciasFactura.proximoNumero(1, 11)).thenReturn(1001L);
        when(facturas.insertarNuevo(any())).thenReturn(300L);

        when(arcaWsfeService.solicitarCae(any())).thenReturn(
                ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("A")
                        .cae("74123456789012")
                        .caeFchVto(LocalDate.now().plusDays(10))
                        .rawRequestXml("<req/>")
                        .rawResponseXml("<resp/>")
                        .build()
        );
        when(pdfService.renderInvoicePdf(any(), any(), any())).thenReturn("DUMMY_PDF".getBytes());

        Facturas.Factura emitida = new Facturas.Factura(
                300L, 50L, 11, 1, 1001L, "74123456789012", LocalDate.now().plusDays(10),
                80, 20334455669L, "Dr. Carlos Gomez", 6, new BigDecimal("15000.00"),
                "PES", BigDecimal.ONE, LocalDate.now(), "/api/subscriptions/invoices/300/pdf",
                "BASE64", "ISSUED", 1, null, "<req/>", "<resp/>", null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(facturas.porId(300L)).thenReturn(Optional.of(emitida));

        Facturas.Factura res = invoiceService.generateInvoiceForPayment(50L);
        assertNotNull(res);
        assertEquals(300L, res.id());
        assertEquals("ISSUED", res.status());
        assertEquals("74123456789012", res.cae());

        verify(secuenciasFactura).proximoNumero(1, 11);
        verify(facturas).actualizarResultadoCae(eq(300L), eq("74123456789012"), any(), eq(1001L),
                eq("/api/subscriptions/invoices/300/pdf"), anyString(), eq("ISSUED"), isNull(),
                eq("<req/>"), eq("<resp/>"), any(), eq(1));
        verify(pagosSuscripcion).actualizarFacturaId(50L, 300L);
    }

    @Test
    void testInvoiceService_generateInvoiceForPayment_caeFailed_consumesNumber() {
        // Decisión 4: un CAE fallido consume el número de la secuencia y deja la factura en FAILED
        when(pagosSuscripcion.porId(50L)).thenReturn(Optional.of(payment));
        when(arcaConfig.isEnabled()).thenReturn(true);
        when(arcaConfig.getPuntoVenta()).thenReturn(1);
        when(suscriptores.porId(1L)).thenReturn(Optional.of(suscriptor));
        when(suscripciones.porId(100L)).thenReturn(Optional.of(sub));
        when(planes.porId(10L)).thenReturn(Optional.of(plan));

        when(secuenciasFactura.proximoNumero(1, 11)).thenReturn(1002L);
        when(facturas.insertarNuevo(any())).thenReturn(301L);

        when(arcaWsfeService.solicitarCae(any())).thenReturn(
                ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("R")
                        .errores(List.of("10001: Error en AFIP"))
                        .rawRequestXml("<req/>")
                        .rawResponseXml("<resp_err/>")
                        .build()
        );

        Facturas.Factura fallida = new Facturas.Factura(
                301L, 50L, 11, 1, 1002L, null, null,
                80, 20334455669L, "Dr. Carlos Gomez", 6, new BigDecimal("15000.00"),
                "PES", BigDecimal.ONE, LocalDate.now(), null,
                null, "FAILED", 1, "10001: Error en AFIP", "<req/>", "<resp_err/>", null,
                LocalDateTime.now(), null
        );
        when(facturas.porId(301L)).thenReturn(Optional.of(fallida));

        Facturas.Factura res = invoiceService.generateInvoiceForPayment(50L);
        assertNotNull(res);
        assertEquals("FAILED", res.status());
        assertEquals("10001: Error en AFIP", res.lastError());

        verify(secuenciasFactura).proximoNumero(1, 11);
        verify(facturas).actualizarResultadoCae(eq(301L), isNull(), isNull(), eq(1002L),
                isNull(), isNull(), eq("FAILED"), eq("10001: Error en AFIP"),
                eq("<req/>"), eq("<resp_err/>"), isNull(), eq(1));
        verify(pagosSuscripcion, never()).actualizarFacturaId(anyLong(), anyLong());
    }

    @Test
    void testInvoiceService_emitirNotaDeCredito_rechazaSiArcaNoEstaHabilitado() {
        when(arcaConfig.isEnabled()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> invoiceService.emitirNotaDeCredito(200L, "motivo", 1L));
        verifyNoInteractions(arcaWsfeService);
        verifyNoInteractions(secuenciasFactura);
    }

    @Test
    void testInvoiceService_emitirNotaDeCredito_successAndValidation() {
        when(arcaConfig.isEnabled()).thenReturn(true);
        when(facturas.porId(200L)).thenReturn(Optional.of(invoice));

        when(secuenciasFactura.proximoNumero(1, 13)).thenReturn(50L);
        when(facturas.insertarNuevo(any())).thenReturn(400L);

        when(arcaWsfeService.solicitarCae(any())).thenReturn(
                ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("A")
                        .cae("74999999999999")
                        .caeFchVto(LocalDate.now().plusDays(10))
                        .rawRequestXml("<req_nc/>")
                        .rawResponseXml("<resp_nc/>")
                        .build()
        );
        when(pdfService.renderInvoicePdf(any(), isNull(), anyString())).thenReturn("PDF_NC".getBytes());

        Facturas.Factura nc = new Facturas.Factura(
                400L, 50L, 13, 1, 50L, "74999999999999", LocalDate.now().plusDays(10),
                80, 20334455669L, "Dr. Carlos Gomez", 6, new BigDecimal("15000.00"),
                "PES", BigDecimal.ONE, LocalDate.now(), "/api/subscriptions/invoices/400/pdf",
                "BASE64", "ISSUED", 1, null, "<req_nc/>", "<resp_nc/>", 200L,
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(facturas.porId(400L)).thenReturn(Optional.of(nc));

        Facturas.Factura resultadoNc = invoiceService.emitirNotaDeCredito(200L, "Cancelación voluntaria", 1L);
        assertNotNull(resultadoNc);
        assertEquals(13, resultadoNc.cbteTipo());
        assertEquals(200L, resultadoNc.comprobanteAsociadoId());
        assertEquals("ISSUED", resultadoNc.status());

        verify(facturas).actualizarEstado(200L, "VOIDED");
        verify(facturas).actualizarResultadoCae(eq(400L), eq("74999999999999"), any(), eq(50L),
                eq("/api/subscriptions/invoices/400/pdf"), anyString(), eq("ISSUED"), isNull(),
                eq("<req_nc/>"), eq("<resp_nc/>"), any(), eq(1));

        // Validaciones: no ISSUED
        Facturas.Factura invoiceFallida = new Facturas.Factura(
                202L, 50L, 11, 1, 1002L, null, null, 80, 20334455669L, "Dr", 6,
                BigDecimal.TEN, "PES", BigDecimal.ONE, LocalDate.now(), null, null,
                "FAILED", 1, "Error", null, null, null, LocalDateTime.now(), null
        );
        when(facturas.porId(202L)).thenReturn(Optional.of(invoiceFallida));
        assertThrows(IllegalStateException.class, () -> invoiceService.emitirNotaDeCredito(202L, "Error", 1L));

        // Validaciones: no tipo 11
        Facturas.Factura invoiceTipo13 = new Facturas.Factura(
                203L, 50L, 13, 1, 51L, "CAE", LocalDate.now(), 80, 20334455669L, "Dr", 6,
                BigDecimal.TEN, "PES", BigDecimal.ONE, LocalDate.now(), null, null,
                "ISSUED", 1, null, null, null, 200L, LocalDateTime.now(), LocalDateTime.now()
        );
        when(facturas.porId(203L)).thenReturn(Optional.of(invoiceTipo13));
        assertThrows(IllegalStateException.class, () -> invoiceService.emitirNotaDeCredito(203L, "Error", 1L));
    }

    @Test
    void testInvoiceService_queries() {
        when(facturas.todasOrdenadas()).thenReturn(List.of(invoice));
        assertEquals(1, invoiceService.getAllInvoices().size());

        when(facturas.porId(200L)).thenReturn(Optional.of(invoice));
        assertTrue(invoiceService.getInvoiceById(200L).isPresent());

        when(pagosSuscripcion.porProfesionalId(1L)).thenReturn(List.of(payment));
        when(facturas.porPagoIds(List.of(50L))).thenReturn(List.of(invoice));
        assertEquals(1, invoiceService.getInvoicesForProfessional(1L).size());
    }

    @Test
    void testFacturacionSuscripcionesArca_reconciliacion() {
        InvoiceService mockInvoiceService = mock(InvoiceService.class);
        FacturacionSuscripcionesArca f = new FacturacionSuscripcionesArca(mockInvoiceService, pagosSuscripcion, facturas);

        // 1. emitirFactura
        f.emitirFactura(50L, "test");
        verify(mockInvoiceService).generateInvoiceForPayment(50L);

        // 2. reconciliarPagosSinFactura
        when(pagosSuscripcion.aprobadosSinFacturaAnterioresA(any())).thenReturn(List.of(payment));
        f.reconciliarPagosSinFactura();
        verify(mockInvoiceService, times(2)).generateInvoiceForPayment(50L);

        // 3. verificarFacturasFallidas
        when(facturas.fallidas()).thenReturn(List.of(invoice));
        assertDoesNotThrow(f::verificarFacturasFallidas);
    }
}
