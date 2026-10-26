package com.tranqui.turnos.web;

import com.tranqui.turnos.turnos.NoEncontradoException;
import com.tranqui.turnos.turnos.ServicioNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Las mismas respuestas que {@code GlobalExceptionHandler} del monolito: el mensaje en texto plano. */
@RestControllerAdvice
public class Errores {

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<String> noEncontrado(NoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalido(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflicto(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<String> noDisponible(ServicioNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }
}
