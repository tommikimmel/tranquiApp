package com.tranqui.app.service.arca;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.InvoiceRepository;
import com.tranqui.app.repository.InvoiceSequenceRepository;
import com.tranqui.app.repository.SubscriptionPaymentRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.InvoiceService;
import com.tranqui.app.service.ResendEmailService;
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
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceSequenceRepository sequenceRepository;

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ArcaWsfeService arcaWsfeService;

    @Mock
    private ArcaInvoicePdfService pdfService;

    @Mock
    private ArcaConfig arcaConfig;

    @Mock
    private ResendEmailService resendEmailService;

    @InjectMocks
    private InvoiceService invoiceService;

    private Usuario profesional;
    private Plan plan;
    private Subscription sub;
    private SubscriptionPayment payment;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        profesional = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .apellido("Gomez")
                .email("carlos@example.com")
                .cuit("20-33445566-9")
                .taxId("20334455669")
                .taxIdType("CUIT")
                .ivaConditionId(6) // Monotributo
                .fiscalAddress("Av. Corrientes 1234, CABA")
                .build();

        plan = Plan.builder()
                .id(10L)
                .name("Consultorio")
                .code("consultorio")
                .build();

        sub = Subscription.builder()
                .id(100L)
                .professional(profesional)
                .plan(plan)
                .build();

        payment = SubscriptionPayment.builder()
                .id(50L)
                .subscriptionId(100L)
                .professionalId(1L)
                .amountArs(new BigDecimal("15000.00"))
                .method("MERCADO_PAGO")
                .status("APPROVED")
                .build();

        invoice = Invoice.builder()
                .id(200L)
                .paymentId(50L)
                .cbteTipo(11)
                .puntoVenta(1)
                .cbteNumero(1001L)
                .cae("74123456789012")
                .caeVencimiento(LocalDate.now().plusDays(10))
                .status(InvoiceStatus.ISSUED)
                .importeTotal(new BigDecimal("15000.00"))
                .fechaEmision(LocalDate.now())
                .receptorNombre("Dr. Carlos Gomez")
                .receptorDocTipo(80)
                .receptorDocNro(20334455669L)
                .receptorCondicionIva(6)
                .build();
    }

    // --- ArcaInvoicePdfService tests ---

    @Test
    void testArcaInvoicePdfService_renderFacturaC_and_NotaCreditoC() {
        ArcaInvoicePdfService realPdfService = new ArcaInvoicePdfService();
        ArcaConfig config = new ArcaConfig();
        config.setCuitEmisor(20384910294L);
        config.setRazonSocial("Tranqui App S.A.S.");
        config.setDomicilioFiscal("Av. Santa Fe 1234, CABA");
        config.setCondicionIvaEmisor("Responsable Monotributo");
        config.setInicioActividades("01/01/2024");
        ReflectionTestUtils.setField(realPdfService, "arcaConfig", config);

        // Factura C
        byte[] pdfFactura = realPdfService.renderInvoicePdf(invoice, plan, "Período 01/10/2026 al 31/10/2026");
        assertNotNull(pdfFactura);
        assertTrue(pdfFactura.length > 500);

        // Nota de Crédito C
        invoice.setCbteTipo(13);
        byte[] pdfNc = realPdfService.renderInvoicePdf(invoice, plan, "Anulación administrativa");
        assertNotNull(pdfNc);
        assertTrue(pdfNc.length > 500);
    }

    // --- ArcaWsfeService & ArcaWsaaService tests ---

    @Test
    void testArcaWsfeService_mockMode() {
        ArcaWsfeService wsfe = new ArcaWsfeService();
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ReflectionTestUtils.setField(wsfe, "arcaConfig", config);

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
        ArcaWsfeService wsfe = new ArcaWsfeService();
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(true);
        config.setCuitEmisor(20384910294L);
        ReflectionTestUtils.setField(wsfe, "arcaConfig", config);

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

        // 6. Exception handling when connecting to dummy URL
        assertThrows(RuntimeException.class, () -> wsfe.consultarUltimoComprobanteAutorizado(1, 11));
        assertThrows(RuntimeException.class, () -> wsfe.solicitarCae(reqFactura));
    }

    @Test
    void testArcaWsaaService_mockMode() {
        ArcaWsaaService wsaa = new ArcaWsaaService();
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ReflectionTestUtils.setField(wsaa, "arcaConfig", config);

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
        ArcaWsaaService wsaa = new ArcaWsaaService();
        ArcaConfig config = new ArcaConfig();
        config.setEnabled(false);
        ReflectionTestUtils.setField(wsaa, "arcaConfig", config);

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
    void testInvoiceService_generateInvoiceForPayment_existingOrCourtesy() {
        payment.setInvoiceId(200L);
        when(paymentRepository.findById(50L)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findById(200L)).thenReturn(Optional.of(invoice));

        Invoice result = invoiceService.generateInvoiceForPayment(50L);
        assertEquals(200L, result.getId());

        // Courtesy payment
        payment.setInvoiceId(null);
        payment.setMethod("COURTESY");
        assertNull(invoiceService.generateInvoiceForPayment(50L));
    }

    @Test
    void testInvoiceService_generateInvoiceForPayment_createsNewInvoice() {
        payment.setInvoiceId(null);
        payment.setMethod("MERCADO_PAGO");
        when(paymentRepository.findById(50L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(arcaConfig.isEnabled()).thenReturn(true);
        when(arcaConfig.getPuntoVenta()).thenReturn(1);

        InvoiceSequence seq = InvoiceSequence.builder().puntoVenta(1).cbteTipo(11).lastNumber(100L).build();
        when(sequenceRepository.findByPuntoVentaAndCbteTipoForUpdate(1, 11)).thenReturn(Optional.of(seq));
        when(arcaWsfeService.solicitarCae(any())).thenReturn(
                ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("A")
                        .cae("74123456789012")
                        .caeFchVto(LocalDate.now().plusDays(10))
                        .build()
        );
        when(pdfService.renderInvoicePdf(any(), any(), any())).thenReturn("PDF".getBytes());
        when(invoiceRepository.save(any())).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(300L);
            return inv;
        });

        Invoice created = invoiceService.generateInvoiceForPayment(50L);
        assertNotNull(created);
        assertEquals(InvoiceStatus.ISSUED, created.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void testInvoiceService_emitirNotaDeCredito_successAndValidation() {
        when(invoiceRepository.findById(200L)).thenReturn(Optional.of(invoice));

        InvoiceSequence seq = InvoiceSequence.builder().puntoVenta(1).cbteTipo(13).lastNumber(10L).build();
        when(sequenceRepository.findByPuntoVentaAndCbteTipoForUpdate(1, 13)).thenReturn(Optional.of(seq));
        when(arcaWsfeService.solicitarCae(any())).thenReturn(
                ArcaWsfeService.ArcaCaeResponse.builder()
                        .resultado("A")
                        .cae("74123456789012")
                        .caeFchVto(LocalDate.now().plusDays(10))
                        .build()
        );
        when(pdfService.renderInvoicePdf(any(), any(), any())).thenReturn("PDF".getBytes());
        when(invoiceRepository.save(any())).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(400L);
            return inv;
        });

        Invoice nc = invoiceService.emitirNotaDeCredito(200L, "Cancelación", 1L);
        assertNotNull(nc);
        assertEquals(13, nc.getCbteTipo());

        // Validate not ISSUED
        invoice.setStatus(InvoiceStatus.FAILED);
        assertThrows(IllegalStateException.class, () -> invoiceService.emitirNotaDeCredito(200L, "Error", 1L));

        // Validate not type 11
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setCbteTipo(13);
        assertThrows(IllegalStateException.class, () -> invoiceService.emitirNotaDeCredito(200L, "Error", 1L));
    }

    @Test
    void testInvoiceService_queries() {
        when(invoiceRepository.findAllOrderByCreatedAtDesc()).thenReturn(List.of(invoice));
        assertEquals(1, invoiceService.getAllInvoices().size());

        when(invoiceRepository.findById(200L)).thenReturn(Optional.of(invoice));
        assertTrue(invoiceService.getInvoiceById(200L).isPresent());

        when(paymentRepository.findByProfessionalIdOrderByPaidAtDesc(1L)).thenReturn(List.of(payment));
        when(invoiceRepository.findByPaymentIdIn(List.of(50L))).thenReturn(List.of(invoice));
        assertEquals(1, invoiceService.getInvoicesForProfessional(1L).size());
    }
}
