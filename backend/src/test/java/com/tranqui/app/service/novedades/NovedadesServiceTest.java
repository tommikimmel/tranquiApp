package com.tranqui.app.service.novedades;

import com.tranqui.app.model.NovedadesEnvio;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NovedadesEnvioRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NovedadesServiceTest {

    // Ejecuta el envío en el mismo hilo, para poder verificarlo sin esperas.
    static class EjecutorDirecto extends AbstractExecutorService {
        public void execute(Runnable r) { r.run(); }
        public void shutdown() {}
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    private UsuarioRepository usuarios;
    private NovedadesEnvioRepository envios;
    private ResendAudienciaClient resend;
    private ResendEmailService mail;
    private NovedadesService service;
    private final Map<String, NovedadesEnvio> tabla = new HashMap<>();

    private Usuario paciente, pacienteSinVerificar, pacienteDadoDeBaja, profesional, eliminado, admin;

    private static Usuario usuario(long id, String email, Rol rol, boolean verificado, boolean recibe, boolean eliminadoFlag) {
        return Usuario.builder().id(id).email(email).nombre("N" + id).rol(rol).emailVerificado(verificado)
                .recibirNovedades(recibe).cuentaEliminada(eliminadoFlag).build();
    }

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        envios = mock(NovedadesEnvioRepository.class);
        resend = mock(ResendAudienciaClient.class);
        mail = mock(ResendEmailService.class);
        service = new NovedadesService(usuarios, envios, resend, mail, new EjecutorDirecto());
        ReflectionTestUtils.setField(service, "segmentoPacientes", "Pacientes");
        ReflectionTestUtils.setField(service, "segmentoProfesionales", "Profesionales");
        ReflectionTestUtils.setField(service, "fromEmail", "soporte@tranqui.test");

        paciente = usuario(1, "paciente@test.com", Rol.PACIENTE, true, true, false);
        pacienteSinVerificar = usuario(2, "sinverificar@test.com", Rol.PACIENTE, false, true, false);
        pacienteDadoDeBaja = usuario(3, "baja@test.com", Rol.PACIENTE, true, false, false);
        profesional = usuario(4, "pro@test.com", Rol.PSIQUIATRA, true, true, false);
        eliminado = usuario(5, "deleted-5@tranquiapp.local", Rol.PACIENTE, true, true, true);
        admin = usuario(6, "admin@test.com", Rol.ADMIN, true, true, false);
        when(usuarios.findByRol(Rol.PACIENTE)).thenReturn(List.of(paciente, pacienteSinVerificar, pacienteDadoDeBaja, eliminado));
        when(usuarios.findByRol(Rol.PSIQUIATRA)).thenReturn(List.of(profesional));
        when(usuarios.findByRol(Rol.ADMIN)).thenReturn(List.of(admin));

        when(envios.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(tabla.get(inv.<String>getArgument(0))));
        when(envios.save(any())).thenAnswer(inv -> { NovedadesEnvio e = inv.getArgument(0); tabla.put(e.getId(), e); return e; });
        when(mail.htmlNovedades(anyString(), anyList(), anyString())).thenAnswer(inv -> "html:" + inv.getArgument(1));
        when(mail.urlMiCuenta()).thenReturn("http://localhost:5173/mi-cuenta");
    }

    private NovedadesContenido contenido(String id) {
        return new NovedadesContenido(id, "Mejoras en Tranqui", List.of("Todo más rápido"),
                List.of("Link de Meet en Mis Turnos"), List.of("Ingresos del mes"));
    }

    // --- Modo individual (entorno local / sin API key) ---

    @Test
    void individual_mandaSoloADestinatariosValidosConLaSeccionDeSuRol() {
        ReflectionTestUtils.setField(service, "transporte", "smtp");

        service.enviar(contenido("mant-1"));

        verify(mail).enviarHtml(eq("paciente@test.com"), anyString(), eq("html:[Todo más rápido, Link de Meet en Mis Turnos]"));
        verify(mail).enviarHtml(eq("pro@test.com"), anyString(), eq("html:[Todo más rápido, Ingresos del mes]"));
        verify(mail, times(2)).enviarHtml(anyString(), anyString(), anyString());
        assertEquals(NovedadesEnvio.Estado.ENVIADO, tabla.get("mant-1").getEstado());
        verifyNoInteractions(resend);
    }

    @Test
    void elMismoIdNoSeEnviaDosVeces() {
        ReflectionTestUtils.setField(service, "transporte", "smtp");

        service.enviar(contenido("mant-2"));
        NovedadesService.Resultado segundo = service.enviar(contenido("mant-2"));

        assertTrue(segundo.yaExistia());
        verify(mail, times(2)).enviarHtml(anyString(), anyString(), anyString());
    }

    @Test
    void sinSeccionParaUnRolEseRolSoloRecibeLoGeneral_yVacioNoRecibe() {
        ReflectionTestUtils.setField(service, "transporte", "smtp");

        service.enviar(new NovedadesContenido("mant-3", "Solo pacientes", List.of(), List.of("Algo para pacientes"), List.of()));

        verify(mail).enviarHtml(eq("paciente@test.com"), anyString(), anyString());
        verify(mail, never()).enviarHtml(eq("pro@test.com"), anyString(), anyString());
    }

    @Test
    void pruebaVaSoloALosAdministradores() {
        int enviados = service.enviarPrueba(contenido("mant-4"));

        assertEquals(2, enviados); // una versión por público
        verify(mail, times(2)).enviarHtml(eq("admin@test.com"), startsWith("[Prueba"), anyString());
        verify(mail, never()).enviarHtml(eq("paciente@test.com"), anyString(), anyString());
        assertTrue(tabla.isEmpty());
    }

    @Test
    void validaElContenido() {
        assertThrows(IllegalArgumentException.class, () -> service.enviar(new NovedadesContenido("x", "T", List.of("a"), null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.enviar(new NovedadesContenido("mant-5", " ", List.of("a"), null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.enviar(new NovedadesContenido("mant-5", "T", List.of(" "), null, null)));
    }

    // --- Modo Broadcast (producción) ---

    private void modoBroadcast() {
        ReflectionTestUtils.setField(service, "transporte", "resend");
        when(resend.configurado()).thenReturn(true);
        when(resend.asegurarSegmento("Pacientes")).thenReturn("segP");
        when(resend.asegurarSegmento("Profesionales")).thenReturn("segR");
        when(resend.enviarBroadcast(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("bc-1");
    }

    @Test
    void broadcast_sincronizaContactosYEnviaUnoPorSegmento() {
        modoBroadcast();
        when(resend.buscarContacto(anyString())).thenReturn(Optional.empty());

        service.enviar(contenido("mant-6"));

        // Crea solo a quienes aceptan novedades, en el segmento de su rol.
        verify(resend).crearContacto("paciente@test.com", "N1", null, "segP");
        verify(resend).crearContacto("pro@test.com", "N4", null, "segR");
        verify(resend, never()).crearContacto(eq("baja@test.com"), any(), any(), any());
        verify(resend, never()).crearContacto(eq("sinverificar@test.com"), any(), any(), any());
        verify(resend, never()).crearContacto(eq("deleted-5@tranquiapp.local"), any(), any(), any());
        // Un broadcast por segmento con su contenido y el link de baja de Resend.
        verify(mail).htmlNovedades(eq("Mejoras en Tranqui"), eq(List.of("Todo más rápido", "Link de Meet en Mis Turnos")), eq("{{{RESEND_UNSUBSCRIBE_URL}}}"));
        verify(resend).enviarBroadcast(eq("segP"), anyString(), eq("Mejoras en Tranqui"), anyString(), anyString());
        verify(resend).enviarBroadcast(eq("segR"), anyString(), eq("Mejoras en Tranqui"), anyString(), anyString());
        verify(mail, never()).enviarHtml(anyString(), anyString(), anyString());
        assertEquals(NovedadesEnvio.Estado.ENVIADO, tabla.get("mant-6").getEstado());
    }

    @Test
    void broadcast_nuncaReactivaAQuienSeDioDeBajaYTraeLaBaja() {
        modoBroadcast();
        when(resend.buscarContacto("paciente@test.com"))
                .thenReturn(Optional.of(new ResendAudienciaClient.Contacto("c1", "paciente@test.com", true)));
        when(resend.buscarContacto("pro@test.com")).thenReturn(Optional.empty());
        when(resend.segmentosDe("paciente@test.com")).thenReturn(Set.of("segR"));

        service.enviar(contenido("mant-7"));

        assertFalse(paciente.isRecibirNovedades());
        verify(resend, never()).actualizarSuscripcion(anyString(), anyBoolean());
        // Corrige el segmento: estaba en profesionales siendo paciente.
        verify(resend).agregarASegmento("paciente@test.com", "segP");
        verify(resend).quitarDeSegmento("paciente@test.com", "segR");
    }

    @Test
    void broadcast_unErrorQuedaRegistradoYAlReintentarSoloMandaLoQueFalto() {
        modoBroadcast();
        when(resend.buscarContacto(anyString())).thenReturn(Optional.empty());
        when(resend.enviarBroadcast(eq("segR"), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Resend caído"))
                .thenReturn("bc-2");

        service.enviar(contenido("mant-8"));
        assertEquals(NovedadesEnvio.Estado.ERROR, tabla.get("mant-8").getEstado());
        assertTrue(tabla.get("mant-8").isEnviadoPacientes());

        service.enviar(contenido("mant-8"));
        assertEquals(NovedadesEnvio.Estado.ENVIADO, tabla.get("mant-8").getEstado());
        verify(resend, times(1)).enviarBroadcast(eq("segP"), anyString(), anyString(), anyString(), anyString());
        verify(resend, times(2)).enviarBroadcast(eq("segR"), anyString(), anyString(), anyString(), anyString());
    }

    // --- Preferencia del usuario ---

    @Test
    void cambiarSuscripcion_actualizaResendSiElContactoExiste() {
        modoBroadcast();
        when(resend.buscarContacto("paciente@test.com"))
                .thenReturn(Optional.of(new ResendAudienciaClient.Contacto("c1", "paciente@test.com", false)));

        service.cambiarSuscripcion(paciente, false);

        assertFalse(paciente.isRecibirNovedades());
        verify(resend).actualizarSuscripcion("paciente@test.com", false);
    }

    @Test
    void eliminarContacto_noFallaSiResendFalla() {
        modoBroadcast();
        doThrow(new IllegalStateException("caído")).when(resend).borrarContacto(anyString());
        assertDoesNotThrow(() -> service.eliminarContacto("paciente@test.com"));
    }
}
