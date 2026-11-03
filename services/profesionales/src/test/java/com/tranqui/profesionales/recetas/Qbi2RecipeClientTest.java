package com.tranqui.profesionales.recetas;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El cliente HTTP de QBI2 contra un servidor local que imita sus respuestas, y el simulado. */
class Qbi2RecipeClientTest {

    private HttpServer servidor;
    private final List<String> pedidos = new ArrayList<>();
    private final List<String> cuerpos = new ArrayList<>();
    private int status = 200;
    private String respuesta = "{}";
    private Qbi2RecipeClientHttp cliente;

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            pedidos.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI()
                    + " auth=" + intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(status, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
        cliente = new Qbi2RecipeClientHttp();
        ReflectionTestUtils.setField(cliente, "environment", "hml");
        ReflectionTestUtils.setField(cliente, "baseUrlHml", "http://127.0.0.1:" + servidor.getAddress().getPort());
        ReflectionTestUtils.setField(cliente, "baseUrlProd", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(cliente, "clienteAppId", "42");
        ReflectionTestUtils.setField(cliente, "token", "token-de-prueba-largo");
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    @Test
    void buscarDiagnosticos_codificaElTextoYLeeLaRespuesta() {
        respuesta = "{\"diagnosticos\":[{\"iddiagnostico\":1,\"coddiagnostico\":\"F410\",\"descdiagnostico\":\"Ansiedad\",\"otro\":1}]}";
        var r = cliente.buscarDiagnosticos("ansiedad generalizada");
        assertThat(r.getDiagnosticos()).singleElement().satisfies(d -> assertThat(d.getCoddiagnostico()).isEqualTo("F410"));
        assertThat(pedidos).containsExactly("GET /apirecipe/GetDiagnostico?text=ansiedad+generalizada&clienteAppId=42 auth=Bearer token-de-prueba-largo");
    }

    @Test
    void sinClienteAppId_noLoMandaEnLaBusqueda() {
        ReflectionTestUtils.setField(cliente, "clienteAppId", "");
        ReflectionTestUtils.setField(cliente, "token", "corto");
        cliente.buscarDiagnosticos(null);
        cliente.buscarFinanciadores();
        assertThat(pedidos).containsExactly("GET /apirecipe/GetDiagnostico?text= auth=Bearer corto",
                "GET /apirecipe/GetFinanciadores auth=Bearer corto");
    }

    @Test
    void buscarMedicamentosYFinanciadores() {
        respuesta = "{\"medicamentos\":[{\"nombreProducto\":\"Clonazepam\"}],\"pageInfo\":{\"numeroPagina\":2}}";
        var meds = cliente.buscarMedicamentos("clona", 2);
        assertThat(meds.getMedicamentos().get(0).getNombreProducto()).isEqualTo("Clonazepam");
        assertThat(meds.getPageInfo().getNumeroPagina()).isEqualTo(2);

        respuesta = "{\"financiadores\":[{\"idfinanciador\":3,\"planes\":[]}]}";
        assertThat(cliente.buscarFinanciadores().getFinanciadores().get(0).getIdfinanciador()).isEqualTo(3);
        assertThat(pedidos.get(0)).startsWith("GET /apirecipe/GetMedicamento/clona?numeroPagina=2&clienteAppId=42");
        assertThat(pedidos.get(1)).startsWith("GET /apirecipe/GetFinanciadores?clienteAppId=42");
    }

    @Test
    void generarReceta_completaElClienteAppIdYOmiteLosNulos() {
        respuesta = "{\"recetas\":[{\"idReceta\":\"R-1\",\"verificador\":\"V\"}],\"idTransaccion\":\"T\"}";
        var request = Qbi2RecetaDtos.RecetaRequest.builder().diagnostico("F410").build();
        var r = cliente.generarReceta(request);
        assertThat(r.getRecetas().get(0).getIdReceta()).isEqualTo("R-1");
        assertThat(pedidos.get(0)).startsWith("POST /apirecipe/Receta ");
        assertThat(cuerpos.get(0)).contains("\"clienteAppId\":42", "\"diagnostico\":\"F410\"").doesNotContain("null");
    }

    @Test
    void generarReceta_conClienteAppIdNoNumericoLoDejaVacio() {
        ReflectionTestUtils.setField(cliente, "clienteAppId", "abc");
        cliente.generarReceta(Qbi2RecetaDtos.RecetaRequest.builder().build());
        assertThat(cuerpos.get(0)).doesNotContain("clienteAppId");
    }

    @Test
    void anularReceta_mandaDeleteConElHash() {
        respuesta = "{\"codigoRespuesta\":0,\"mensaje\":\"ok\"}";
        var r = cliente.anularReceta("abc/123");
        assertThat(r.getCodigoRespuesta()).isZero();
        assertThat(pedidos.get(0)).startsWith("DELETE /apirecipe/Receta/abc%2F123 ");
        assertThat(cuerpos.get(0)).isEqualTo("{\"clienteAppId\":42}");
    }

    @Test
    void errorDeQbi2_conservaStatusYCuerpo() {
        status = 400;
        respuesta = "{\"errores\":[\"QBI34\"]}";
        assertThatThrownBy(() -> cliente.anularReceta("h"))
                .isInstanceOfSatisfying(Qbi2RecipeException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(400);
                    assertThat(e.getResponseBody()).contains("QBI34");
                });
    }

    @Test
    void respuestaIlegible_fallaConMensajeClaro() {
        respuesta = "no es json";
        assertThatThrownBy(() -> cliente.buscarDiagnosticos("x")).hasMessageContaining("GetDiagnostico");
        assertThatThrownBy(() -> cliente.buscarMedicamentos("x", 1)).hasMessageContaining("GetMedicamento");
        assertThatThrownBy(() -> cliente.buscarFinanciadores()).hasMessageContaining("GetFinanciadores");
        assertThatThrownBy(() -> cliente.generarReceta(Qbi2RecetaDtos.RecetaRequest.builder().build()))
                .hasMessageContaining("generar la receta");
        assertThatThrownBy(() -> cliente.anularReceta("h")).hasMessageContaining("anular la receta");
    }

    @Test
    void sinConexion_fallaConElDestino() {
        ReflectionTestUtils.setField(cliente, "environment", "prod");
        assertThatThrownBy(() -> cliente.buscarFinanciadores()).hasMessageContaining("Error llamando a QBI2 Recipe");
    }

    @Test
    void clienteSimulado_devuelveDatosMarcados() {
        Qbi2RecipeClientMock mock = new Qbi2RecipeClientMock();
        assertThat(mock.buscarDiagnosticos("x").getDiagnosticos()).hasSize(1);
        assertThat(mock.buscarMedicamentos("x", 3).getPageInfo().getNumeroPagina()).isEqualTo(3);
        assertThat(mock.buscarFinanciadores().getFinanciadores()).hasSize(1);
        assertThat(mock.generarReceta(new Qbi2RecetaDtos.RecetaRequest()).getRecetas().get(0).getVerificador()).isEqualTo("MOCK-VERIFICADOR");
        assertThat(mock.anularReceta("h").getMensaje()).contains("[MOCK]");
    }
}
