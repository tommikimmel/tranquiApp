package com.tranqui.calendario.agenda;

import com.sun.net.httpserver.HttpServer;
import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.calendario.interno.ClienteInterno;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Las tareas programadas del calendario y su cliente interno de carga inicial. */
class ProgramadorYClienteTest {

    private final CuentasGoogle cuentas = mock(CuentasGoogle.class);
    private final TurnosAgendados turnos = mock(TurnosAgendados.class);
    private final Sincronizacion sincronizacion = mock(Sincronizacion.class);
    private final Canales canales = mock(Canales.class);

    private static CuentasGoogle.Cuenta cuenta(long id) {
        return new CuentasGoogle.Cuenta(id, null, "r", null, null, null, null, null, null, false);
    }

    @Test
    void sincronizarCuentas_unErrorNoCortaElResto() throws Exception {
        when(cuentas.todas()).thenReturn(List.of(cuenta(1), cuenta(2)));
        doThrow(new IllegalStateException("google caído")).when(sincronizacion).incremental(1L);
        new Programador(cuentas, turnos, sincronizacion, canales, true).sincronizarCuentas();
        verify(sincronizacion).incremental(2L);
    }

    @Test
    void conGoogleOCanalesApagados_noHaceNada() {
        Programador p = new Programador(cuentas, turnos, sincronizacion, canales, false);
        p.sincronizarCuentas();
        p.renovarCanales();
        verifyNoInteractions(cuentas, sincronizacion);
    }

    @Test
    void renovarCanales_losQueVencenEn48Horas() {
        when(canales.habilitados()).thenReturn(true);
        when(cuentas.canalesPorVencer(any())).thenReturn(List.of(cuenta(1), cuenta(2)));
        doThrow(new IllegalStateException("x")).when(canales).renovar(1L);
        new Programador(cuentas, turnos, sincronizacion, canales, true).renovarCanales();
        verify(canales).renovar(2L);
    }

    @Test
    void borrarTurnosPasados() {
        when(turnos.borrarPasados()).thenReturn(3, 0);
        Programador p = new Programador(cuentas, turnos, sincronizacion, canales, true);
        p.borrarTurnosPasados();
        p.borrarTurnosPasados();
        verify(turnos, org.mockito.Mockito.times(2)).borrarPasados();
    }

    @Test
    void clienteInterno_rutasYFilasInvalidas() throws Exception {
        List<String> pedidos = new CopyOnWriteArrayList<>();
        Map<String, String> respuestas = Map.of(
                "/internal/monolito/contactos", "[{\"id\":1,\"email\":\"a@t.com\"},{\"id\":0}]",
                "/internal/monolito/calendario/cuentas", "[{\"profesionalId\":2,\"watchActivo\":false}]",
                "/internal/monolito/calendario/eventos-externos", "[{\"id\":3,\"profesionalId\":2,\"todoElDia\":true}]",
                "/internal/monolito/turnos-confirmados", "[{\"turnoId\":4,\"fecha\":\"2026-11-05\"},{\"turnoId\":-4}]");
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            pedidos.add(ex.getRequestURI().getPath());
            byte[] cuerpo = respuestas.getOrDefault(ex.getRequestURI().getPath(), "null").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
        try {
            ClienteInterno c = new ClienteInterno("http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-servicio-test-de-32-bytes-ok");
            assertThat(c.contactos(0, 10)).hasSize(1);
            assertThat(c.cuentas()).hasSize(1);
            assertThat(c.eventosExternos(0, 10)).singleElement().satisfies(e -> assertThat(e.todoElDia()).isTrue());
            assertThat(c.turnosConfirmados()).hasSize(1);
            assertThat(pedidos).hasSize(4);
        } finally {
            servidor.stop(0);
        }
        assertThatThrownBy(() -> new ClienteInterno("http://x", "")).hasMessageContaining("SERVICE_JWT_KEY");
    }
}
