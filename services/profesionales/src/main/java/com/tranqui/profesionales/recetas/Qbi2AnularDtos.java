package com.tranqui.profesionales.recetas;

/**
 * Formas de request y response para DELETE /apirecipe/Receta/{hash} de QBI2 Recipe ("Anular Receta").
 */
public class Qbi2AnularDtos {

    public static class AnularRequest {
        private Integer clienteAppId;

        public AnularRequest() {}
        public AnularRequest(Integer clienteAppId) { this.clienteAppId = clienteAppId; }

        public Integer getClienteAppId() { return clienteAppId; }
        public void setClienteAppId(Integer clienteAppId) { this.clienteAppId = clienteAppId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer clienteAppId;
            public Builder clienteAppId(Integer v) { this.clienteAppId = v; return this; }
            public AnularRequest build() { return new AnularRequest(clienteAppId); }
        }
    }

    public static class FarmalinkResult {
        private Integer codigoRespuesta;
        private String mensaje;

        public FarmalinkResult() {}
        public FarmalinkResult(Integer codigoRespuesta, String mensaje) {
            this.codigoRespuesta = codigoRespuesta;
            this.mensaje = mensaje;
        }

        public Integer getCodigoRespuesta() { return codigoRespuesta; }
        public void setCodigoRespuesta(Integer codigoRespuesta) { this.codigoRespuesta = codigoRespuesta; }
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer codigoRespuesta;
            private String mensaje;
            public Builder codigoRespuesta(Integer v) { this.codigoRespuesta = v; return this; }
            public Builder mensaje(String v) { this.mensaje = v; return this; }
            public FarmalinkResult build() { return new FarmalinkResult(codigoRespuesta, mensaje); }
        }
    }
}
