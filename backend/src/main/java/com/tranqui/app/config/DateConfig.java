package com.tranqui.app.config;

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

public class DateConfig {

    private static final DateTimeFormatter[] DESERIALIZE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    private static final DateTimeFormatter SERIALIZE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        dateStr = dateStr.trim();
        for (DateTimeFormatter formatter : DESERIALIZE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("No se pudo interpretar la fecha: " + dateStr);
    }

    @JsonComponent
    public static class MultiFormatLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String dateStr = p.getText();
            if (dateStr == null || dateStr.trim().isEmpty()) {
                return null;
            }
            try {
                return parseLocalDate(dateStr);
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    @JsonComponent
    public static class CustomLocalDateSerializer extends JsonSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.format(SERIALIZE_FORMATTER));
            }
        }
    }

    @Component
    public static class StringToLocalDateConverter implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            return parseLocalDate(source);
        }
    }
}
