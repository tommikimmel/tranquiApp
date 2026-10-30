package com.tranqui.app.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ResincronizacionSuscripcionesTest {

    private static final String CLAVE = "clave-servicio-de-test-de-32-bytes-ok";

    private HttpServer servidor;
    private final List<String> rutasLlamadas = new ArrayList<>();
    private final AtomicReference<String> ultimoAuth = new AtomicReference<>();
    private volatile int estado = 200;
    private final AtomicInteger contadorLlamadas = new AtomicInteger();

    @Mock
    private CopiaSuscripciones copia;

    @BeforeEach
    void arrancar() throws Exception {
        rutasLlamadas.clear();
        contadorLlamadas.set(0);
        estado = 200;
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String getBaseUrl() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void siFlagEsFalse_noHaceNada() {
        ResincronizacionSuscripciones resync = new ResincronizacionSuscripciones(
                copia, false, getBaseUrl(), CLAVE
        );

        resync.resincronizar();

        assertThat(rutasLlamadas).isEmpty();
        verifyNoInteractions(copia);
    }

    @Test
    void conFlagTrue_traePaginaUnicaYHazeUpsert() {
        String json = """
                [
                  {
                    "professionalId": 10,
                    "estado": "ACTIVE",
                    "planCode": "PRO",
                    "acceso": true,
                    "puedeRecetar": true,
                    "periodoHasta": "2026-12-31T23:59:59",
                    "graciaHasta": "2027-01-07T23:59:59",
                    "version": 3
                  }
                ]
                """;

        servidor.createContext("/", intercambio -> {
            rutasLlamadas.add(intercambio.getRequestURI().toString());
            ultimoAuth.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });

        ResincronizacionSuscripciones resync = new ResincronizacionSuscripciones(
                copia, true, getBaseUrl(), CLAVE
        );

        resync.resincronizar();

        assertThat(rutasLlamadas).containsExactly("/internal/suscripciones/estado?pagina=0&tamanio=200");
        assertThat(ultimoAuth.get()).startsWith("Bearer ");
        verify(copia).upsert(
                eq(10L), eq("ACTIVE"), eq("PRO"), eq(true), eq(true),
                eq(LocalDateTime.of(2026, 12, 31, 23, 59, 59)), eq(LocalDateTime.of(2027, 1, 7, 23, 59, 59)), eq(3L)
        );
    }

    @Test
    void conFlagTrue_paginacionHastaPaginaConMenosElementosQueTamanio() {
        servidor.createContext("/", intercambio -> {
            String uri = intercambio.getRequestURI().toString();
            rutasLlamadas.add(uri);

            StringBuilder sb = new StringBuilder("[");
            // Una fila inválida en la página llena: se saltea, pero la página sigue contando como llena.
            if (uri.contains("pagina=0")) {
                for (int i = 0; i < 200; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"professionalId\":%d,\"estado\":\"ACTIVE\",\"planCode\":\"BASIC\",\"acceso\":true,\"puedeRecetar\":false,\"version\":1}", i == 7 ? -8 : i + 1));
                }
            } else if (uri.contains("pagina=1")) {
                for (int i = 200; i < 205; i++) {
                    if (i > 200) sb.append(",");
                    sb.append(String.format("{\"professionalId\":%d,\"estado\":\"ACTIVE\",\"planCode\":\"BASIC\",\"acceso\":true,\"puedeRecetar\":false,\"version\":1}", i + 1));
                }
            }
            sb.append("]");

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });

        ResincronizacionSuscripciones resync = new ResincronizacionSuscripciones(
                copia, true, getBaseUrl(), CLAVE
        );

        resync.resincronizar();

        assertThat(rutasLlamadas).containsExactly(
                "/internal/suscripciones/estado?pagina=0&tamanio=200",
                "/internal/suscripciones/estado?pagina=1&tamanio=200"
        );
        verify(copia, times(204)).upsert(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), anyLong());
    }

    @Test
    void siServicioResponde503_noRompeYLogueaWarning() {
        servidor.createContext("/", intercambio -> {
            rutasLlamadas.add(intercambio.getRequestURI().toString());
            byte[] bytes = "{\"error\":\"unavailable\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(503, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });

        ResincronizacionSuscripciones resync = new ResincronizacionSuscripciones(
                copia, true, getBaseUrl(), CLAVE
        );

        assertDoesNotThrow(resync::resincronizar);
        assertThat(rutasLlamadas).hasSize(1);
        verify(copia, never()).upsert(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), anyLong());
    }
}
