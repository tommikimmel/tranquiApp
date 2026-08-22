package com.tranqui.app.service.arca;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "arca")
@Getter
@Setter
public class ArcaConfig {
    private boolean enabled = false;
    private String environment = "homologacion"; // homologacion | produccion
    private Long cuitEmisor = 20384910294L; // Default fallback / dev CUIT
    private Integer puntoVenta = 1; // Default WS point of sale
    private String certPath;
    private String certContent;
    private String keyPath;
    private String keyContent;
    private String razonSocial = "TRANQUI SALUD";
    private String domicilioFiscal = "Av. Corrientes 1234, CABA";
    private String condicionIvaEmisor = "Responsable Monotributo";
    private String inicioActividades = "01/08/2026";

    public String getWsaaUrl() {
        if ("produccion".equalsIgnoreCase(environment)) {
            return "https://wsaa.afip.gov.ar/ws/services/LoginCms";
        }
        return "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";
    }

    public String getWsfeUrl() {
        if ("produccion".equalsIgnoreCase(environment)) {
            return "https://servicios1.afip.gov.ar/wsfev1/service.asmx";
        }
        return "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";
    }
}
