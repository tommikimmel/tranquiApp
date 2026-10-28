package com.tranqui.identidad.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeo uniforme de excepciones de negocio a respuestas HTTP con texto plano,
 * exactamente igual a GlobalExceptionHandler del monolito.
 */
@RestControllerAdvice
public class Errores {

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<String> noEncontrado(NoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> solicitudInvalida(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflicto(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
