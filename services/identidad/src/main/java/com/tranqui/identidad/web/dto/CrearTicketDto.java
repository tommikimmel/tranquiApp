package com.tranqui.identidad.web.dto;

public class CrearTicketDto {
    private String asunto;
    private String mensaje;

    public CrearTicketDto() {}

    public CrearTicketDto(String asunto, String mensaje) {
        this.asunto = asunto;
        this.mensaje = mensaje;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
