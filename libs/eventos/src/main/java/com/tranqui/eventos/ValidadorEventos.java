package com.tranqui.eventos;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Valida el data de un evento contra las reglas de su record (Fase 10): nunca se confía en lo que manda otro servicio.
 * Lo usan el consumidor (antes de llamar al manejador) y la outbox (antes de publicar), así un dato inválido falla en
 * origen y, si igual llega, no se escribe en ninguna copia.
 *
 * <p>La descripción de los problemas nombra solo el campo y la regla, nunca el valor: los eventos llevan mails y
 * datos personales que no tienen que terminar en los logs.
 */
public final class ValidadorEventos {

    /** {@code rechazar} (por defecto) o {@code loguear} (la primera semana en producción: avisa y deja pasar). */
    public enum Modo {
        RECHAZAR, LOGUEAR;

        public static Modo de(String valor) {
            if (valor == null || valor.isBlank() || valor.trim().equalsIgnoreCase("rechazar")) {
                return RECHAZAR;
            }
            if (valor.trim().equalsIgnoreCase("loguear")) {
                return LOGUEAR;
            }
            throw new IllegalStateException("eventos.validacion.modo tiene que ser 'rechazar' o 'loguear', no '" + valor + "'.");
        }
    }

    // Sin EL para interpolar los mensajes: no hace falta y evita sumar otra dependencia.
    private static final Validator VALIDADOR;
    static {
        ValidatorFactory fabrica = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        VALIDADOR = fabrica.getValidator();
    }

    private ValidadorEventos() {
    }

    /** Los problemas del data ("campo: Regla", ordenados), o vacío si cumple. Un data nulo es un problema. */
    public static Optional<String> problemas(Object data) {
        if (data == null) {
            return Optional.of("el evento no trae data");
        }
        var violaciones = VALIDADOR.validate(data);
        if (violaciones.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(violaciones.stream()
                .map(ValidadorEventos::describir)
                .sorted()
                .collect(Collectors.joining(", ")));
    }

    private static String describir(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ": " + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    }
}
