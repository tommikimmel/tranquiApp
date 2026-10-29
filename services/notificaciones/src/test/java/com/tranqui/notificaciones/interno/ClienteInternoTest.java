package com.tranqui.notificaciones.interno;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las consultas internas contra un servidor de prueba: rutas, token de servicio y validación de lo que vuelve. */
class ClienteInternoTest {

    private HttpServer servidor;
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final Map<String, String> respuestas = new HashMap<>();

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            String ruta = ex.getRequestURI().getPath();
            pedidos.add(ruta + " " + ex.getRequestHeaders().getFirst("Authorization").startsWith("Bearer "));
            byte[] cuerpo = respuestas.getOrDefault(ruta, "null").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    private ClienteInterno cliente() {
        return new ClienteInterno("http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-servicio-test-de-32-bytes-ok");
    }

    @Test
    void sinClaveDeServicio_noArranca() {
        assertThatThrownBy(() -> new ClienteInterno("http://x", " ")).hasMessageContaining("SERVICE_JWT_KEY");
    }

    @Test
    void contactos_descartaLasFilasInvalidas() {
        respuestas.put("/internal/monolito/contactos", "[{\"id\":1,\"email\":\"a@t.com\",\"rol\":\"PACIENTE\"},"
                + "{\"id\":-3,\"email\":\"b@t.com\"},{\"id\":4,\"email\":\"no-es-mail\"}]");
        assertThat(cliente().contactos(0, 10)).extracting(ClienteInterno.ContactoInterno::id).containsExactly(1L);
        assertThat(pedidos).containsExactly("/internal/monolito/contactos true");
    }

    @Test
    void sinCuerpo_listasVacias() {
        assertThat(cliente().contactos(0, 10)).isEmpty();
        assertThat(cliente().notificacionesVigentes()).isEmpty();
    }

    @Test
    void notificacionesVigentes() {
        respuestas.put("/internal/monolito/notificaciones-vigentes",
                "[{\"usuarioId\":2,\"titulo\":\"T\",\"mensaje\":\"m\",\"leido\":false,\"tipo\":\"X\",\"fechaCreacion\":\"2026-10-01T10:00:00\"}]");
        assertThat(cliente().notificacionesVigentes()).singleElement().satisfies(n -> assertThat(n.usuarioId()).isEqualTo(2L));
    }

    @Test
    void enlacesAsistencia_vienenDeTurnosYSonObligatorios() {
        respuestas.put("/internal/turnos/turnos/7/enlaces-asistencia", "{\"confirmarUrl\":\"https://x/si\",\"noAsistiraUrl\":\"https://x/no\"}");
        assertThat(cliente().enlacesAsistencia(7L).confirmarUrl()).isEqualTo("https://x/si");
        assertThatThrownBy(() -> cliente().enlacesAsistencia(8L)).hasMessageContaining("turno 8");
    }
}
