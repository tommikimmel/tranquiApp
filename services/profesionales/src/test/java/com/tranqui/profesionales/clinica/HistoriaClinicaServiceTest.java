package com.tranqui.profesionales.clinica;

import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.interno.ClienteChat;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.perfil.NoEncontradoException;
import com.tranqui.profesionales.perfil.ServicioNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lecturas de la historia clínica del paciente, anulación de seguimientos y la edición de la ficha en identidad. */
class HistoriaClinicaServiceTest {

    private final Personas personas = mock(Personas.class);
    private final Seguimientos seguimientos = mock(Seguimientos.class);
    private final Informes informes = mock(Informes.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final AccesoPacientes acceso = mock(AccesoPacientes.class);
    private final HistoriaClinicaService service = new HistoriaClinicaService(personas, mock(TurnosAtendidos.class), seguimientos,
            informes, mock(ClienteChat.class), interno, acceso, mock(OutboxPublisher.class));

    private Persona medico;
    private Persona paciente;

    private static Persona persona(long id, String nombre, String apellido) {
        Persona p = mock(Persona.class);
        when(p.id()).thenReturn(id);
        when(p.nombre()).thenReturn(nombre);
        when(p.apellido()).thenReturn(apellido);
        return p;
    }

    @BeforeEach
    void setUp() {
        medico = persona(2L, "Lucía", "Paz");
        paciente = persona(1L, "Ana", null);
        when(personas.porEmail("lucia@mail.com")).thenReturn(Optional.of(medico));
        when(personas.porEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        when(personas.porId(1L)).thenReturn(Optional.of(paciente));
    }

    private static Seguimientos.Seguimiento seguimiento(long id, long medicoId) {
        Seguimientos.Seguimiento s = mock(Seguimientos.Seguimiento.class);
        when(s.id()).thenReturn(id);
        when(s.medicoId()).thenReturn(medicoId);
        when(s.hashIntegridad()).thenReturn("h" + id);
        return s;
    }

    @Test
    void seguimientos_delMedicoYDelPacienteConElNombreDelProfesional() {
        Seguimientos.Seguimiento s = seguimiento(10L, 2L);
        Seguimientos.Seguimiento sinMedico = seguimiento(11L, 99L);
        when(seguimientos.porPacienteYMedico(1L, 2L)).thenReturn(List.of(s));
        when(seguimientos.porPaciente(1L)).thenReturn(List.of(s, sinMedico));
        when(personas.porIds(any())).thenReturn(Map.of(2L, medico));

        assertThat(service.obtenerSeguimientos(1L, "lucia@mail.com")).singleElement()
                .extracting("nombreMedico").isEqualTo("Lucía Paz");
        List<Dtos.SeguimientoResponse> delPaciente = service.obtenerSeguimientosPaciente("ana@mail.com");
        assertThat(delPaciente).extracting("nombreMedico").containsExactly("Lucía Paz", null);
        assertThatThrownBy(() -> service.obtenerSeguimientos(1L, "nadie@mail.com")).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> service.obtenerSeguimientosPaciente("nadie@mail.com")).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void informesDelPaciente() {
        Informes.Informe inf = mock(Informes.Informe.class);
        when(inf.id()).thenReturn(20L);
        when(inf.medicoId()).thenReturn(2L);
        when(informes.porPaciente(1L)).thenReturn(List.of(inf));
        Persona sinApellido = persona(2L, "Lucía", null);
        when(personas.porIds(any())).thenReturn(Map.of(2L, sinApellido));
        assertThat(service.obtenerInformesPaciente("ana@mail.com")).extracting("nombreMedico").containsExactly("Lucía");
        assertThatThrownBy(() -> service.obtenerInformesPaciente("nadie@mail.com")).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void elPacienteNoCargaSeguimientosSinProfesional() {
        assertThatThrownBy(() -> service.guardarSeguimientoPaciente("ana@mail.com", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardarSeguimientoPaciente("nadie@mail.com", null)).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void eliminarSeguimiento_soloElAutorYConMotivo() {
        Seguimientos.Seguimiento propio = seguimiento(10L, 2L);
        Seguimientos.Seguimiento ajeno = seguimiento(11L, 7L);
        when(seguimientos.porId(10L)).thenReturn(Optional.of(propio));
        when(seguimientos.porId(11L)).thenReturn(Optional.of(ajeno));
        service.eliminarSeguimiento(10L, "lucia@mail.com", null);
        verify(seguimientos).actualizarEstadoYMotivo(10L, "ANULADO", "No especificado", "h10");
        service.eliminarSeguimiento(10L, "lucia@mail.com", "duplicado");
        verify(seguimientos).actualizarEstadoYMotivo(10L, "ANULADO", "duplicado", "h10");

        assertThatThrownBy(() -> service.eliminarSeguimiento(11L, "lucia@mail.com", "x")).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> service.eliminarSeguimiento(12L, "lucia@mail.com", "x")).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> service.eliminarSeguimiento(10L, "nadie@mail.com", "x")).isInstanceOf(NoEncontradoException.class);
        verify(seguimientos, never()).actualizarEstadoYMotivo(eq(11L), anyString(), anyString(), anyString());
    }

    @Test
    void actualizarPaciente_erroresDeIdentidad() {
        Dtos.PacienteDto dto = mock(Dtos.PacienteDto.class);
        when(interno.actualizarPaciente(eq(1L), any())).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad",
                null, "Datos del paciente inválidos: dni".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.actualizarPaciente(1L, "lucia@mail.com", dto))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Datos del paciente inválidos");

        when(interno.actualizarPaciente(eq(1L), any())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> service.actualizarPaciente(1L, "lucia@mail.com", dto)).isInstanceOf(ServicioNoDisponibleException.class);
        when(interno.actualizarPaciente(eq(1L), any())).thenThrow(new ResourceAccessException("caído"));
        assertThatThrownBy(() -> service.actualizarPaciente(1L, "lucia@mail.com", dto)).isInstanceOf(ServicioNoDisponibleException.class);
        verify(acceso, org.mockito.Mockito.times(3)).exigirAtendido(2L, 1L);

        assertThatThrownBy(() -> service.actualizarPaciente(9L, "lucia@mail.com", dto)).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> service.actualizarPaciente(1L, "nadie@mail.com", dto)).isInstanceOf(NoEncontradoException.class);
        verify(interno, never()).actualizarPaciente(eq(9L), any());
    }
}
