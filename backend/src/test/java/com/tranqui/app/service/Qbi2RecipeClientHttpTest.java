package com.tranqui.app.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// Spins up a tiny JDK-native HTTP server (no new test dependency like WireMock needed) standing
// in for QBI2/Innovamed's real HML endpoint, and points qbi2.recipe.base-url-hml at it for this
// test class only via @DynamicPropertySource. qbi2.recipe.enabled=true here activates the real
// Qbi2RecipeClientHttp bean instead of Qbi2RecipeClientMock (mutually exclusive via
// @ConditionalOnProperty on both classes).
@SpringBootTest(properties = {
        "qbi2.recipe.enabled=true",
        "qbi2.recipe.environment=hml",
        "qbi2.recipe.cliente-app-id=611",
        "qbi2.recipe.token=test-token"
})
class Qbi2RecipeClientHttpTest {

    private static HttpServer server;
    private static volatile String lastPath;
    private static volatile String lastAuthHeader;
    private static volatile int nextStatus = 200;
    private static volatile String nextBody = "{}";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().toString();
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = nextBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(nextStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @DynamicPropertySource
    static void registerBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("qbi2.recipe.base-url-hml", () -> "http://localhost:" + server.getAddress().getPort());
        registry.add("qbi2.recipe.base-url-prod", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @Autowired
    private Qbi2RecipeClient qbi2RecipeClient;

    @Test
    void esLaImplementacionRealHttpCuandoEstaHabilitada() {
        assertInstanceOf(Qbi2RecipeClientHttp.class, qbi2RecipeClient);
    }

    @Test
    void buscarDiagnosticos_parseaLaRespuestaYMandaElToken() {
        nextStatus = 200;
        nextBody = "{\"diagnosticos\":[{\"iddiagnostico\":1,\"coddiagnostico\":\"F411\",\"descdiagnostico\":\"Trastorno de ansiedad\"}]}";

        var resp = qbi2RecipeClient.buscarDiagnosticos("ansiedad");

        assertNotNull(resp.getDiagnosticos());
        assertEquals(1, resp.getDiagnosticos().size());
        assertEquals("F411", resp.getDiagnosticos().get(0).getCoddiagnostico());
        assertTrue(lastPath.contains("GetDiagnostico"));
        assertTrue(lastPath.contains("text=ansiedad"));
        assertEquals("Bearer test-token", lastAuthHeader);
    }

    @Test
    void buscarMedicamentos_parseaListaYPageInfo() {
        nextStatus = 200;
        nextBody = "{\"medicamentos\":[{\"presentacion\":\"comp x 30\",\"nombreProducto\":\"CLONAGIN\",\"nombreDroga\":\"clonazepam\",\"regNo\":\"12345\"}]," +
                "\"pageInfo\":{\"numeroPagina\":1,\"cantidadPaginas\":3,\"cantidadMaxResultadosXPagina\":20,\"tieneMasResultados\":true}}";

        var resp = qbi2RecipeClient.buscarMedicamentos("clonazepam", 1);

        assertEquals(1, resp.getMedicamentos().size());
        assertEquals("CLONAGIN", resp.getMedicamentos().get(0).getNombreProducto());
        assertEquals("12345", resp.getMedicamentos().get(0).getRegNo());
        assertTrue(resp.getPageInfo().getTieneMasResultados());
        assertTrue(lastPath.contains("GetMedicamento/clonazepam"));
    }

    @Test
    void buscarFinanciadores_parseaLaLista() {
        nextStatus = 200;
        nextBody = "{\"financiadores\":[{\"idfinanciador\":1,\"nrofinanciador\":\"001\",\"nombreComercial\":\"OSDE\",\"planes\":[]}]}";

        var resp = qbi2RecipeClient.buscarFinanciadores();

        assertEquals(1, resp.getFinanciadores().size());
        assertEquals("OSDE", resp.getFinanciadores().get(0).getNombreComercial());
    }

    @Test
    void respuestaDeError_lanzaQbi2RecipeExceptionConElStatusYElBody() {
        nextStatus = 400;
        nextBody = "{\"error\":\"QBI2 OPERACION INVALIDA.\",\"mensaje\":\"Medicamento no encontrado\"}";

        Qbi2RecipeException ex = assertThrows(Qbi2RecipeException.class,
                () -> qbi2RecipeClient.buscarMedicamentos("noexiste", 1));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getResponseBody().contains("Medicamento no encontrado"));
    }

    @Test
    void respuestaNoJson_noRompeConNullPointerSinoConExcepcionClara() {
        nextStatus = 200;
        nextBody = "esto no es json";

        assertThrows(RuntimeException.class, () -> qbi2RecipeClient.buscarDiagnosticos("x"));
    }
}
