package com.tranqui.chat.interno;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las consultas de carga inicial del chat: rutas, token de servicio y filas inválidas que se saltean. */
class ClienteInternoTest {

    @Test
    void rutasYValidacion() throws Exception {
        List<String> pedidos = new CopyOnWriteArrayList<>();
        Map<String, String> respuestas = Map.of(
                "/internal/monolito/contactos", "[{\"id\":1,\"email\":\"a@t.com\"},{\"id\":2,\"email\":\"no-es-mail\"}]",
                "/internal/monolito/mensajes", "[{\"id\":5,\"remitenteId\":1,\"destinatarioId\":2,\"contenido\":\"hola\",\"leido\":false},"
                        + "{\"id\":-1}]",
                "/internal/monolito/turnos-confirmados", "[{\"turnoId\":9,\"pacienteId\":1,\"profesionalId\":2}]");
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            pedidos.add(ex.getRequestURI().getPath() + " " + ex.getRequestHeaders().getFirst("Authorization").startsWith("Bearer "));
            byte[] cuerpo = respuestas.getOrDefault(ex.getRequestURI().getPath(), "null").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
        try {
            ClienteInterno c = new ClienteInterno("http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-servicio-test-de-32-bytes-ok");
            assertThat(c.contactos(0, 10)).extracting(ClienteInterno.Contacto::id).containsExactly(1L);
            assertThat(c.mensajes(0, 10)).extracting(ClienteInterno.MensajeMonolito::id).containsExactly(5L);
            assertThat(c.turnosConfirmados()).hasSize(1);
            assertThat(pedidos).containsExactly("/internal/monolito/contactos true", "/internal/monolito/mensajes true",
                    "/internal/monolito/turnos-confirmados true");
        } finally {
            servidor.stop(0);
        }
        assertThatThrownBy(() -> new ClienteInterno("http://x", " ")).hasMessageContaining("SERVICE_JWT_KEY");
    }

    @Test
    void sinCuerpo_listasVacias() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            byte[] cuerpo = "null".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
        try {
            ClienteInterno c = new ClienteInterno("http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-servicio-test-de-32-bytes-ok");
            assertThat(c.contactos(0, 10)).isEmpty();
            assertThat(c.mensajes(0, 10)).isEmpty();
            assertThat(c.turnosConfirmados()).isEmpty();
        } finally {
            servidor.stop(0);
        }
    }
}
