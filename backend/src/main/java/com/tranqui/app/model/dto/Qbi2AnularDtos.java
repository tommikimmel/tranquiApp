package com.tranqui.app.model.dto;

import lombok.*;

/**
 * Request/response shapes for QBI2 Recipe's DELETE /apirecipe/Receta/{hash}
 * ("Anular Receta"). See Qbi2RecetaDtos for the source of the field spec and
 * its caveats.
 */
public class Qbi2AnularDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnularRequest {
        private Integer clienteAppId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    // Success example: { codigoRespuesta: 0, mensaje: "Receta anulada correctamente" }.
    // Known error codes (surfaced as HTTP 400 with a different error shape, not this one):
    // QBI79 = "LA RECETA HA VENCIDO EL DIA" (expired, cannot cancel),
    // QBI2 = "QBI2 OPERACION INVALIDA" (already cancelled or lookup failed),
    // QBI106 = "ERROR AL GENERAR EL TOKEN".
    public static class FarmalinkResult {
        private Integer codigoRespuesta;
        private String mensaje;
    }
}
