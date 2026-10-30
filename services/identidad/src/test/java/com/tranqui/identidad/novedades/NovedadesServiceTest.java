package com.tranqui.identidad.novedades;

import com.tranqui.identidad.datos.NovedadesEnvio;
import com.tranqui.identidad.datos.NovedadesEnvios;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

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

/** El envío por broadcasts de Resend, la sincronización de contactos y la preferencia de Mi Cuenta. */
class NovedadesServiceTest {

    private final Usuarios usuarios = mock(Usuarios.class);
    private final NovedadesEnvios envios = mock(NovedadesEnvios.class);
    private final ResendAudienciaClient resend = mock(ResendAudienciaClient.class);
    private final MailNovedades mail = mock(MailNovedades.class);
    private final List<NovedadesEnvio> guardados = new ArrayList<>();
    private NovedadesService service;

    /** Corre la tarea en el mismo hilo, para ver el resultado del envío sin esperar. */
    private static final class MismoHilo extends AbstractExecutorService {
        public void execute(Runnable r) { r.run(); }
        public void shutdown() {}
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    @BeforeEach
    void setUp() {
        service = new NovedadesService(usuarios, envios, resend, mail, new MismoHilo());
        ReflectionTestUtils.setField(service, "transporte", "resend");
        ReflectionTestUtils.setField(service, "segmentoPacientes", "Pacientes");
        ReflectionTestUtils.setField(service, "segmentoProfesionales", "Profesionales");
        when(resend.configurado()).thenReturn(true);
        when(resend.asegurarSegmento("Pacientes")).thenReturn("sP");
        when(resend.asegurarSegmento("Profesionales")).thenReturn("sR");
        when(mail.remitente()).thenReturn("Tranqui App <n@t>");
        // la tabla de envíos: lo último insertado o actualizado
        when(envios.buscarPorId("nov-1")).thenAnswer(inv ->
                Optional.ofNullable(guardados.isEmpty() ? null : guardados.get(guardados.size() - 1)));
        org.mockito.Mockito.doAnswer(inv -> guardados.add(inv.getArgument(0))).when(envios).actualizar(any());
        org.mockito.Mockito.doAnswer(inv -> guardados.add(inv.getArgument(0))).when(envios).insertar(any());
        when(usuarios.actualizar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Usuario usuario(long id, String rol, boolean recibe) {
        return Usuario.builder().id(id).version(0).email("u" + id + "@t.com").rol(rol).emailVerificado(true)
                .cuentaEliminada(false).recibirNovedades(recibe).nombre("N").apellido("A").build();
    }

    private static NovedadesContenido contenido(List<String> pacientes, List<String> profesionales) {
        return new NovedadesContenido("nov-1", "Novedades", List.of(), pacientes, profesionales);
    }

    private NovedadesEnvio ultimo() {
        return guardados.get(guardados.size() - 1);
    }

    @Test
    void validaElContenido() {
        assertThatThrownBy(() -> service.enviar(null)).hasMessageContaining("id del envío");
        assertThatThrownBy(() -> service.enviar(new NovedadesContenido("a b", "T", List.of("x"), null, null))).hasMessageContaining("id");
        assertThatThrownBy(() -> service.enviar(new NovedadesContenido("nov-1", " ", List.of("x"), null, null))).hasMessageContaining("título");
        assertThatThrownBy(() -> service.enviar(new NovedadesContenido("nov-1", "T", List.of(" "), null, null))).hasMessageContaining("viñeta");
    }

    @Test
    void broadcast_sincronizaContactosYMandaUnoPorPublico() {
        Usuario nuevo = usuario(1, "PACIENTE", true);
        Usuario dadoDeBaja = usuario(2, "PSIQUIATRA", true);
        Usuario sinNovedades = usuario(3, "PACIENTE", false);
        Usuario falla = usuario(4, "PACIENTE", true);
        Usuario noVerificado = usuario(5, "PACIENTE", true).toBuilder().emailVerificado(false).build();
        when(usuarios.buscarPorRol("PACIENTE")).thenReturn(List.of(nuevo, sinNovedades, falla, noVerificado));
        when(usuarios.buscarPorRol("PSIQUIATRA")).thenReturn(List.of(dadoDeBaja));
        when(resend.buscarContacto("u1@t.com")).thenReturn(Optional.empty());
        when(resend.buscarContacto("u3@t.com")).thenReturn(Optional.empty());
        when(resend.buscarContacto("u2@t.com")).thenReturn(Optional.of(new ResendAudienciaClient.Contacto("c2", "u2@t.com", true)));
        when(resend.segmentosDe("u2@t.com")).thenReturn(Set.of("sP"));
        when(resend.buscarContacto("u4@t.com")).thenThrow(new IllegalStateException("429"));
        when(resend.enviarBroadcast(anyString(), anyString(), anyString(), any(), anyString())).thenReturn("b1", "b2");

        NovedadesService.Resultado r = service.enviar(contenido(List.of("Agenda"), List.of("Recetas")));

        assertThat(r.estado()).isEqualTo(NovedadesService.ENVIANDO);
        verify(envios).insertar(any());
        verify(resend).crearContacto("u1@t.com", "N", "A", "sP");
        verify(resend, never()).crearContacto(eq("u3@t.com"), any(), any(), any());
        verify(usuarios).actualizar(any());
        verify(resend).agregarASegmento("u2@t.com", "sR");
        verify(resend).quitarDeSegmento("u2@t.com", "sP");
        verify(resend).enviarBroadcast(eq("sP"), anyString(), eq("Novedades"), any(), eq("nov-1 - pacientes"));
        verify(resend).enviarBroadcast(eq("sR"), anyString(), eq("Novedades"), any(), eq("nov-1 - profesionales"));
        assertThat(ultimo().estado()).isEqualTo(NovedadesService.ENVIADO);
        assertThat(ultimo().detalle()).contains("1 creados", "1 bajas", "1 errores", "broadcast pacientes: b1");
        assertThat(ultimo().enviadoPacientes()).isTrue();
        assertThat(ultimo().enviadoProfesionales()).isTrue();
    }

    @Test
    void broadcast_siFallaElSegundoPublico_quedaEnErrorConElPrimeroMarcado() {
        when(usuarios.buscarPorRol(anyString())).thenReturn(List.of());
        when(resend.enviarBroadcast(eq("sP"), anyString(), anyString(), any(), anyString())).thenReturn("b1");
        when(resend.enviarBroadcast(eq("sR"), anyString(), anyString(), any(), anyString())).thenThrow(new IllegalStateException("caído"));
        service.enviar(contenido(List.of("Agenda"), List.of("Recetas")));
        assertThat(ultimo().estado()).isEqualTo(NovedadesService.ERROR);
        assertThat(ultimo().detalle()).contains("caído");
        assertThat(ultimo().enviadoPacientes()).isTrue();
        assertThat(ultimo().enviadoProfesionales()).isFalse();
    }

    @Test
    void reintento_noRepiteElPublicoYaEnviado() {
        NovedadesEnvio conError = new NovedadesEnvio("nov-1", "Novedades", NovedadesService.ERROR, "Error", LocalDateTime.now(), null, true, false);
        guardados.add(conError);
        when(usuarios.buscarPorRol(anyString())).thenReturn(List.of());
        when(resend.enviarBroadcast(anyString(), anyString(), anyString(), any(), anyString())).thenReturn("b2");
        service.enviar(contenido(List.of("Agenda"), List.of("Recetas")));
        verify(resend, never()).enviarBroadcast(eq("sP"), anyString(), anyString(), any(), anyString());
        verify(resend).enviarBroadcast(eq("sR"), anyString(), anyString(), any(), anyString());
    }

    @Test
    void smtp_mandaUnoPorUsuarioQueAcepta() {
        ReflectionTestUtils.setField(service, "transporte", "smtp");
        when(usuarios.buscarPorRol("PACIENTE")).thenReturn(List.of(usuario(1, "PACIENTE", true), usuario(3, "PACIENTE", false)));
        when(usuarios.buscarPorRol("PSIQUIATRA")).thenReturn(List.of(usuario(2, "PSIQUIATRA", true)));
        service.enviar(contenido(List.of("Agenda"), List.of()));
        verify(mail).enviarHtml(eq("u1@t.com"), eq("Novedades"), any());
        verify(mail, never()).enviarHtml(eq("u2@t.com"), any(), any());
        assertThat(ultimo().detalle()).isEqualTo("envío individual: 1 pacientes, 0 profesionales");
    }

    @Test
    void recibeNovedades_traeLaBajaDeResend() {
        Usuario u = usuario(1, "PACIENTE", true);
        when(resend.buscarContacto("u1@t.com")).thenReturn(Optional.of(new ResendAudienciaClient.Contacto("c", "u1@t.com", true)));
        assertThat(service.recibeNovedades(u)).isFalse();
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).actualizar(captor.capture());
        assertThat(captor.getValue().isRecibirNovedades()).isFalse();

        when(resend.buscarContacto("u1@t.com")).thenThrow(new IllegalStateException("caído"));
        assertThat(service.recibeNovedades(u)).isTrue();
    }

    @Test
    void cambiarSuscripcion_actualizaOCreaElContacto() {
        when(resend.buscarContacto("u1@t.com")).thenReturn(Optional.of(new ResendAudienciaClient.Contacto("c", "u1@t.com", false)));
        service.cambiarSuscripcion(usuario(1, "PACIENTE", true), false);
        verify(resend).actualizarSuscripcion("u1@t.com", false);

        when(resend.buscarContacto("u2@t.com")).thenReturn(Optional.empty());
        service.cambiarSuscripcion(usuario(2, "PSIQUIATRA", false), true);
        verify(resend).crearContacto("u2@t.com", "N", "A", "sR");

        service.cambiarSuscripcion(usuario(3, "ADMIN", false), true);
        verify(resend, never()).crearContacto(eq("u3@t.com"), any(), any(), any());
    }

    @Test
    void eliminarContacto_noFallaSiResendFalla() {
        doThrow(new IllegalStateException("caído")).when(resend).borrarContacto("a@b.com");
        service.eliminarContacto("a@b.com");
        service.eliminarContacto(null);
        verify(resend).borrarContacto("a@b.com");

        ReflectionTestUtils.setField(service, "transporte", "smtp");
        service.eliminarContacto("c@d.com");
        verify(resend, never()).borrarContacto("c@d.com");
    }
}
