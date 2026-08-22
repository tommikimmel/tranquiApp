package com.tranqui.app.service.arca;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ArcaWsaaService {

    private static final Logger log = LoggerFactory.getLogger(ArcaWsaaService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Autowired
    private ArcaConfig arcaConfig;

    private volatile ArcaTicketAcceso cachedTicket;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong uniqueIdGenerator = new AtomicLong(System.currentTimeMillis() / 1000);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Gets cached Ticket de Acceso (Token + Sign) or obtains a new one via WSAA LoginCms
     */
    public ArcaTicketAcceso getTicketAcceso() {
        if (cachedTicket != null && cachedTicket.isValid()) {
            return cachedTicket;
        }

        lock.lock();
        try {
            // Double check inside lock
            if (cachedTicket != null && cachedTicket.isValid()) {
                return cachedTicket;
            }

            if (!arcaConfig.isEnabled() || !hasValidCerts()) {
                log.info("ARCA WSAA running in simulation/mock mode (enabled={}, hasCerts={})",
                        arcaConfig.isEnabled(), hasValidCerts());
                cachedTicket = generateMockTicket();
                return cachedTicket;
            }

            log.info("Requesting new WSAA Ticket de Acceso from {}", arcaConfig.getWsaaUrl());
            cachedTicket = requestRealTicket();
            return cachedTicket;
        } catch (Exception e) {
            log.error("Error authenticating against ARCA WSAA: {}", e.getMessage(), e);
            if (!arcaConfig.isEnabled()) {
                log.warn("Falling back to mock Ticket de Acceso due to WSAA exception");
                cachedTicket = generateMockTicket();
                return cachedTicket;
            }
            throw new RuntimeException("Error en autenticación ARCA (WSAA): " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private boolean hasValidCerts() {
        return (arcaConfig.getCertPath() != null && !arcaConfig.getCertPath().isBlank())
                || (arcaConfig.getCertContent() != null && !arcaConfig.getCertContent().isBlank());
    }

    private ArcaTicketAcceso generateMockTicket() {
        LocalDateTime now = LocalDateTime.now();
        return ArcaTicketAcceso.builder()
                .token("mock-token-" + System.currentTimeMillis())
                .sign("mock-sign-" + System.currentTimeMillis())
                .generationTime(now)
                .expirationTime(now.plusHours(12))
                .build();
    }

    private ArcaTicketAcceso requestRealTicket() throws Exception {
        X509Certificate cert = loadCertificate();
        PrivateKey key = loadPrivateKey();

        String ltrXml = buildLoginTicketRequestXml();
        String cmsSignedBase64 = signLoginTicketRequest(ltrXml, cert, key);

        String soapRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:wsaa=\"http://wsaa.view.sua.dvad.infra.afip.gov.ar\">\n" +
                "  <soapenv:Header/>\n" +
                "  <soapenv:Body>\n" +
                "    <wsaa:loginCms>\n" +
                "      <wsaa:in0>" + cmsSignedBase64 + "</wsaa:in0>\n" +
                "    </wsaa:loginCms>\n" +
                "  </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(arcaConfig.getWsaaUrl()))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("WSAA HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseLoginTicketResponse(response.body());
    }

    private String buildLoginTicketRequestXml() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(-3));
        OffsetDateTime genTime = now.minusMinutes(10);
        OffsetDateTime expTime = now.plusHours(12);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

        long uniqueId = uniqueIdGenerator.incrementAndGet();

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<loginTicketRequest version=\"1.0\">\n" +
                "  <header>\n" +
                "    <uniqueId>" + uniqueId + "</uniqueId>\n" +
                "    <generationTime>" + genTime.format(fmt) + "</generationTime>\n" +
                "    <expirationTime>" + expTime.format(fmt) + "</expirationTime>\n" +
                "  </header>\n" +
                "  <service>wsfe</service>\n" +
                "</loginTicketRequest>";
    }

    private String signLoginTicketRequest(String xml, X509Certificate cert, PrivateKey key) throws Exception {
        CMSTypedData data = new CMSProcessableByteArray(xml.getBytes(StandardCharsets.UTF_8));
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

        ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(key);

        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                .build(sha1Signer, cert));

        gen.addCertificates(new JcaCertStore(Collections.singletonList(new X509CertificateHolder(cert.getEncoded()))));

        CMSSignedData signedData = gen.generate(data, true);
        return Base64.getEncoder().encodeToString(signedData.getEncoded());
    }

    private ArcaTicketAcceso parseLoginTicketResponse(String soapResponseBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document soapDoc = builder.parse(new ByteArrayInputStream(soapResponseBody.getBytes(StandardCharsets.UTF_8)));

        NodeList loginCmsReturnList = soapDoc.getElementsByTagName("loginCmsReturn");
        if (loginCmsReturnList.getLength() == 0) {
            throw new RuntimeException("Respuesta WSAA inválida: no contiene elemento loginCmsReturn");
        }

        String innerXml = loginCmsReturnList.item(0).getTextContent();
        Document innerDoc = builder.parse(new ByteArrayInputStream(innerXml.getBytes(StandardCharsets.UTF_8)));

        String token = innerDoc.getElementsByTagName("token").item(0).getTextContent();
        String sign = innerDoc.getElementsByTagName("sign").item(0).getTextContent();
        String expirationTimeStr = innerDoc.getElementsByTagName("expirationTime").item(0).getTextContent();

        LocalDateTime expirationTime;
        try {
            expirationTime = OffsetDateTime.parse(expirationTimeStr).toLocalDateTime();
        } catch (Exception e) {
            expirationTime = LocalDateTime.now().plusHours(11);
        }

        return ArcaTicketAcceso.builder()
                .token(token)
                .sign(sign)
                .generationTime(LocalDateTime.now())
                .expirationTime(expirationTime)
                .build();
    }

    private X509Certificate loadCertificate() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        if (arcaConfig.getCertContent() != null && !arcaConfig.getCertContent().isBlank()) {
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(arcaConfig.getCertContent().getBytes(StandardCharsets.UTF_8)));
        }
        if (arcaConfig.getCertPath() != null) {
            try (InputStream is = Files.newInputStream(Paths.get(arcaConfig.getCertPath()))) {
                return (X509Certificate) cf.generateCertificate(is);
            }
        }
        throw new IllegalStateException("Certificado ARCA no configurado");
    }

    private PrivateKey loadPrivateKey() throws Exception {
        Reader reader;
        if (arcaConfig.getKeyContent() != null && !arcaConfig.getKeyContent().isBlank()) {
            reader = new StringReader(arcaConfig.getKeyContent());
        } else if (arcaConfig.getKeyPath() != null) {
            reader = new FileReader(arcaConfig.getKeyPath());
        } else {
            throw new IllegalStateException("Clave privada ARCA no configurada");
        }

        try (PEMParser pemParser = new PEMParser(reader)) {
            Object obj = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (obj instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) obj).getPrivateKeyInfo());
            } else if (obj instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) obj);
            }
            throw new IllegalStateException("Formato de clave privada PEM no reconocido");
        }
    }
}
