package com.tranqui.identidad.web.dto;

public class PreferenciasNotificacionDto {
    private boolean emailHabilitado;

    public PreferenciasNotificacionDto() {}

    public PreferenciasNotificacionDto(boolean emailHabilitado) {
        this.emailHabilitado = emailHabilitado;
    }

    public boolean isEmailHabilitado() { return emailHabilitado; }
    public void setEmailHabilitado(boolean emailHabilitado) { this.emailHabilitado = emailHabilitado; }
}
