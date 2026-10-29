package com.tranqui.eventos;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(ValidadorEventos.class);

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

    /**
     * Valida la respuesta de una ruta interna de otro servicio (Fase 10, V2): nunca se confía en lo que manda. En una
     * lista, las filas que no cumplen se saltean con un aviso (la resincronización sigue con las demás); una respuesta
     * suelta que no cumple es una falla, igual que si el otro servicio no hubiera respondido.
     */
    @SuppressWarnings("unchecked")
    public static <T> T respuesta(T respuesta, String origen) {
        if (respuesta instanceof List<?> lista) {
            List<Object> validas = new ArrayList<>(lista.size());
            for (int i = 0; i < lista.size(); i++) {
                Object fila = lista.get(i);
                Optional<String> problemas = problemas(fila);
                if (problemas.isPresent()) {
                    log.warn("Respuesta de {}: se saltea la fila {} ({})", origen, i, problemas.get());
                } else {
                    validas.add(fila);
                }
            }
            return (T) validas;
        }
        if (respuesta != null) {
            problemas(respuesta).ifPresent(p -> {
                throw new IllegalStateException("Respuesta inválida de " + origen + ": " + p);
            });
        }
        return respuesta;
    }

    private static String describir(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ": " + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    }
}
