package com.tranqui.identidad.novedades;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Las llamadas a la API de contactos y broadcasts de Resend, contra un HttpClient falso. */
class ResendAudienciaClientTest {

    private final ResendAudienciaClient cliente = new ResendAudienciaClient();
    private final List<String> pedidos = new ArrayList<>();
    /** Respuestas por "MÉTODO /ruta": status y cuerpo; si hay varias se consumen en orden. */
    private final Map<String, Deque<Object[]>> respuestas = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ReflectionTestUtils.setField(cliente, "apiKey", " re_test ");
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String clave = req.method() + " " + req.uri().getRawPath();
            pedidos.add(clave + " " + req.headers().firstValue("Authorization").orElse(""));
            Deque<Object[]> cola = respuestas.getOrDefault(clave, new ArrayDeque<>());
            Object[] r = cola.size() > 1 ? cola.poll() : cola.peek();
            HttpResponse<String> res = mock(HttpResponse.class);
            when(res.statusCode()).thenReturn(r != null ? (Integer) r[0] : 200);
            when(res.body()).thenReturn(r != null ? (String) r[1] : "");
            return res;
        });
        ReflectionTestUtils.setField(cliente, "http", http);
    }

    private void responder(String clave, int status, String cuerpo) {
        respuestas.computeIfAbsent(clave, k -> new ArrayDeque<>()).add(new Object[]{status, cuerpo});
    }

    @Test
    void sinApiKeyValida_noLlama() {
        ReflectionTestUtils.setField(cliente, "apiKey", "otra");
        assertThat(cliente.configurado()).isFalse();
        assertThatThrownBy(() -> cliente.borrarContacto("a@b.com")).hasMessageContaining("RESEND_API_KEY");
        ReflectionTestUtils.setField(cliente, "apiKey", null);
        assertThat(cliente.configurado()).isFalse();
        assertThat(pedidos).isEmpty();
    }

    @Test
    void segmentos_usaElExistenteOLoCrea() {
        responder("GET /segments", 200, "{\"data\":[{\"id\":\"s1\",\"name\":\"Pacientes\"}]}");
        responder("POST /segments", 200, "{\"id\":\"s2\"}");
        assertThat(cliente.asegurarSegmento("Pacientes")).isEqualTo("s1");
        assertThat(cliente.asegurarSegmento("Profesionales")).isEqualTo("s2");
        assertThat(pedidos.get(0)).isEqualTo("GET /segments Bearer re_test");
    }

    @Test
    void contactos_buscarCrearActualizarYBorrar() {
        responder("GET /contacts/a%40b.com", 200, "{\"id\":\"c1\",\"email\":\"a@b.com\",\"unsubscribed\":true}");
        responder("GET /contacts/x%40b.com", 404, "");
        responder("DELETE /contacts/x%40b.com", 404, "");
        responder("GET /contacts/a%40b.com/segments", 200, "{\"data\":[{\"id\":\"s1\"},{\"id\":\"s2\"}]}");
        responder("DELETE /contacts/a%40b.com/segments/s9", 404, "");

        assertThat(cliente.buscarContacto("a@b.com")).contains(new ResendAudienciaClient.Contacto("c1", "a@b.com", true));
        assertThat(cliente.buscarContacto("x@b.com")).isEmpty();
        cliente.crearContacto("a@b.com", "Ana", null, "s1");
        cliente.actualizarNombre("a@b.com", "Ana", "Paz");
        cliente.actualizarSuscripcion("a@b.com", false);
        assertThat(cliente.segmentosDe("a@b.com")).containsExactlyInAnyOrder("s1", "s2");
        cliente.agregarASegmento("a@b.com", "s3");
        cliente.quitarDeSegmento("a@b.com", "s9");
        cliente.borrarContacto("x@b.com");
        assertThat(pedidos).extracting(p -> p.substring(0, p.indexOf(" Bearer"))).containsExactly(
                "GET /contacts/a%40b.com", "GET /contacts/x%40b.com", "POST /contacts", "PATCH /contacts/a%40b.com",
                "PATCH /contacts/a%40b.com", "GET /contacts/a%40b.com/segments", "POST /contacts/a%40b.com/segments/s3",
                "DELETE /contacts/a%40b.com/segments/s9", "DELETE /contacts/x%40b.com");
    }

    @Test
    void broadcast_devuelveElId() {
        responder("POST /broadcasts", 200, "{\"id\":\"b1\"}");
        assertThat(cliente.enviarBroadcast("s1", "TranquiApp <n@t.com>", "Novedades", "<p>hola</p>", "nov-1")).isEqualTo("b1");
    }

    @Test
    void limiteDeResend_reintentaUnaVez() {
        responder("POST /broadcasts", 429, "");
        responder("POST /broadcasts", 200, "{\"id\":\"b2\"}");
        assertThat(cliente.enviarBroadcast("s1", "f", "a", "h", "n")).isEqualTo("b2");
        assertThat(pedidos).hasSize(2);
    }

    @Test
    void errorDeResend_fallaConElStatus() {
        responder("POST /broadcasts", 422, "{\"message\":\"from inválido\"}");
        assertThatThrownBy(() -> cliente.enviarBroadcast("s1", "f", "a", "h", "n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("422");
    }

    @Test
    void respuestaIlegible_fallaConElDestino() {
        responder("GET /segments", 200, "no es json");
        assertThatThrownBy(() -> cliente.asegurarSegmento("x")).hasMessageContaining("Error llamando a Resend: GET /segments");
    }
}
