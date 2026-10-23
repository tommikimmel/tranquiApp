package com.tranqui.comun.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void serializacionYDeserializacionJson_funcionaCorrectamente() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.now();
        ErrorResponse error = new ErrorResponse(
                "recurso_no_encontrado",
                "El turno con ID 45 no existe",
                404,
                ahora,
                "corr-abc-123",
                Map.of("campo", "turnoId", "valor", 45)
        );

        String json = mapper.writeValueAsString(error);
        assertThat(json).contains("\"error\":\"recurso_no_encontrado\"")
                .contains("\"mensaje\":\"El turno con ID 45 no existe\"")
                .contains("\"status\":404")
                .contains("\"correlationId\":\"corr-abc-123\"")
                .contains("\"detalles\":{");

        ErrorResponse deserializado = mapper.readValue(json, ErrorResponse.class);
        assertThat(deserializado.error()).isEqualTo("recurso_no_encontrado");
        assertThat(deserializado.mensaje()).isEqualTo("El turno con ID 45 no existe");
        assertThat(deserializado.status()).isEqualTo(404);
        assertThat(deserializado.correlationId()).isEqualTo("corr-abc-123");
        assertThat(deserializado.detalles()).containsEntry("campo", "turnoId");
    }

    @Test
    void factoryMethods_generanObjetoSinDetallesSiNoSeProveen() throws Exception {
        ErrorResponse error = ErrorResponse.of("bad_request", "Parametro invalido", 400, "corr-xyz");

        assertThat(error.error()).isEqualTo("bad_request");
        assertThat(error.mensaje()).isEqualTo("Parametro invalido");
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.correlationId()).isEqualTo("corr-xyz");
        assertThat(error.detalles()).isNull();

        String json = mapper.writeValueAsString(error);
        // Debe excluir campos nulos (NON_NULL)
        assertThat(json).doesNotContain("\"detalles\"");
    }
}
