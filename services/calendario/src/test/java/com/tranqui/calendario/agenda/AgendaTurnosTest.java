package com.tranqui.calendario.agenda;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.calendario.google.GoogleCalendarApi;
import com.tranqui.calendario.google.TokensGoogle;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Con Google habilitado: los eventos y el Meet de los turnos, como hacía el monolito. */
class AgendaTurnosTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate FECHA = LocalDate.now().plusDays(2);

    private final TurnosAgendados turnos = mock(TurnosAgendados.class);
    private final CuentasGoogle cuentas = mock(CuentasGoogle.class);
    private final Participantes participantes = mock(Participantes.class);
    private final TokensGoogle tokens = mock(TokensGoogle.class);
    private final GoogleCalendarApi google = mock(GoogleCalendarApi.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final CuentasGoogle.Cuenta cuenta = new CuentasGoogle.Cuenta(2L, "a", "r", null, null, null, null, null, null, false);

    private AgendaTurnos agenda;

    @BeforeEach
    void preparar() {
        agenda = new AgendaTurnos(turnos, cuentas, participantes, tokens, google, outbox, true);
        when(cuentas.buscar(2L)).thenReturn(Optional.of(cuenta));
        when(tokens.accessToken(cuenta)).thenReturn("token-de-acceso");
        when(participantes.porId(1L)).thenReturn(Optional.of(new Participantes.Participante(1L, "pac@mail.com", "Ana", "PACIENTE")));
        when(turnos.guardarEvento(eq(70L), any(), any())).thenReturn(1);
    }

    private void turno(String modalidad, String eventId) {
        when(turnos.buscar(70L)).thenReturn(Optional.of(new TurnosAgendados.Turno(70L, 2L, 1L, FECHA, LocalTime.of(10, 0),
                LocalTime.of(10, 45), modalidad, eventId, null, 0)));
    }

    @Test
    void online_creaElEventoConMeetYAvisa() throws IOException {
        turno("ONLINE", null);
        when(google.crear(anyString(), anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(new GoogleCalendarApi.EventoCreado("g-70", "https://meet.google.com/abc-defg-hij"));

        agenda.crearEvento(70L);

        verify(google).crear("token-de-acceso", "primary", "[Tranqui App] Consulta Online - Ana",
                "Consulta de telemedicina con Tranqui App", ZonedDateTime.of(FECHA, LocalTime.of(10, 0), ZONA),
                ZonedDateTime.of(FECHA, LocalTime.of(10, 45), ZONA), true);
        verify(turnos).guardarEvento(70L, "g-70", "https://meet.google.com/abc-defg-hij");
        ArgumentCaptor<MeetCreadoData> aviso = ArgumentCaptor.forClass(MeetCreadoData.class);
        verify(outbox).publicar(eq(Topicos.CALENDARIO_MEET_CREADO_V1), eq("turno"), eq("70"), eq(1), aviso.capture());
        assertThat(aviso.getValue().meetUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(aviso.getValue().googleEventId()).isEqualTo("g-70");
        assertThat(aviso.getValue().linkNuevo()).isTrue();
    }

    @Test
    void presencial_eventoSinMeetYSinMailDeLink() throws IOException {
        turno("PRESENCIAL", null);
        when(google.crear(anyString(), anyString(), anyString(), anyString(), any(), any(), eq(false)))
                .thenReturn(new GoogleCalendarApi.EventoCreado("g-70", ""));

        agenda.crearEvento(70L);

        ArgumentCaptor<MeetCreadoData> aviso = ArgumentCaptor.forClass(MeetCreadoData.class);
        verify(outbox).publicar(eq(Topicos.CALENDARIO_MEET_CREADO_V1), eq("turno"), eq("70"), eq(1), aviso.capture());
        assertThat(aviso.getValue().linkNuevo()).isFalse();
        assertThat(aviso.getValue().googleEventId()).isEqualTo("g-70");
    }

    @Test
    void googleFalla_quedaSinEventoParaReintentar() throws IOException {
        turno("ONLINE", null);
        when(google.crear(anyString(), anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenThrow(new IOException("503"));

        agenda.crearEvento(70L);

        verify(turnos, never()).guardarEvento(any(), any(), any());
        verify(outbox, never()).publicar(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void sinCuentaDeGoogle_noHaceNada() throws IOException {
        turno("ONLINE", null);
        when(cuentas.buscar(2L)).thenReturn(Optional.empty());

        agenda.crearEvento(70L);

        verify(google, never()).crear(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void conEvento_noLoCreaDosVeces() throws IOException {
        turno("ONLINE", "g-70");
        agenda.crearEvento(70L);
        verify(google, never()).crear(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void cancelado_borraElEventoYLaCopia() throws IOException {
        turno("ONLINE", "g-70");
        agenda.cancelado(70L);
        verify(google).borrar("token-de-acceso", "primary", "g-70");
        verify(turnos).borrar(70L);
    }

    @Test
    void reprogramado_mueveElEvento() throws IOException {
        turno("ONLINE", "g-70");
        LocalDate nueva = FECHA.plusDays(1);

        agenda.reprogramado(70L, nueva, LocalTime.of(15, 0), null);

        // Sin hora de fin nueva conserva la duración (45 minutos).
        verify(turnos).mover(70L, nueva, LocalTime.of(15, 0), LocalTime.of(15, 45));
        verify(google).mover("token-de-acceso", "primary", "g-70", ZonedDateTime.of(nueva, LocalTime.of(15, 0), ZONA),
                ZonedDateTime.of(nueva, LocalTime.of(15, 45), ZONA));
    }
}
