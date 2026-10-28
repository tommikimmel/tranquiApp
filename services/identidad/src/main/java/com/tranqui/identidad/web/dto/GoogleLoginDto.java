package com.tranqui.identidad.web.dto;

public class GoogleLoginDto {
    private String idToken;

    public GoogleLoginDto() {}

    public GoogleLoginDto(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
