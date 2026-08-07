package com.tranqui.app.service;

/**
 * Thrown when QBI2/Innovamed fails to generate an official electronic prescription.
 * The message carries a professional-facing explanation — including QBI2's own
 * validation mensaje when available (e.g. "QBI235: EL CAMPO MEDICO IDREFEPS NO CUMPLE
 * EL RANGO..."), since that's the one piece of info that tells the médico what to fix —
 * but never the raw HTTP status/stack trace, which is logged separately. See
 * GlobalExceptionHandler.
 */
public class RecetaElectronicaException extends RuntimeException {
    public RecetaElectronicaException(String message, Throwable cause) {
        super(message, cause);
    }
}
