package com.tranqui.profesionales.recetas;

/**
 * Excepción lanzada cuando QBI2 Recipe responde con un código HTTP de error (>= 400).
 * Contiene el código de estado y el cuerpo de la respuesta para que el llamador pueda
 * extraer detalles específicos de validación de QBI2 si están disponibles.
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
