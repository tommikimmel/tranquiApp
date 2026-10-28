package com.tranqui.pagos.interno;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 9, I3b: las llamadas de régimen van a {@code /internal/identidad/...} con la fuente de identidad, y al monolito
 * con la de siempre (la vuelta atrás es sacar la variable). Contra un servidor HTTP de prueba, sin Spring.
 */
class ClienteInternoFuenteTest {

    private HttpServer servidor;
    private final List<String> pedidos = new CopyOnWriteArrayList<>();

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            pedidos.add(intercambio.getRequestMethod() + " " + ruta);
            byte[] cuerpo = (unObjeto(intercambio.getRequestMethod(), ruta) ? "{}" : "[]").getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    private static boolean unObjeto(String metodo, String ruta) {
        return ruta.matches(".*/(pacientes|profesionales|suscriptores)/\\d+(/identidad)?$")
                || ("POST".equals(metodo) && ruta.endsWith("/pacientes"));
    }

    private ClienteInterno cliente(String fuente) {
        return new ClienteInterno("http://127.0.0.1:" + servidor.getAddress().getPort(), "clave-servicio-test-de-32-bytes-ok", fuente);
    }

    @Test
    void unaFuenteDesconocida_noArranca() {
        assertThatThrownBy(() -> cliente("legado")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRANQUI_FUENTE_IDENTIDAD");
    }

    @Test
    void conIdentidad_losSuscriptoresSalenDeIdentidad() {
        ClienteInterno interno = cliente("identidad");
        assertThat(interno.fuenteIdentidad()).isTrue();
        interno.suscriptor(5L);
        interno.suscriptores(0, 10);
        assertThat(pedidos).containsExactly("GET /internal/identidad/suscriptores/5", "GET /internal/identidad/suscriptores");
    }

    @Test
    void conElMonolito_todoComoHoy() {
        ClienteInterno interno = cliente("monolito");
        assertThat(interno.fuenteIdentidad()).isFalse();
        interno.suscriptor(5L);
        assertThat(pedidos).containsExactly("GET /internal/monolito/suscriptores/5");
        assertThat(cliente(null).fuenteIdentidad()).isFalse();
    }
}
