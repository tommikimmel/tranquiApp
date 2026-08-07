package com.tranqui.app.service;

/**
 * Thrown by {@link Qbi2RecipeClientHttp} when QBI2 Recipe answers an HTTP call with a
 * >=400 status. Carries the raw status/body (unlike a generic RuntimeException) so
 * callers can pull out QBI2's own error code + mensaje — e.g. "QBI235: EL CAMPO MEDICO
 * IDREFEPS NO CUMPLE EL RANGO MÍNIMO O MÁXIMO DE CARACTERES" — instead of a dead-end
 * "no la aceptó" that hides exactly what QBI2 didn't like about the request.
 */
public class Qbi2RecipeException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public Qbi2RecipeException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
