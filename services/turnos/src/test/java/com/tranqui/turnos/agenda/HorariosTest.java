package com.tranqui.turnos.agenda;

import com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los horarios libres: turnos que bloquean, el filtro de Google Calendar y su caché, y el conteo de la home. */
class HorariosTest {

    private static final LocalDate FECHA = LocalDate.now().plusDays(7);

    private final Turnos turnos = mock(Turnos.class);
    private final Profesionales profesionales = mock(Profesionales.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final Horarios horarios = new Horarios(turnos, profesionales, interno);

    @BeforeEach
    void setUp() {
        int dia = FECHA.getDayOfWeek().getValue();
        when(profesionales.franjas(eq(2L), anyString())).thenReturn(List.of(new Franja(dia, "09:00", "12:00", "ONLINE")));
        when(profesionales.porId(2L)).thenReturn(Optional.of(agenda(true, true)));
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of());
        when(interno.ocupados(eq(2L), any())).thenReturn(List.of());
    }

    private static Profesionales.Agenda agenda(boolean online, boolean presencial) {
        return new Profesionales.Agenda(2L, 60, 0, online, presencial, BigDecimal.TEN, null, null, null, null, null, null, null);
    }

    private static Turno turno(String estado, LocalTime inicio, boolean ocupaAgenda, LocalDateTime creado) {
        Turno t = new Turno();
        t.estado = estado;
        t.horaInicio = inicio;
        t.horaFin = inicio.plusMinutes(60);
        t.ocupaAgenda = ocupaAgenda;
        t.fechaCreacion = creado;
        return t;
    }

    @Test
    void losTurnosVigentesBloqueanYLosVencidosNo() {
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of(
                turno(Turno.CONFIRMADO, LocalTime.of(9, 0), true, null),
                turno(Turno.PENDIENTE_PAGO, LocalTime.of(10, 0), true, LocalDateTime.now().minusMinutes(1)),
                turno(Turno.PENDIENTE_PAGO, LocalTime.of(11, 0), true, LocalDateTime.now().minusMinutes(30)),
                turno(Turno.EXPIRADO, LocalTime.of(11, 0), true, null),
                turno(Turno.CONFIRMADO, LocalTime.of(11, 0), false, null)));
        assertThat(horarios.disponibles(2L, FECHA, "ONLINE")).containsExactly(LocalTime.of(11, 0));
    }

    @Test
    void sinAgenda_usaLaDuracionPorDefectoYNoConsultaGoogle() {
        when(profesionales.porId(2L)).thenReturn(Optional.empty());
        assertThat(horarios.disponibles(2L, FECHA, "ONLINE")).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 45),
                LocalTime.of(10, 30), LocalTime.of(11, 15));
        verify(interno, times(0)).ocupados(any(), any());
    }

    @Test
    void googleCalendar_filtraLosChoquesYElDiaEnteroBloqueaTodo() {
        when(interno.ocupados(2L, FECHA)).thenReturn(List.of(new ClienteInterno.Intervalo(LocalTime.of(10, 30), LocalTime.of(10, 45), false)));
        assertThat(horarios.disponibles(2L, FECHA, "ONLINE")).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
        // la segunda consulta sale de la caché
        horarios.disponibles(2L, FECHA, "PRESENCIAL");
        verify(interno, times(1)).ocupados(2L, FECHA);

        LocalDate otroDia = FECHA.plusWeeks(1);
        when(turnos.delDia(2L, otroDia)).thenReturn(List.of());
        when(interno.ocupados(2L, otroDia)).thenReturn(List.of(new ClienteInterno.Intervalo(LocalTime.MIN, LocalTime.MAX, true)));
        assertThat(horarios.disponibles(2L, otroDia, "ONLINE")).isEmpty();
    }

    @Test
    void calendarioCaido_ofreceLaAgendaSinFiltro() {
        when(interno.ocupados(2L, FECHA)).thenThrow(new IllegalStateException("caído"));
        assertThat(horarios.disponibles(2L, FECHA, "ONLINE")).hasSize(3);
    }

    @Test
    void conteos_porModalidadUnionYErrores() {
        when(profesionales.porId(3L)).thenReturn(Optional.empty());
        when(profesionales.porId(4L)).thenReturn(Optional.of(agenda(false, true)));
        when(profesionales.franjas(eq(4L), anyString())).thenReturn(List.of());
        when(turnos.delDia(4L, FECHA)).thenReturn(List.of());
        when(profesionales.porId(5L)).thenThrow(new IllegalStateException("base caída"));

        Map<Long, Integer> conModalidad = horarios.conteos(List.of(2L), FECHA, "ONLINE");
        assertThat(conModalidad).containsEntry(2L, 3);

        Map<Long, Integer> union = horarios.conteos(List.of(2L, 3L, 4L, 5L), FECHA, null);
        assertThat(union).containsEntry(2L, 3).containsEntry(3L, 0).containsEntry(4L, 0).containsEntry(5L, 0);
    }

    @Test
    void bloques_respetanElIntervaloYElPasadoDeHoy() {
        int dia = FECHA.getDayOfWeek().getValue();
        List<Franja> franjas = List.of(new Franja(dia, "09:00", "11:00", "ONLINE"), new Franja(dia == 1 ? 2 : 1, "09:00", "11:00", "ONLINE"));
        assertThat(Horarios.bloques(franjas, List.of(), FECHA, 45, 15)).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(Horarios.bloques(List.of(new Franja(dia, "23:00", "23:59", "ONLINE")), List.of(), FECHA, 30, 0))
                .containsExactly(LocalTime.of(23, 0), LocalTime.of(23, 30));
    }
}
