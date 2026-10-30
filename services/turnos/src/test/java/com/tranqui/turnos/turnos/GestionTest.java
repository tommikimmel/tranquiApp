package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las acciones sobre un turno ya reservado: abandono, asistencia, documento, recetas, reprogramación y enlaces. */
class GestionTest {

    private final Turnos turnos = mock(Turnos.class);
    private final Personas personas = mock(Personas.class);
    private final Profesionales profesionales = mock(Profesionales.class);
    private final Publicador publicador = mock(Publicador.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final Gestion gestion = new Gestion(turnos, personas, profesionales, publicador, interno, "https://tranqui.test");
    private Turno t;

    private static Personas.Persona persona(long id, String email) {
        return new Personas.Persona(id, 0, "PACIENTE", email, "N" + id, "A", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, true, false);
    }

    @BeforeEach
    void setUp() {
        t = new Turno();
        t.id = 10L;
        t.medicoId = 2L;
        t.pacienteId = 1L;
        t.fecha = LocalDate.now().plusDays(5);
        t.horaInicio = LocalTime.of(10, 0);
        t.horaFin = LocalTime.of(10, 45);
        t.estado = Turno.CONFIRMADO;
        t.modalidad = "PRESENCIAL";
        when(turnos.porIdParaActualizar(10L)).thenReturn(Optional.of(t));
        when(turnos.porId(10L)).thenReturn(Optional.of(t));
        when(personas.porId(2L)).thenReturn(Optional.of(persona(2L, "lucia@mail.com")));
        when(personas.porId(1L)).thenReturn(Optional.of(persona(1L, "ana@mail.com")));
    }

    @Test
    void abandonarPago_soloConElTokenYMientrasEspera() {
        t.tokenReserva = "tok";
        assertThatThrownBy(() -> gestion.abandonarPago(10L, null)).isInstanceOf(IllegalStateException.class);
        gestion.abandonarPago(10L, "tok");
        assertThat(t.estado).isEqualTo(Turno.CONFIRMADO);
        t.estado = Turno.PENDIENTE_PAGO;
        gestion.abandonarPago(10L, "tok");
        assertThat(t.estado).isEqualTo(Turno.CANCELADO);
        assertThatThrownBy(() -> gestion.abandonarPago(99L, "tok")).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void asistencia_valoresDelDominio() {
        gestion.asistencia(10L, "llego", "lucia@mail.com");
        assertThat(t.asistencia).isEqualTo("LLEGO");
        assertThatThrownBy(() -> gestion.asistencia(10L, "TARDE", "lucia@mail.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gestion.asistencia(10L, null, "lucia@mail.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gestion.asistencia(10L, "LLEGO", "ana@mail.com")).isInstanceOf(ProhibidoException.class);
    }

    @Test
    void documentoEnviado_reglasYEnvio() {
        String archivo = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "otro@mail.com", archivo, "a.pdf")).hasMessageContaining("permiso");
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", archivo, "a.pdf")).hasMessageContaining("no corresponde");
        t.ocupaAgenda = false;
        t.estado = Turno.PENDIENTE_PAGO;
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", archivo, "a.pdf")).hasMessageContaining("pagado");
        t.estado = Turno.CONFIRMADO;
        t.esReceta = true;
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", archivo, "a.pdf")).hasMessageContaining("recetas");
        t.esReceta = false;
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", " ", "a.pdf")).hasMessageContaining("Adjuntá");

        doThrow(new ResourceAccessException("caído")).when(interno).enviarDocumento(any());
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", archivo, "a.pdf")).hasMessageContaining("No se pudo enviar");
        assertThat(t.documentoEnviado).isFalse();

        org.mockito.Mockito.reset(interno);
        gestion.documentoEnviado(10L, "LUCIA@mail.com", archivo, "a.pdf");
        assertThat(t.documentoEnviado).isTrue();
        verify(publicador).documentoEnviado(eq(t), any(), eq("ADJUNTO"));

        when(personas.porId(1L)).thenReturn(Optional.of(persona(1L, " ")));
        assertThatThrownBy(() -> gestion.documentoEnviado(10L, "lucia@mail.com", archivo, "a.pdf")).hasMessageContaining("email registrado");
    }

    @Test
    void recetasEmitidas_marcaLasPendientes() {
        Turno receta = new Turno();
        receta.id = 11L;
        when(turnos.recetasPendientes(2L, 1L)).thenReturn(List.of(receta));
        gestion.recetasEmitidas(2L, 1L);
        assertThat(receta.documentoEnviado).isTrue();
        verify(publicador).documentoEnviado(receta, Etiquetas.tipo(receta), "RECETA");
    }

    @Test
    void reprogramar_conLaDuracionDeLaAgendaYReseteaLaConfirmacion() {
        t.tokenConfirmacionAsistencia = "viejo";
        t.asistenciaConfirmadaPaciente = true;
        LocalDate nueva = LocalDate.now().plusDays(9);
        when(profesionales.porId(2L)).thenReturn(Optional.empty());
        gestion.reprogramar(10L, nueva.toString(), "11:30", "lucia@mail.com");
        assertThat(t.horaFin).isEqualTo(LocalTime.of(12, 15));
        assertThat(t.tokenConfirmacionAsistencia).isNull();
        assertThat(t.asistenciaConfirmadaPaciente).isFalse();
        verify(publicador).reprogramado(eq(t), any(), eq(LocalTime.of(10, 0)), any());
    }

    @Test
    void confirmarYNoAsistira_conElTokenDelMail() {
        t.tokenConfirmacionAsistencia = "tok";
        assertThatThrownBy(() -> gestion.confirmarAsistencia(10L, "otro")).hasMessageContaining("no es válido");
        assertThatThrownBy(() -> gestion.confirmarAsistencia(10L, " ")).hasMessageContaining("no es válido");
        gestion.confirmarAsistencia(10L, "tok");
        assertThat(t.asistenciaConfirmadaPaciente).isTrue();
        verify(publicador).asistenciaConfirmada(t);

        gestion.noAsistira(10L, "tok");
        assertThat(t.estado).isEqualTo(Turno.CANCELADO);
        verify(publicador).cancelado(eq(t), eq("PACIENTE"), anyString(), anyString());
        gestion.noAsistira(10L, "tok");
        verify(publicador).cancelado(any(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> gestion.confirmarAsistencia(10L, "tok")).hasMessageContaining("ya no está disponible");
    }

    @Test
    void enlacesAsistencia() {
        assertThat(gestion.enlacesAsistencia(10L)).isEmpty();
        t.tokenConfirmacionAsistencia = "tok";
        assertThat(gestion.enlacesAsistencia(10L)).hasValueSatisfying(e -> assertThat(e).containsExactly(
                "https://tranqui.test/api/turnos/10/confirmar-asistencia?token=tok",
                "https://tranqui.test/api/turnos/10/no-asistira?token=tok"));
        verify(interno, never()).enviarDocumento(any());
    }
}
