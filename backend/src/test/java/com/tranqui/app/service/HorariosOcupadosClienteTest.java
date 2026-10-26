package com.tranqui.app.service;

import com.sun.net.httpserver.HttpServer;
import com.tranqui.app.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Fase 3: con calendario.externo, los horarios ocupados se piden al servicio de calendario por el
 * gateway (/internal/calendario/...), con JWT de servicio; si no responde, la agenda sale sin filtro.
 */
class HorariosOcupadosClienteTest {

    private static final String CLAVE = "clave-servicio-de-test-de-32-bytes-ok";

    private HttpServer servidor;
    private final AtomicReference<String> ultimaRuta = new AtomicReference<>();
    private final AtomicReference<String> ultimoAuth = new AtomicReference<>();
    private volatile int estado = 200;
    private volatile String cuerpo = "[]";

    @BeforeEach
    void arrancar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            ultimaRuta.set(intercambio.getRequestURI().toString());
            ultimoAuth.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private HorariosOcupadosCliente cliente(GoogleCalendarService google) {
        return new HorariosOcupadosCliente(google, true, "http://127.0.0.1:" + servidor.getAddress().getPort(), CLAVE);
    }

    private static Usuario medico() {
        Usuario u = new Usuario();
        u.setId(16L);
        return u;
    }

    @Test
    void externo_leeLosIntervalosDelCalendarioSinLlamarAGoogle() {
        cuerpo = "[{\"inicio\":\"10:00:00\",\"fin\":\"11:30:00\",\"todoElDia\":false}]";
        GoogleCalendarService google = mock(GoogleCalendarService.class);

        List<HorariosOcupadosCliente.Intervalo> ocupados = cliente(google).ocupados(medico(), LocalDate.of(2026, 10, 22));

        assertThat(ocupados).containsExactly(new HorariosOcupadosCliente.Intervalo(LocalTime.of(10, 0), LocalTime.of(11, 30), false));
        assertThat(ultimaRuta.get()).isEqualTo("/internal/calendario/profesionales/16/ocupado?fecha=2026-10-22");
        assertThat(ultimoAuth.get()).startsWith("Bearer ");
        verify(google, never()).obtenerEventosDelDia(any(), any());
    }

    @Test
    void calendarioCaido_listaVacia() {
        estado = 503;
        cuerpo = "{\"error\":\"servicio_no_disponible\"}";

        assertThat(cliente(mock(GoogleCalendarService.class)).ocupados(medico(), LocalDate.now())).isEmpty();
    }

    @Test
    void sinFlag_usaGoogleComoAntes() {
        GoogleCalendarService google = mock(GoogleCalendarService.class);
        HorariosOcupadosCliente local = new HorariosOcupadosCliente(google, false, "http://127.0.0.1:1", CLAVE);

        assertThat(local.ocupados(medico(), LocalDate.now())).isEmpty();
        verify(google).obtenerEventosDelDia(any(), any());
        assertThat(ultimaRuta.get()).isNull();
    }
}
