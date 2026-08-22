package com.tranqui.app.service.arca;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class ArcaWsfeService {

    private static final Logger log = LoggerFactory.getLogger(ArcaWsfeService.class);
    private static final DateTimeFormatter AFIP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ArcaConfig arcaConfig;

    @Autowired
    private ArcaWsaaService wsaaService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SolicitudCaeRequest {
        private Integer puntoVenta;
        private Integer cbteTipo; // 11 = Factura C, 13 = Nota de Crédito C
        private Long cbteNumero;
        private Integer docTipo; // 80 = CUIT, 96 = DNI, 99 = Consumidor Final
        private Long docNro;
        private BigDecimal importeTotal;
        private LocalDate fechaEmision;
        private LocalDate fechaServDesde;
        private LocalDate fechaServHasta;
        private LocalDate fechaVtoPago;
        private Integer condicionIvaReceptorId; // RG 5616/2024 (1=RI, 4=Exento, 5=CF, 6=Monotributo)
        private Long comprobanteAsociadoNumero; // Solo para Nota de Crédito
        private Integer comprobanteAsociadoTipo;
        private Integer comprobanteAsociadoPtoVta;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArcaCaeResponse {
        private String resultado; // "A" = Aprobado, "R" = Rechazado
        private String cae;
        private LocalDate caeFchVto;
        private Long cbteNumero;
        @Builder.Default
        private List<String> errores = new ArrayList<>();
        @Builder.Default
        private List<String> observaciones = new ArrayList<>();
        private String rawRequestXml;
        private String rawResponseXml;

        public boolean isAprobado() {
            return "A".equalsIgnoreCase(resultado);
        }
    }

    /**
     * Consulta el último número de comprobante autorizado en ARCA para el punto de venta y tipo.
     */
    public long consultarUltimoComprobanteAutorizado(int puntoVenta, int cbteTipo) {
        if (!arcaConfig.isEnabled()) {
            log.info("ARCA WSFE running in mock mode: returning simulated last authorized number");
            return 0L;
        }

        try {
            ArcaTicketAcceso ta = wsaaService.getTicketAcceso();
            String soapRequest =
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\">\n" +
                    "  <soapenv:Header/>\n" +
                    "  <soapenv:Body>\n" +
                    "    <ar:FECompUltimoAutorizado>\n" +
                    "      <ar:Auth>\n" +
                    "        <ar:Token>" + ta.getToken() + "</ar:Token>\n" +
                    "        <ar:Sign>" + ta.getSign() + "</ar:Sign>\n" +
                    "        <ar:Cuit>" + arcaConfig.getCuitEmisor() + "</ar:Cuit>\n" +
                    "      </ar:Auth>\n" +
                    "      <ar:PtoVta>" + puntoVenta + "</ar:PtoVta>\n" +
                    "      <ar:CbteTipo>" + cbteTipo + "</ar:CbteTipo>\n" +
                    "    </ar:FECompUltimoAutorizado>\n" +
                    "  </soapenv:Body>\n" +
                    "</soapenv:Envelope>";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(arcaConfig.getWsfeUrl()))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("WSFEv1 FECompUltimoAutorizado HTTP " + response.statusCode() + ": " + response.body());
            }

            return parseUltimoAutorizadoResponse(response.body());
        } catch (Exception e) {
            log.error("Error consultando FECompUltimoAutorizado: {}", e.getMessage(), e);
            throw new RuntimeException("Error consultando último comprobante en ARCA: " + e.getMessage(), e);
        }
    }

    /**
     * Solicita autorización de CAE ante ARCA WSFEv1 (FECAESolicitar).
     */
    public ArcaCaeResponse solicitarCae(SolicitudCaeRequest req) {
        if (!arcaConfig.isEnabled()) {
            log.info("ARCA WSFE running in mock mode: simulating CAE approval for CbteTipo={}, Nro={}",
                    req.getCbteTipo(), req.getCbteNumero());
            return generateMockCaeResponse(req);
        }

        try {
            ArcaTicketAcceso ta = wsaaService.getTicketAcceso();
            String soapRequest = buildCaeSolicitarSoapRequest(ta, req);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(arcaConfig.getWsfeUrl()))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"http://ar.gov.afip.dif.FEV1/FECAESolicitar\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("WSFEv1 FECAESolicitar HTTP " + response.statusCode() + ": " + response.body());
            }

            ArcaCaeResponse caeResp = parseCaeSolicitarResponse(response.body(), req.getCbteNumero());
            caeResp.setRawRequestXml(soapRequest);
            caeResp.setRawResponseXml(response.body());
            return caeResp;
        } catch (Exception e) {
            log.error("Error solicitando CAE en ARCA WSFEv1: {}", e.getMessage(), e);
            throw new RuntimeException("Error solicitando CAE ante ARCA: " + e.getMessage(), e);
        }
    }

    private ArcaCaeResponse generateMockCaeResponse(SolicitudCaeRequest req) {
        Random random = new Random();
        StringBuilder caeBuilder = new StringBuilder("74");
        for (int i = 0; i < 12; i++) {
            caeBuilder.append(random.nextInt(10));
        }
        String cae = caeBuilder.toString();
        LocalDate vto = (req.getFechaEmision() != null ? req.getFechaEmision() : LocalDate.now()).plusDays(10);

        return ArcaCaeResponse.builder()
                .resultado("A")
                .cae(cae)
                .caeFchVto(vto)
                .cbteNumero(req.getCbteNumero())
                .rawRequestXml("<mock>Simulated WSFEv1 Request</mock>")
                .rawResponseXml("<mock>Simulated WSFEv1 Response - CAE: " + cae + "</mock>")
                .build();
    }

    private String buildCaeSolicitarSoapRequest(ArcaTicketAcceso ta, SolicitudCaeRequest req) {
        LocalDate fecha = req.getFechaEmision() != null ? req.getFechaEmision() : LocalDate.now();
        LocalDate fchServDesde = req.getFechaServDesde() != null ? req.getFechaServDesde() : fecha;
        LocalDate fchServHasta = req.getFechaServHasta() != null ? req.getFechaServHasta() : fecha.plusMonths(1);
        LocalDate fchVtoPago = req.getFechaVtoPago() != null ? req.getFechaVtoPago() : fecha.plusDays(10);

        String cbteFchStr = fecha.format(AFIP_DATE_FORMATTER);
        String fchServDesdeStr = fchServDesde.format(AFIP_DATE_FORMATTER);
        String fchServHastaStr = fchServHasta.format(AFIP_DATE_FORMATTER);
        String fchVtoPagoStr = fchVtoPago.format(AFIP_DATE_FORMATTER);

        int condicionIva = req.getCondicionIvaReceptorId() != null ? req.getCondicionIvaReceptorId() : 6;

        String cbtesAsocXml = "";
        if (req.getCbteTipo() == 13 && req.getComprobanteAsociadoNumero() != null) {
            int asocTipo = req.getComprobanteAsociadoTipo() != null ? req.getComprobanteAsociadoTipo() : 11;
            int asocPto = req.getComprobanteAsociadoPtoVta() != null ? req.getComprobanteAsociadoPtoVta() : req.getPuntoVenta();
            cbtesAsocXml =
                    "            <ar:CbtesAsoc>\n" +
                    "              <ar:CbteAsoc>\n" +
                    "                <ar:Tipo>" + asocTipo + "</ar:Tipo>\n" +
                    "                <ar:PtoVta>" + asocPto + "</ar:PtoVta>\n" +
                    "                <ar:Nro>" + req.getComprobanteAsociadoNumero() + "</ar:Nro>\n" +
                    "              </ar:CbteAsoc>\n" +
                    "            </ar:CbtesAsoc>\n";
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\">\n" +
                "  <soapenv:Header/>\n" +
                "  <soapenv:Body>\n" +
                "    <ar:FECAESolicitar>\n" +
                "      <ar:Auth>\n" +
                "        <ar:Token>" + ta.getToken() + "</ar:Token>\n" +
                "        <ar:Sign>" + ta.getSign() + "</ar:Sign>\n" +
                "        <ar:Cuit>" + arcaConfig.getCuitEmisor() + "</ar:Cuit>\n" +
                "      </ar:Auth>\n" +
                "      <ar:FeCAEReq>\n" +
                "        <ar:FeCabReq>\n" +
                "          <ar:CantReg>1</ar:CantReg>\n" +
                "          <ar:PtoVta>" + req.getPuntoVenta() + "</ar:PtoVta>\n" +
                "          <ar:CbteTipo>" + req.getCbteTipo() + "</ar:CbteTipo>\n" +
                "        </ar:FeCabReq>\n" +
                "        <ar:FeDetReq>\n" +
                "          <ar:FECAEDetRequest>\n" +
                "            <ar:Concepto>2</ar:Concepto>\n" + // 2 = Servicios
                "            <ar:DocTipo>" + req.getDocTipo() + "</ar:DocTipo>\n" +
                "            <ar:DocNro>" + req.getDocNro() + "</ar:DocNro>\n" +
                "            <ar:CbteDesde>" + req.getCbteNumero() + "</ar:CbteDesde>\n" +
                "            <ar:CbteHasta>" + req.getCbteNumero() + "</ar:CbteHasta>\n" +
                "            <ar:CbteFch>" + cbteFchStr + "</ar:CbteFch>\n" +
                "            <ar:ImpTotal>" + req.getImporteTotal().toPlainString() + "</ar:ImpTotal>\n" +
                "            <ar:ImpTotConc>0</ar:ImpTotConc>\n" +
                "            <ar:ImpNeto>" + req.getImporteTotal().toPlainString() + "</ar:ImpNeto>\n" +
                "            <ar:ImpOpEx>0</ar:ImpOpEx>\n" +
                "            <ar:ImpTrib>0</ar:ImpTrib>\n" +
                "            <ar:ImpIVA>0</ar:ImpIVA>\n" +
                "            <ar:FchServDesde>" + fchServDesdeStr + "</ar:FchServDesde>\n" +
                "            <ar:FchServHasta>" + fchServHastaStr + "</ar:FchServHasta>\n" +
                "            <ar:FchVtoPago>" + fchVtoPagoStr + "</ar:FchVtoPago>\n" +
                "            <ar:MonId>PES</ar:MonId>\n" +
                "            <ar:MonCotiz>1</ar:MonCotiz>\n" +
                "            <ar:CondicionIVAReceptorId>" + condicionIva + "</ar:CondicionIVAReceptorId>\n" +
                cbtesAsocXml +
                "          </ar:FECAEDetRequest>\n" +
                "        </ar:FeDetReq>\n" +
                "      </ar:FeCAEReq>\n" +
                "    </ar:FECAESolicitar>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";
    }

    private long parseUltimoAutorizadoResponse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList nroNodes = doc.getElementsByTagName("CbteNro");
        if (nroNodes.getLength() > 0) {
            String val = nroNodes.item(0).getTextContent();
            return Long.parseLong(val.trim());
        }
        return 0L;
    }

    private ArcaCaeResponse parseCaeSolicitarResponse(String xml, Long requestedNumero) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        ArcaCaeResponse resp = new ArcaCaeResponse();
        resp.setCbteNumero(requestedNumero);

        NodeList resNodes = doc.getElementsByTagName("Resultado");
        if (resNodes.getLength() > 0) {
            resp.setResultado(resNodes.item(0).getTextContent().trim());
        } else {
            resp.setResultado("R");
        }

        NodeList caeNodes = doc.getElementsByTagName("CAE");
        if (caeNodes.getLength() > 0) {
            resp.setCae(caeNodes.item(0).getTextContent().trim());
        }

        NodeList caeVtoNodes = doc.getElementsByTagName("CAEFchVto");
        if (caeVtoNodes.getLength() > 0) {
            String vtoStr = caeVtoNodes.item(0).getTextContent().trim();
            try {
                resp.setCaeFchVto(LocalDate.parse(vtoStr, AFIP_DATE_FORMATTER));
            } catch (Exception ignored) {
            }
        }

        NodeList errNodes = doc.getElementsByTagName("Err");
        for (int i = 0; i < errNodes.getLength(); i++) {
            Element errEl = (Element) errNodes.item(i);
            String code = errEl.getElementsByTagName("Code").getLength() > 0 ? errEl.getElementsByTagName("Code").item(0).getTextContent() : "";
            String msg = errEl.getElementsByTagName("Msg").getLength() > 0 ? errEl.getElementsByTagName("Msg").item(0).getTextContent() : "";
            resp.getErrores().add(code + ": " + msg);
        }

        NodeList obsNodes = doc.getElementsByTagName("Obs");
        for (int i = 0; i < obsNodes.getLength(); i++) {
            Element obsEl = (Element) obsNodes.item(i);
            String code = obsEl.getElementsByTagName("Code").getLength() > 0 ? obsEl.getElementsByTagName("Code").item(0).getTextContent() : "";
            String msg = obsEl.getElementsByTagName("Msg").getLength() > 0 ? obsEl.getElementsByTagName("Msg").item(0).getTextContent() : "";
            resp.getObservaciones().add(code + ": " + msg);
        }

        return resp;
    }
}
