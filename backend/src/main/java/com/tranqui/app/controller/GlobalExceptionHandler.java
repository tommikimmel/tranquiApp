package com.tranqui.app.controller;

import com.tranqui.app.service.RecetaElectronicaException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the business exceptions services throw (e.g. TurnoService rejecting a duplicate
 * booking) to a clean 4xx response carrying the original Spanish message as plain text —
 * matching what frontend/src/api/api.ts reads via response.text() on a non-ok response.
 * Without this, Spring's default error handling turns them into an opaque 500 with the
 * message stripped (server.error.include-message defaults to "never"), which is why a
 * rejected booking used to surface to the patient as an unexplained failure.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    // 422 (not 5xx) so apiFetch's sanitizeErrorMessage passes the real, professional-facing
    // message through instead of masking it behind a generic "server error" string.
    @ExceptionHandler(RecetaElectronicaException.class)
    public ResponseEntity<String> handleRecetaElectronica(RecetaElectronicaException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }
}
