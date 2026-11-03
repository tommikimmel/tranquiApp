package com.tranqui.eventos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 10, V1: cada record de {@code data} con un valor válido pasa, y cada regla rota se detecta nombrando el campo
 * (y nunca el valor). Recorre todos los records por reflexión: un record nuevo queda cubierto sin escribir un test.
 */
class ValidacionDatosTest {

    private static final String PAQUETE = "com.tranqui.eventos.data";

    @Test
    void cadaRecordValidoPasaYCadaReglaRotaSeDetecta() throws Exception {
        List<Class<?>> records = records();
        assertThat(records).hasSizeGreaterThanOrEqualTo(44);
        int reglasProbadas = 0;
        for (Class<?> tipo : records) {
            Object valido = instancia(tipo, -1, null);
            assertThat(ValidadorEventos.problemas(valido)).as(tipo.getSimpleName() + " válido").isEmpty();

            RecordComponent[] componentes = tipo.getRecordComponents();
            for (int i = 0; i < componentes.length; i++) {
                Object invalido = valorInvalido(componentes[i]);
                if (invalido == SIN_REGLA) {
                    continue;
                }
                Object roto = instancia(tipo, i, invalido);
                String nombre = componentes[i].getName();
                assertThat(ValidadorEventos.problemas(roto)).as(tipo.getSimpleName() + "." + nombre)
                        .hasValueSatisfying(p -> assertThat(p).contains(nombre + ":"));
                if (invalido instanceof String texto && !texto.isEmpty()) {
                    assertThat(ValidadorEventos.problemas(roto).orElseThrow()).doesNotContain(texto);
                }
                reglasProbadas++;
            }
        }
        assertThat(reglasProbadas).isGreaterThanOrEqualTo(120);
    }

    @Test
    void sinDataEsUnProblema() {
        assertThat(ValidadorEventos.problemas(null)).isPresent();
    }

    @Test
    void elModoSeLeeDeLaPropiedad() {
        assertThat(ValidadorEventos.Modo.de(null)).isEqualTo(ValidadorEventos.Modo.RECHAZAR);
        assertThat(ValidadorEventos.Modo.de(" Loguear ")).isEqualTo(ValidadorEventos.Modo.LOGUEAR);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ValidadorEventos.Modo.de("ignorar"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ armado por reflexión

    private static final Object SIN_REGLA = new Object();

    private static List<Class<?>> records() throws IOException, ClassNotFoundException {
        Path dir = Path.of("src/main/java", PAQUETE.replace('.', '/'));
        List<Class<?>> lista = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(dir)) {
            for (Path p : archivos.filter(f -> f.toString().endsWith("Data.java")).toList()) {
                Class<?> c = Class.forName(PAQUETE + "." + p.getFileName().toString().replace(".java", ""));
                lista.add(c);
                Arrays.stream(c.getDeclaredClasses()).filter(Class::isRecord).forEach(lista::add);
            }
        }
        return lista;
    }

    /** Una instancia válida del record, con el componente {@code indice} reemplazado por {@code valor}. */
    private static Object instancia(Class<?> tipo, int indice, Object valor) throws Exception {
        RecordComponent[] componentes = tipo.getRecordComponents();
        Class<?>[] tipos = Arrays.stream(componentes).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] args = new Object[componentes.length];
        for (int i = 0; i < componentes.length; i++) {
            args[i] = i == indice ? valor : valorValido(componentes[i]);
        }
        Constructor<?> canonico = tipo.getDeclaredConstructor(tipos);
        canonico.setAccessible(true);
        return canonico.newInstance(args);
    }

    private static Object valorValido(RecordComponent c) throws Exception {
        Class<?> t = c.getType();
        Pattern patron = anotacion(c, Pattern.class);
        if (t == String.class) {
            if (patron != null) return patron.regexp().split("\\|")[0];
            if ((anotacion(c, Email.class) != null)) return "ana@mail.com";
            return "texto";
        }
        if (t == Long.class || t == long.class) return 1L;
        if (t == Integer.class || t == int.class) return 1;
        if (t == Double.class || t == double.class) return 1.0;
        if (t == Boolean.class || t == boolean.class) return false;
        if (t == BigDecimal.class) return BigDecimal.ONE;
        if (t == LocalDate.class) return LocalDate.of(2026, 11, 3);
        if (t == LocalDateTime.class) return LocalDateTime.of(2026, 11, 3, 10, 0);
        if (t == LocalTime.class) return LocalTime.of(10, 0);
        if (t == OffsetDateTime.class) return OffsetDateTime.parse("2026-11-03T10:00:00-03:00");
        if (t == List.class) return List.of();
        if (t.isRecord()) return instancia(t, -1, null);
        throw new IllegalStateException("Falta un valor válido para " + t + " en " + c);
    }

    // Las restricciones de Jakarta no apuntan a RECORD_COMPONENT: llegan al campo privado, que es lo que se valida.
    private static <A extends java.lang.annotation.Annotation> A anotacion(RecordComponent c, Class<A> tipo) {
        try {
            return c.getDeclaringRecord().getDeclaredField(c.getName()).getAnnotation(tipo);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object valorInvalido(RecordComponent c) {
        Class<?> t = c.getType();
        if ((anotacion(c, Pattern.class) != null)) return "VALOR_INVENTADO";
        if ((anotacion(c, Email.class) != null)) return "no-es-un-mail";
        if ((anotacion(c, Positive.class) != null)) return t == Long.class ? -1L : -1;
        if ((anotacion(c, PositiveOrZero.class) != null)) return new BigDecimal("-1");
        if ((anotacion(c, NotBlank.class) != null)) return "";
        if ((anotacion(c, NotNull.class) != null)) return null;
        return SIN_REGLA;
    }
}
