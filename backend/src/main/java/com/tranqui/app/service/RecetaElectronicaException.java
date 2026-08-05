package com.tranqui.app.service;

/**
 * Thrown when QBI2/Innovamed fails to generate an official electronic prescription.
 * The message carries only a professional-facing explanation (never the raw QBI2
 * status/body, which is logged separately) — see GlobalExceptionHandler.
 */
public class RecetaElectronicaException extends RuntimeException {
    public RecetaElectronicaException(String message, Throwable cause) {
        super(message, cause);
    }
}
