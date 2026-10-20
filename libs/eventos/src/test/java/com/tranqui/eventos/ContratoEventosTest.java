package com.tranqui.eventos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContratoEventosTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = EventObjectMapperFactory.create();
    }

    @Test
    void sobreEvento_respetaEstructuraYNombresDeCampoDeSeccion5_3() throws Exception {
        UUID eventId = UUID.fromString("0e8a2c5e-6f6c-4b1e-9d1a-3c2b7f1e9a10");
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 10, 7, 21, 30, 0, 0, ZoneOffset.ofHours(-3));

        TurnoConfirmadoData data = new TurnoConfirmadoData(
                4521L,
                88L,
                7L,
                LocalDate.of(2026, 10, 10),
                "10:00",
                "ONLINE",
                new BigDecimal("45000")
        );

        EventEnvelope<TurnoConfirmadoData> envelope = EventEnvelope.<TurnoConfirmadoData>builder()
                .eventId(eventId)
                .eventType("turnos.turno.confirmado")
                .version(1)
                .occurredAt(occurredAt)
                .producer("turnos")
                .correlationId("c4f1-corr-id")
                .causationId("9b7d-cause-id")
                .aggregateType("turno")
                .aggregateId("4521")
                .aggregateVersion(3)
                .data(data)
                .build();

        String json = mapper.writeValueAsString(envelope);

        JsonNode root = mapper.readTree(json);
        assertThat(root.has("eventId")).isTrue();
        assertThat(root.get("eventId").asText()).isEqualTo("0e8a2c5e-6f6c-4b1e-9d1a-3c2b7f1e9a10");
        assertThat(root.get("eventType").asText()).isEqualTo("turnos.turno.confirmado");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.get("occurredAt").asText()).contains("2026-10-07T21:30:00");
        assertThat(root.get("producer").asText()).isEqualTo("turnos");
        assertThat(root.get("correlationId").asText()).isEqualTo("c4f1-corr-id");
        assertThat(root.get("causationId").asText()).isEqualTo("9b7d-cause-id");
        assertThat(root.get("aggregateType").asText()).isEqualTo("turno");
        assertThat(root.get("aggregateId").asText()).isEqualTo("4521");
        assertThat(root.get("aggregateVersion").asInt()).isEqualTo(3);

        JsonNode dataNode = root.get("data");
        assertThat(dataNode.get("turnoId").asLong()).isEqualTo(4521L);
        assertThat(dataNode.get("pacienteId").asLong()).isEqualTo(88L);
        assertThat(dataNode.get("profesionalId").asLong()).isEqualTo(7L);
        assertThat(dataNode.get("fecha").asText()).isEqualTo("2026-10-10");
        assertThat(dataNode.get("horaInicio").asText()).isEqualTo("10:00");
        assertThat(dataNode.get("modalidad").asText()).isEqualTo("ONLINE");
        assertThat(dataNode.get("precio").asInt()).isEqualTo(45000);

        // Deserialización completa
        EventEnvelope<TurnoConfirmadoData> deserializado = mapper.readValue(
                json, new TypeReference<EventEnvelope<TurnoConfirmadoData>>() {}
        );
        assertThat(deserializado.eventId()).isEqualTo(eventId);
        assertThat(deserializado.data()).isEqualTo(data);
    }

    // ==================== Tests de Roundtrip JSON para los 25 data records ====================

    @Test
    void roundtrip_usuarioRegistradoData() throws Exception {
        UsuarioRegistradoData data = new UsuarioRegistradoData(1L, "paciente@tranqui.com", "Juan", "Perez", "PACIENTE");
        probarRoundtrip(data, UsuarioRegistradoData.class);
    }

    @Test
    void roundtrip_usuarioActualizadoData() throws Exception {
        UsuarioActualizadoData data = new UsuarioActualizadoData(1L, "nuevo@tranqui.com", "Juan", "Perez", "+5491112345678");
        probarRoundtrip(data, UsuarioActualizadoData.class);
    }

    @Test
    void roundtrip_usuarioEliminadoData() throws Exception {
        UsuarioEliminadoData data = new UsuarioEliminadoData(1L, "Solicitud de baja voluntaria");
        probarRoundtrip(data, UsuarioEliminadoData.class);
    }

    @Test
    void roundtrip_codigoVerificacionGeneradoData() throws Exception {
        CodigoVerificacionGeneradoData data = new CodigoVerificacionGeneradoData(1L, "user@test.com", "492810", "VERIFICACION_EMAIL");
        probarRoundtrip(data, CodigoVerificacionGeneradoData.class);
    }

    @Test
    void roundtrip_ticketRespondidoData() throws Exception {
        TicketRespondidoData data = new TicketRespondidoData(5L, 1L, "user@test.com", "Problema con el pago", "Hemos procesado la consulta.");
        probarRoundtrip(data, TicketRespondidoData.class);
    }

    @Test
    void roundtrip_profesionalActualizadoData() throws Exception {
        ProfesionalActualizadoData data = new ProfesionalActualizadoData(
                10L, "Dra. Maria", "Gomez", "Psicología", "https://img.test/foto.jpg",
                List.of("ONLINE", "PRESENCIAL"), 50
        );
        probarRoundtrip(data, ProfesionalActualizadoData.class);
    }

    @Test
    void roundtrip_profesionalVisibilidadCambiadaData() throws Exception {
        ProfesionalVisibilidadCambiadaData data = new ProfesionalVisibilidadCambiadaData(10L, false, "Vacaciones");
        probarRoundtrip(data, ProfesionalVisibilidadCambiadaData.class);
    }

    @Test
    void roundtrip_tarifaActualizadaData() throws Exception {
        TarifaActualizadaData data = new TarifaActualizadaData(10L, 2L, new BigDecimal("35000.00"), "ARS");
        probarRoundtrip(data, TarifaActualizadaData.class);
    }

    @Test
    void roundtrip_disponibilidadActualizadaData() throws Exception {
        DisponibilidadActualizadaData data = new DisponibilidadActualizadaData(10L, 1, "09:00", "17:00", true);
        probarRoundtrip(data, DisponibilidadActualizadaData.class);
    }

    @Test
    void roundtrip_turnoReservadoData() throws Exception {
        TurnoReservadoData data = new TurnoReservadoData(
                100L, 20L, 10L, LocalDate.of(2026, 11, 1), "14:00",
                new BigDecimal("40000"), OffsetDateTime.now()
        );
        probarRoundtrip(data, TurnoReservadoData.class);
    }

    @Test
    void roundtrip_turnoConfirmadoData() throws Exception {
        TurnoConfirmadoData data = new TurnoConfirmadoData(
                100L, 20L, 10L, LocalDate.of(2026, 11, 1), "14:00", "ONLINE", new BigDecimal("40000")
        );
        probarRoundtrip(data, TurnoConfirmadoData.class);
    }

    @Test
    void roundtrip_turnoCanceladoData() throws Exception {
        TurnoCanceladoData data = new TurnoCanceladoData(100L, 20L, 10L, "PACIENTE", "Imprevisto", true);
        probarRoundtrip(data, TurnoCanceladoData.class);
    }

    @Test
    void roundtrip_turnoReprogramadoData() throws Exception {
        TurnoReprogramadoData data = new TurnoReprogramadoData(
                100L, 20L, 10L,
                LocalDate.of(2026, 11, 1), "14:00",
                LocalDate.of(2026, 11, 2), "16:00"
        );
        probarRoundtrip(data, TurnoReprogramadoData.class);
    }

    @Test
    void roundtrip_turnoExpiradoData() throws Exception {
        TurnoExpiradoData data = new TurnoExpiradoData(100L, 20L, 10L);
        probarRoundtrip(data, TurnoExpiradoData.class);
    }

    @Test
    void roundtrip_turnoRecordatorioData() throws Exception {
        TurnoRecordatorioData data = new TurnoRecordatorioData(
                100L, 20L, 10L, LocalDate.of(2026, 11, 1), "14:00"
        );
        probarRoundtrip(data, TurnoRecordatorioData.class);
    }

    @Test
    void roundtrip_documentoSolicitadoData() throws Exception {
        DocumentoSolicitadoData data = new DocumentoSolicitadoData(
                100L, 20L, 10L, "CERTIFICADO_ASISTENCIA", "Para presentar en el trabajo"
        );
        probarRoundtrip(data, DocumentoSolicitadoData.class);
    }

    @Test
    void roundtrip_pagoAprobadoData() throws Exception {
        PagoAprobadoData data = new PagoAprobadoData(500L, 100L, "mp-pay-999", new BigDecimal("40000"), "credit_card");
        probarRoundtrip(data, PagoAprobadoData.class);
    }

    @Test
    void roundtrip_pagoRechazadoData() throws Exception {
        PagoRechazadoData data = new PagoRechazadoData(500L, 100L, "mp-pay-999", "cc_rejected_insufficient_amount");
        probarRoundtrip(data, PagoRechazadoData.class);
    }

    @Test
    void roundtrip_reembolsoRealizadoData() throws Exception {
        ReembolsoRealizadoData data = new ReembolsoRealizadoData(300L, 500L, 100L, new BigDecimal("40000"), "Cancelación con más de 48hs");
        probarRoundtrip(data, ReembolsoRealizadoData.class);
    }

    @Test
    void roundtrip_cuentaMpVinculadaData() throws Exception {
        CuentaMpVinculadaData data = new CuentaMpVinculadaData(10L, "mp-user-789654");
        probarRoundtrip(data, CuentaMpVinculadaData.class);
    }

    @Test
    void roundtrip_cuentaMpDesvinculadaData() throws Exception {
        CuentaMpDesvinculadaData data = new CuentaMpDesvinculadaData(10L);
        probarRoundtrip(data, CuentaMpDesvinculadaData.class);
    }

    @Test
    void roundtrip_suscripcionCambioEstadoData() throws Exception {
        SuscripcionCambioEstadoData data = new SuscripcionCambioEstadoData(
                80L, 10L, "PENDIENTE", "ACTIVA", LocalDate.of(2026, 12, 1)
        );
        probarRoundtrip(data, SuscripcionCambioEstadoData.class);
    }

    @Test
    void roundtrip_meetCreadoData() throws Exception {
        MeetCreadoData data = new MeetCreadoData(100L, "https://meet.google.com/abc-defg-hij", "g-event-1234");
        probarRoundtrip(data, MeetCreadoData.class);
    }

    @Test
    void roundtrip_horarioBloqueadoData() throws Exception {
        HorarioBloqueadoData data = new HorarioBloqueadoData(
                10L, "g-event-pers-55",
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(2), "Turno médico personal"
        );
        probarRoundtrip(data, HorarioBloqueadoData.class);
    }

    @Test
    void roundtrip_mensajeEnviadoData() throws Exception {
        MensajeEnviadoData data = new MensajeEnviadoData(777L, 33L, 20L, 10L, OffsetDateTime.now());
        probarRoundtrip(data, MensajeEnviadoData.class);
    }

    private <T> void probarRoundtrip(T original, Class<T> clazz) throws Exception {
        String json = mapper.writeValueAsString(original);
        assertThat(json).isNotNull().isNotBlank();
        T recuperado = mapper.readValue(json, clazz);
        assertThat(recuperado).isEqualTo(original);
    }
}
