package com.tranqui.profesionales.recetas;

/**
 * Excepción lanzada cuando ocurre un error al emitir o validar una receta electrónica (QBI2 / Innovamed).
 * Mapeada a 422 Unprocessable Entity en {@code Errores} con el mensaje en texto plano para el profesional.
 */
public class RecetaElectronicaException extends RuntimeException {

    public RecetaElectronicaException(String message, Throwable cause) {
        super(message, cause);
    }
}
