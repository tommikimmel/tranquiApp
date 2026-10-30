package com.tranqui.pagos.facturacion;

import java.time.LocalDateTime;

public class ArcaTicketAcceso {
    private String token;
    private String sign;
    private LocalDateTime generationTime;
    private LocalDateTime expirationTime;

    public ArcaTicketAcceso() {}

    public ArcaTicketAcceso(String token, String sign, LocalDateTime generationTime, LocalDateTime expirationTime) {
        this.token = token;
        this.sign = sign;
        this.generationTime = generationTime;
        this.expirationTime = expirationTime;
    }

    public boolean isValid() {
        if (token == null || sign == null || expirationTime == null) {
            return false;
        }
        return LocalDateTime.now().plusMinutes(30).isBefore(expirationTime);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public LocalDateTime getGenerationTime() {
        return generationTime;
    }

    public void setGenerationTime(LocalDateTime generationTime) {
        this.generationTime = generationTime;
    }

    public LocalDateTime getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(LocalDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String token;
        private String sign;
        private LocalDateTime generationTime;
        private LocalDateTime expirationTime;

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder sign(String sign) {
            this.sign = sign;
            return this;
        }

        public Builder generationTime(LocalDateTime generationTime) {
            this.generationTime = generationTime;
            return this;
        }

        public Builder expirationTime(LocalDateTime expirationTime) {
            this.expirationTime = expirationTime;
            return this;
        }

        public ArcaTicketAcceso build() {
            return new ArcaTicketAcceso(token, sign, generationTime, expirationTime);
        }
    }
}
