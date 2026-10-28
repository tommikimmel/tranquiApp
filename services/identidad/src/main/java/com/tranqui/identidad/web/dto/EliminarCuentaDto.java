package com.tranqui.identidad.web.dto;

public class EliminarCuentaDto {
    private String password;

    public EliminarCuentaDto() {}

    public EliminarCuentaDto(String password) {
        this.password = password;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
