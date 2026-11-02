package com.tranqui.comun.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Formato común de error JSON para la plataforma Tranqui App.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    String mensaje,
    int status,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime timestamp,
    String correlationId,
    Map<String, Object> detalles
) {
    public static ErrorResponse of(String error, String mensaje, int status, String correlationId) {
        return new ErrorResponse(error, mensaje, status, OffsetDateTime.now(), correlationId, null);
    }

    public static ErrorResponse of(String error, String mensaje, int status, String correlationId, Map<String, Object> detalles) {
        return new ErrorResponse(error, mensaje, status, OffsetDateTime.now(), correlationId, detalles);
    }
}
