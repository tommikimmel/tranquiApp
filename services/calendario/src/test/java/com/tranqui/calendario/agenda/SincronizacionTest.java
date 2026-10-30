package com.tranqui.calendario.agenda;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.calendario.google.GoogleCalendarApi;
import com.tranqui.calendario.google.TokensGoogle;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La caché de eventos propios del profesional, como GoogleCalendarSyncService del monolito. */
class SincronizacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CuentasGoogle cuentas = mock(CuentasGoogle.class);
    private final EventosExternos externos = mock(EventosExternos.class);
    private final TurnosAgendados turnos = mock(TurnosAgendados.class);
    private final TokensGoogle tokens = mock(TokensGoogle.class);
    private final GoogleCalendarApi google = mock(GoogleCalendarApi.class);
    private final AgendaTurnos agenda = mock(AgendaTurnos.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private Sincronizacion sincronizacion;

    @BeforeEach
    void preparar() {
        sincronizacion = new Sincronizacion(cuentas, externos, turnos, tokens, google, agenda, outbox, true);
        when(externos.guardar(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new EventosExternos.Guardado(5L, 0));
    }

    private static Event evento(String id, LocalDateTime inicio, LocalDateTime fin) {
        return new Event().setId(id).setSummary("Dentista").setStatus("confirmed")
                .setStart(new EventDateTime().setDateTime(new DateTime(inicio.atZone(ZONA).toInstant().toEpochMilli())))
                .setEnd(new EventDateTime().setDateTime(new DateTime(fin.atZone(ZONA).toInstant().toEpochMilli())));
    }

    @Test
    void eventoPropio_seGuardaYSePublicaComoHorarioBloqueado() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 3, 9, 0);
        Event e = evento("ext-1", inicio, inicio.plusHours(1)).setTransparency("transparent");

        sincronizacion.aplicarCambio(2L, e);

        verify(externos).guardar(2L, "ext-1", "Dentista", inicio, inicio.plusHours(1), false, true, "confirmed");
        verify(outbox).publicar(eq(Topicos.CALENDARIO_HORARIO_BLOQUEADO_V1), eq("evento_externo"), eq("5"), eq(1), any());
    }

    @Test
    void eventoDeUnTurnoDeLaApp_oCancelado_seSacaDeLaCache() {
        when(turnos.esEventoDeLaApp(2L, "g-turno")).thenReturn(true);
        sincronizacion.aplicarCambio(2L, evento("g-turno", LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        sincronizacion.aplicarCambio(2L, evento("ext-2", LocalDateTime.now(), LocalDateTime.now().plusHours(1)).setStatus("cancelled"));

        verify(externos).borrarPorGoogleId("g-turno");
        verify(externos).borrarPorGoogleId("ext-2");
        verify(externos, never()).guardar(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void syncTokenVencido_haceLaSincronizacionCompleta() throws Exception {
        CuentasGoogle.Cuenta cuenta = new CuentasGoogle.Cuenta(2L, "a", "r", null, null, "sync-viejo", null, null, null, false);
        when(cuentas.buscar(2L)).thenReturn(Optional.of(cuenta));
        when(tokens.accessToken(cuenta)).thenReturn("token");
        when(google.cambios("token", "primary", "sync-viejo", null))
                .thenThrow(new GoogleCalendarApi.SyncTokenVencidoException("410"));
        when(google.ventana(eq("token"), eq("primary"), any(), any(), any()))
                .thenReturn(new GoogleCalendarApi.Pagina(List.of(), null, "sync-nuevo"));

        sincronizacion.incremental(2L);

        verify(externos).borrarDeProfesional(2L);
        verify(cuentas).guardarSyncToken(2L, "sync-nuevo");
        verify(agenda).exportarPendientes(2L);
    }

    @Test
    void googleDeshabilitado_noLlamaANada() throws Exception {
        new Sincronizacion(cuentas, externos, turnos, tokens, google, agenda, outbox, false).incremental(2L);
        verify(google, never()).cambios(anyString(), anyString(), anyString(), any());
    }

    private static CuentasGoogle.Cuenta cuenta(String syncToken) {
        return new CuentasGoogle.Cuenta(7L, "a", "r", null, null, syncToken, null, null, null, false);
    }

    @Test
    void incremental_recorreLasPaginasYGuardaElSyncToken() throws Exception {
        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta("sync-1")));
        when(tokens.accessToken(any())).thenReturn("acc");
        when(google.cambios("acc", "primary", "sync-1", null)).thenReturn(new GoogleCalendarApi.Pagina(null, "p2", null));
        when(google.cambios("acc", "primary", "sync-1", "p2")).thenReturn(new GoogleCalendarApi.Pagina(
                List.of(evento("ext-9", LocalDateTime.of(2026, 11, 3, 9, 0), LocalDateTime.of(2026, 11, 3, 10, 0))), null, "sync-2"));
        sincronizacion.incremental(7L);
        verify(cuentas).guardarSyncToken(7L, "sync-2");
        verify(agenda).exportarPendientes(7L);
        verify(outbox).publicar(eq(Topicos.CALENDARIO_HORARIO_BLOQUEADO_V1), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void incremental_syncVencido_haceLaCompleta() throws Exception {
        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta("sync-1")));
        when(tokens.accessToken(any())).thenReturn("acc");
        when(google.cambios(any(), any(), any(), any())).thenThrow(new GoogleCalendarApi.SyncTokenVencidoException("410"));
        when(google.ventana(eq("acc"), eq("primary"), any(), any(), any())).thenReturn(new GoogleCalendarApi.Pagina(List.of(), null, "sync-3"));
        sincronizacion.incremental(7L);
        verify(externos).borrarDeProfesional(7L);
        verify(cuentas).guardarSyncToken(7L, "sync-3");
    }

    @Test
    void incremental_sinSyncTokenOErrores() throws Exception {
        when(tokens.accessToken(any())).thenReturn("acc");
        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta(null)));
        when(google.ventana(any(), any(), any(), any(), any())).thenThrow(new java.io.IOException("403"));
        sincronizacion.incremental(7L);
        verify(cuentas, never()).guardarSyncToken(any(), any());

        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta("sync-1")));
        when(google.cambios(any(), any(), any(), any())).thenThrow(new java.io.IOException("500"));
        sincronizacion.incremental(7L);
        verify(agenda, never()).exportarPendientes(any());
    }

    @Test
    void sinCuentaSinTokenODeshabilitado_noSincroniza() throws Exception {
        when(cuentas.buscar(8L)).thenReturn(Optional.empty());
        sincronizacion.incremental(8L);
        sincronizacion.completa(8L);
        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta("sync-1")));
        when(tokens.accessToken(any())).thenReturn(null);
        sincronizacion.incremental(7L);
        sincronizacion.completa(7L);
        new Sincronizacion(cuentas, externos, turnos, tokens, google, agenda, outbox, false).incremental(7L);
        verify(google, never()).cambios(any(), any(), any(), any());
        verify(google, never()).ventana(any(), any(), any(), any(), any());
    }

    @Test
    void completa_aPedido() throws Exception {
        when(cuentas.buscar(7L)).thenReturn(Optional.of(cuenta("sync-1")));
        when(tokens.accessToken(any())).thenReturn("acc");
        when(google.ventana(any(), any(), any(), any(), any())).thenReturn(new GoogleCalendarApi.Pagina(List.of(), null, null));
        sincronizacion.completa(7L);
        verify(cuentas).guardarSyncToken(7L, null);
    }
}
