package com.tranqui.pagos.facturacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "arca")
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Long getCuitEmisor() {
        return cuitEmisor;
    }

    public void setCuitEmisor(Long cuitEmisor) {
        this.cuitEmisor = cuitEmisor;
    }

    public Integer getPuntoVenta() {
        return puntoVenta;
    }

    public void setPuntoVenta(Integer puntoVenta) {
        this.puntoVenta = puntoVenta;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getCertContent() {
        return certContent;
    }

    public void setCertContent(String certContent) {
        this.certContent = certContent;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getKeyContent() {
        return keyContent;
    }

    public void setKeyContent(String keyContent) {
        this.keyContent = keyContent;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getDomicilioFiscal() {
        return domicilioFiscal;
    }

    public void setDomicilioFiscal(String domicilioFiscal) {
        this.domicilioFiscal = domicilioFiscal;
    }

    public String getCondicionIvaEmisor() {
        return condicionIvaEmisor;
    }

    public void setCondicionIvaEmisor(String condicionIvaEmisor) {
        this.condicionIvaEmisor = condicionIvaEmisor;
    }

    public String getInicioActividades() {
        return inicioActividades;
    }

    public void setInicioActividades(String inicioActividades) {
        this.inicioActividades = inicioActividades;
    }

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
