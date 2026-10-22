package com.tranqui.app.eventos;

import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.eventos.data.MeetCreadoData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fase 3: el monolito guarda en el turno el Meet que crea el servicio de calendario. */
class ManejadoresCalendarioTest {

    private final TurnoRepository turnos = mock(TurnoRepository.class);

    private static MeetCreadoData meet(String url, String eventId) {
        return new MeetCreadoData(7L, url, eventId, 1L, 2L, LocalDate.now(), "10:00", true);
    }

    @Test
    void guardaElLinkYElIdDelEvento() {
        Turno turno = Turno.builder().id(7L).build();
        when(turnos.findById(7L)).thenReturn(Optional.of(turno));

        ManejadoresCalendario.guardarMeet(turnos, meet("https://meet.google.com/abc-defg-hij", "g-1"));

        assertThat(turno.getTelemedicinaUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(turno.getGoogleEventId()).isEqualTo("g-1");
        verify(turnos).save(turno);
    }

    @Test
    void repetido_noVuelveAGuardar() {
        Turno turno = Turno.builder().id(7L).telemedicinaUrl("https://meet.google.com/abc-defg-hij").googleEventId("g-1").build();
        when(turnos.findById(7L)).thenReturn(Optional.of(turno));

        ManejadoresCalendario.guardarMeet(turnos, meet("https://meet.google.com/abc-defg-hij", "g-1"));

        verify(turnos, never()).save(any());
    }

    @Test
    void presencialSinLink_guardaSoloElEvento() {
        Turno turno = Turno.builder().id(7L).build();
        when(turnos.findById(7L)).thenReturn(Optional.of(turno));

        ManejadoresCalendario.guardarMeet(turnos, meet("", "g-2"));

        assertThat(turno.getTelemedicinaUrl()).isNull();
        assertThat(turno.getGoogleEventId()).isEqualTo("g-2");
    }

    @Test
    void turnoQueNoExiste_noFalla() {
        when(turnos.findById(7L)).thenReturn(Optional.empty());
        ManejadoresCalendario.guardarMeet(turnos, meet("https://meet.google.com/x", "g-3"));
        verify(turnos, never()).save(any());
    }
}
