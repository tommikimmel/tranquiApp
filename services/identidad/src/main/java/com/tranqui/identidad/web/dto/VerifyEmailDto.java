package com.tranqui.identidad.web.dto;

public class VerifyEmailDto {
    private String email;
    private String codigo;

    public VerifyEmailDto() {}

    public VerifyEmailDto(String email, String codigo) {
        this.email = email;
        this.codigo = codigo;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
