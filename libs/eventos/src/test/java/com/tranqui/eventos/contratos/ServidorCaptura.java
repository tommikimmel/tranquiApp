package com.tranqui.eventos.contratos;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado consumidor de los contratos de pedido: un servidor HTTP local que guarda el cuerpo que manda el cliente real
 * (con su propio mapper, que es lo que viaja) y responde {@code respuesta} con 200.
 */
public final class ServidorCaptura implements AutoCloseable {

    private final HttpServer servidor;
    private final Map<String, String> cuerpos = new ConcurrentHashMap<>();

    public ServidorCaptura(String respuesta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", x -> {
            cuerpos.put(x.getRequestMethod() + " " + x.getRequestURI().getPath(),
                    new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] r = respuesta.getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().add("Content-Type", "application/json");
            x.sendResponseHeaders(200, r.length);
            x.getResponseBody().write(r);
            x.close();
        });
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** El cuerpo recibido, por ejemplo {@code cuerpo("PUT /internal/monolito/pacientes/6")}. */
    public String cuerpo(String metodoYRuta) {
        String c = cuerpos.get(metodoYRuta);
        if (c == null) {
            throw new AssertionError("No llegó " + metodoYRuta + "; llegaron " + cuerpos.keySet());
        }
        return c;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
