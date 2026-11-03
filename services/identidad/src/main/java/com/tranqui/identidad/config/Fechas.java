package com.tranqui.identidad.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Las fechas como las maneja el monolito ({@code DateConfig}), para que las rutas respondan el mismo JSON:
 * se escriben {@code dd/MM/yyyy} y se aceptan en varios formatos (el frontend manda los dos). Las rutas
 * internas mandan ISO con un serializador propio en el campo.
 */
public final class Fechas {

    private static final DateTimeFormatter[] FORMATOS_LECTURA = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    private static final DateTimeFormatter FORMATO_ESCRITURA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Fechas() {}

    public static LocalDate leer(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        String limpio = texto.trim();
        for (DateTimeFormatter formato : FORMATOS_LECTURA) {
            try {
                return LocalDate.parse(limpio, formato);
            } catch (Exception ignorada) {
                // probar el siguiente formato
            }
        }
        throw new IllegalArgumentException("No se pudo interpretar la fecha: " + texto);
    }

    @JsonComponent
    public static class Lectura extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            try {
                return leer(p.getText());
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @JsonComponent
    public static class Escritura extends JsonSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.format(FORMATO_ESCRITURA));
            }
        }
    }

    /** Los parámetros {@code fecha} de las rutas. */
    @Component
    public static class Parametro implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            return leer(source);
        }
    }
}
