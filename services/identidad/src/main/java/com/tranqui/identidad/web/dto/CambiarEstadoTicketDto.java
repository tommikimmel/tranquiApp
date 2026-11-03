package com.tranqui.identidad.web.dto;

public class CambiarEstadoTicketDto {
    private String estado;

    public CambiarEstadoTicketDto() {}

    public CambiarEstadoTicketDto(String estado) {
        this.estado = estado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
