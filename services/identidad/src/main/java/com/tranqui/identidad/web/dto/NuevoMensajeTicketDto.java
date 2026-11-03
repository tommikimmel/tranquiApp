package com.tranqui.identidad.web.dto;

public class NuevoMensajeTicketDto {
    private String contenido;

    public NuevoMensajeTicketDto() {}

    public NuevoMensajeTicketDto(String contenido) {
        this.contenido = contenido;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }
}
