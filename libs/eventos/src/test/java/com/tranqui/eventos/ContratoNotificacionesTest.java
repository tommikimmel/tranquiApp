package com.tranqui.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.data.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contratos agregados o ampliados en la Fase 1 (notificaciones): roundtrip JSON, compatibilidad
 * con productores viejos (campos nuevos opcionales) y ningún campo prohibido por la sección 5.3.
 */
class ContratoNotificacionesTest {

    private final ObjectMapper mapper = EventObjectMapperFactory.create();

    /** Sección 5.3: nunca contraseñas, tokens ni datos de salud en un evento. */
    private static final Pattern CAMPO_PROHIBIDO = Pattern.compile(
            ".*(password|contrasena|token|secret|clave|diagnostico|sintoma|nota|contenido|plantrabajo|estadoanimo|archivo|pdf).*");

    @Test
    void ningunData_tieneCamposProhibidos() throws Exception {
        List<Class<?>> records = recordsDelPaquete();
        assertThat(records).hasSizeGreaterThanOrEqualTo(31);

        List<String> prohibidos = new ArrayList<>();
        for (Class<?> record : records) {
            // Fase 9: PasswordTemporalGeneradaData lleva la contraseña temporal para el envío por correo
            if (record.equals(PasswordTemporalGeneradaData.class)) {
                continue;
            }
            for (RecordComponent componente : record.getRecordComponents()) {
                if (CAMPO_PROHIBIDO.matcher(componente.getName().toLowerCase(Locale.ROOT)).matches()) {
                    prohibidos.add(record.getSimpleName() + "." + componente.getName());
                }
            }
        }
        assertThat(prohibidos).isEmpty();
    }

    @Test
    void camposNuevosSonOpcionales_unEventoViejoSeLeeIgual() throws Exception {
        String viejo = "{\"turnoId\":1,\"pacienteId\":2,\"profesionalId\":3,\"fecha\":\"2026-10-10\","
                + "\"horaInicio\":\"10:00\",\"modalidad\":\"ONLINE\",\"precio\":45000}";

        TurnoConfirmadoData data = mapper.readValue(viejo, TurnoConfirmadoData.class);

        assertThat(data.turnoId()).isEqualTo(1L);
        assertThat(data.ocupaAgenda()).isNull();
        assertThat(data.esDocumento()).isFalse();
        assertThat(data.meetUrl()).isNull();
    }

    @Test
    void constructorViejo_dejaLosCamposNuevosEnNull() throws Exception {
        JsonNode json = mapper.valueToTree(new TurnoCanceladoData(1L, 2L, 3L, "PACIENTE", "x", false));

        assertThat(json.has("turnoId")).isTrue();
        assertThat(json.get("resultadoReembolso").isNull()).isTrue();
    }

    @Test
    void roundtrip_contratosAmpliados() throws Exception {
        roundtrip(new TurnoConfirmadoData(1L, 2L, 3L, LocalDate.of(2026, 10, 10), "10:00", "PRESENCIAL",
                new BigDecimal("45000"), true, "Consulta", null, "Av. Siempre Viva 742", null, TurnoConfirmadoData.ORIGEN_PAGO));
        roundtrip(new TurnoCanceladoData(1L, 2L, 3L, "PROFESIONAL", "Cancelado por usuario", true,
                LocalDate.of(2026, 10, 10), "10:00", true, "REEMBOLSADO"));
        roundtrip(new TurnoReprogramadoData(1L, 2L, 3L, LocalDate.of(2026, 10, 10), "10:00",
                LocalDate.of(2026, 10, 12), "11:00", "ONLINE", null, "https://meet.google.com/abc"));
        roundtrip(new TurnoRecordatorioData(1L, 2L, 3L, LocalDate.of(2026, 10, 10), "10:00",
                "Presencial", "Consulta", "Av. Siempre Viva 742"));
        roundtrip(new UsuarioActualizadoData(1L, "a@b.com", "Ana", "Paz", "11", "PACIENTE", false));
        roundtrip(new MeetCreadoData(1L, "https://meet.google.com/abc", "g-1", 2L, 3L,
                LocalDate.of(2026, 10, 10), "10:00", true));
        roundtrip(new CodigoVerificacionGeneradoData(1L, "a@b.com", "123456", "REGISTRO", "Ana"));
    }

    @Test
    void roundtrip_contratosNuevos() throws Exception {
        roundtrip(new AsistenciaConfirmadaData(1L, 2L, 3L, LocalDate.of(2026, 10, 10), "10:00"));
        roundtrip(new DocumentoEnviadoData(1L, 2L, 3L, "Certificado", "ADJUNTO"));
        roundtrip(new TicketCreadoData(1L, 2L, "Ana Paz", "No puedo pagar"));
        roundtrip(new TicketMensajeRecibidoData(1L, 2L, "Ana Paz", "No puedo pagar"));
        roundtrip(new HistoriaClinicaActualizadaData(3L, 2L, "INFORME", "PROFESIONAL", "EVOLUCION"));
        roundtrip(new SuscripcionAvisoData(1L, 3L, SuscripcionAvisoData.PAGO_CONFIRMADO, "Profesional",
                new BigDecimal("9999"), LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0),
                "MONTHLY", "FC-0001", null));
    }

    @Test
    void topicosNuevos_estanEnElCatalogoYSonValidos() {
        for (String topico : List.of(Topicos.TURNOS_ASISTENCIA_CONFIRMADA_V1, Topicos.TURNOS_DOCUMENTO_ENVIADO_V1,
                Topicos.IDENTIDAD_TICKET_CREADO_V1, Topicos.IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1,
                Topicos.PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1, Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1)) {
            assertThat(Topicos.esValido(topico)).as(topico).isTrue();
            assertThat(Topicos.TODOS_LOS_TOPICOS).contains(topico);
        }
    }

    private <T> void roundtrip(T original) throws Exception {
        @SuppressWarnings("unchecked")
        Class<T> clase = (Class<T>) original.getClass();
        T leido = mapper.readValue(mapper.writeValueAsString(original), clase);
        assertThat(leido).isEqualTo(original);
    }

    private static List<Class<?>> recordsDelPaquete() throws Exception {
        String paquete = UsuarioRegistradoData.class.getPackageName();
        URL url = UsuarioRegistradoData.class.getResource(UsuarioRegistradoData.class.getSimpleName() + ".class");
        File carpeta = new File(url.toURI()).getParentFile();
        List<Class<?>> records = new ArrayList<>();
        for (File archivo : carpeta.listFiles((d, n) -> n.endsWith("Data.class"))) {
            Class<?> clase = Class.forName(paquete + "." + archivo.getName().replace(".class", ""));
            if (clase.isRecord()) {
                records.add(clase);
            }
        }
        return records;
    }
}
