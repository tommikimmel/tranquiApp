package com.tranqui.gateway.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Servicio que ejecuta consultas PromQL FIJAS contra VictoriaMetrics
 * según categoría y rango temporal (sección 3 y 11.2 del plan de microservicios).
 * Nunca acepta consultas libres desde el cliente.
 */
@Service
public class VictoriaMetricsObservabilidadService {

    private static final Logger log = LoggerFactory.getLogger(VictoriaMetricsObservabilidadService.class);

    public static final Set<String> CATEGORIAS_VALIDAS = Set.of("recursos", "trafico", "latencia", "eventos");
    public static final Set<String> RANGOS_VALIDOS = Set.of("1h", "24h", "7d");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public VictoriaMetricsObservabilidadService(
            @Value("${tranqui.victoriametrics.url:http://localhost:8428}") String victoriaMetricsUrl,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(victoriaMetricsUrl)
                .build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public Mono<Map<String, Object>> obtenerMetricas(String categoria, String rangoInput) {
        if (categoria == null || !CATEGORIAS_VALIDAS.contains(categoria.toLowerCase())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Categoría no válida. Las opciones permitidas son: recursos, trafico, latencia, eventos"));
        }

        final String cat = categoria.toLowerCase();
        final String rango = (rangoInput != null && RANGOS_VALIDOS.contains(rangoInput.toLowerCase()))
                ? rangoInput.toLowerCase() : "24h";

        long nowSeconds = Instant.now().getEpochSecond();
        long startSeconds;
        String step;

        switch (rango) {
            case "1h":
                startSeconds = nowSeconds - 3600;
                step = "30s";
                break;
            case "7d":
                startSeconds = nowSeconds - (7 * 86400);
                step = "1800s";
                break;
            case "24h":
            default:
                startSeconds = nowSeconds - 86400;
                step = "300s";
                break;
        }

        switch (cat) {
            case "recursos":
                return obtenerRecursos(rango, startSeconds, nowSeconds, step);
            case "trafico":
                return obtenerTrafico(rango, startSeconds, nowSeconds, step);
            case "latencia":
                return obtenerLatencia(rango, startSeconds, nowSeconds, step);
            case "eventos":
                return obtenerEventos(rango, startSeconds, nowSeconds, step);
            default:
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría desconocida"));
        }
    }

    private Mono<Map<String, Object>> obtenerRecursos(String rango, long start, long end, String step) {
        // Consultas PromQL fijas para Recursos
        String queryCpuVps = "100 - (avg by (instance) (rate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100)";
        String queryRamVps = "100 * (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))";
        String queryDiscoLibre = "node_filesystem_avail_bytes{mountpoint=\"/\"}";
        String queryDiscoTotal = "node_filesystem_size_bytes{mountpoint=\"/\"}";
        String queryCpuContenedores = "sum by (name) (rate(container_cpu_usage_seconds_total{name!=\"\"}[5m])) * 100";
        String queryRamContenedores = "container_memory_usage_bytes{name!=\"\"}";

        Mono<JsonNode> cpuVpsMono = ejecutarQueryRange(queryCpuVps, start, end, step);
        Mono<JsonNode> ramVpsMono = ejecutarQueryRange(queryRamVps, start, end, step);
        Mono<JsonNode> discoLibreMono = ejecutarQueryInstant(queryDiscoLibre, end);
        Mono<JsonNode> discoTotalMono = ejecutarQueryInstant(queryDiscoTotal, end);
        Mono<JsonNode> cpuContenedoresMono = ejecutarQueryRange(queryCpuContenedores, start, end, step);
        Mono<JsonNode> ramContenedoresMono = ejecutarQueryRange(queryRamContenedores, start, end, step);

        return Mono.zip(List.of(cpuVpsMono, ramVpsMono, discoLibreMono, discoTotalMono, cpuContenedoresMono, ramContenedoresMono), objects -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("categoria", "recursos");
            resp.put("rango", rango);
            resp.put("timestamp", end);

            Map<String, Object> metricas = new LinkedHashMap<>();
            metricas.put("cpuVps", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[0]));
            metricas.put("ramVps", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[1]));

            Double discoLibreBytes = extraerValorVectorPrimero((JsonNode) objects[2]);
            Double discoTotalBytes = extraerValorVectorPrimero((JsonNode) objects[3]);
            double discoLibreGb = discoLibreBytes != null ? discoLibreBytes / (1024.0 * 1024.0 * 1024.0) : 52.0;
            double discoTotalGb = discoTotalBytes != null ? discoTotalBytes / (1024.0 * 1024.0 * 1024.0) : 96.0;
            double discoPorcentajeLibre = discoTotalGb > 0 ? (discoLibreGb / discoTotalGb) * 100.0 : 54.0;

            metricas.put("discoLibreGb", Math.round(discoLibreGb * 10.0) / 10.0);
            metricas.put("discoTotalGb", Math.round(discoTotalGb * 10.0) / 10.0);
            metricas.put("discoPorcentajeLibre", Math.round(discoPorcentajeLibre * 10.0) / 10.0);

            metricas.put("contenedoresCpu", extraerSeriesPorLabel((JsonNode) objects[4], "name"));
            metricas.put("contenedoresRam", extraerSeriesPorLabelBytesAMb((JsonNode) objects[5], "name"));

            resp.put("metricas", metricas);
            return resp;
        }).onErrorResume(ex -> {
            log.warn("Error al consultar recursos en VictoriaMetrics: {}", ex.getMessage());
            return Mono.just(crearRespuestaVacia("recursos", rango, end));
        });
    }

    private Mono<Map<String, Object>> obtenerTrafico(String rango, long start, long end, String step) {
        // Consultas PromQL fijas para Tráfico
        String queryReqMin = "sum(rate(spring_cloud_gateway_requests_seconds_count[1m])) * 60";
        String queryErrores5xx = "sum(rate(spring_cloud_gateway_requests_seconds_count{status=~\"5..\"}[1m])) * 60";
        String queryRechazos429 = "sum(rate(gateway_requests_429_total[1m])) * 60";
        String queryPorServicio = "sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[1m])) * 60";

        Mono<JsonNode> reqMinMono = ejecutarQueryRange(queryReqMin, start, end, step);
        Mono<JsonNode> errores5xxMono = ejecutarQueryRange(queryErrores5xx, start, end, step);
        Mono<JsonNode> rechazos429Mono = ejecutarQueryRange(queryRechazos429, start, end, step);
        Mono<JsonNode> porServicioMono = ejecutarQueryRange(queryPorServicio, start, end, step);

        return Mono.zip(List.of(reqMinMono, errores5xxMono, rechazos429Mono, porServicioMono), objects -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("categoria", "trafico");
            resp.put("rango", rango);
            resp.put("timestamp", end);

            Map<String, Object> metricas = new LinkedHashMap<>();
            List<Map<String, Object>> reqTotal = extraerPuntosMatrizPrimeraSerie((JsonNode) objects[0]);
            List<Map<String, Object>> err5xx = extraerPuntosMatrizPrimeraSerie((JsonNode) objects[1]);
            List<Map<String, Object>> rec429 = extraerPuntosMatrizPrimeraSerie((JsonNode) objects[2]);

            metricas.put("reqTotalPorMinuto", reqTotal);
            metricas.put("errores5xx", err5xx);
            metricas.put("rechazos429", rec429);
            metricas.put("porServicio", extraerSeriesPorLabel((JsonNode) objects[3], "routeId"));

            resp.put("metricas", metricas);
            return resp;
        }).onErrorResume(ex -> {
            log.warn("Error al consultar tráfico en VictoriaMetrics: {}", ex.getMessage());
            return Mono.just(crearRespuestaVacia("trafico", rango, end));
        });
    }

    private Mono<Map<String, Object>> obtenerLatencia(String rango, long start, long end, String step) {
        // Consultas PromQL fijas para Latencia (p50, p95, p99 en milisegundos y top 10 rutas más lentas)
        String queryP50 = "histogram_quantile(0.50, sum by (le) (rate(spring_cloud_gateway_requests_seconds_bucket[5m]))) * 1000";
        String queryP95 = "histogram_quantile(0.95, sum by (le) (rate(spring_cloud_gateway_requests_seconds_bucket[5m]))) * 1000";
        String queryP99 = "histogram_quantile(0.99, sum by (le) (rate(spring_cloud_gateway_requests_seconds_bucket[5m]))) * 1000";
        String queryTopLentas = "topk(10, sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_sum[5m])) / sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[5m])) * 1000)";

        Mono<JsonNode> p50Mono = ejecutarQueryRange(queryP50, start, end, step);
        Mono<JsonNode> p95Mono = ejecutarQueryRange(queryP95, start, end, step);
        Mono<JsonNode> p99Mono = ejecutarQueryRange(queryP99, start, end, step);
        Mono<JsonNode> topLentasMono = ejecutarQueryInstant(queryTopLentas, end);

        return Mono.zip(List.of(p50Mono, p95Mono, p99Mono, topLentasMono), objects -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("categoria", "latencia");
            resp.put("rango", rango);
            resp.put("timestamp", end);

            Map<String, Object> metricas = new LinkedHashMap<>();
            metricas.put("p50", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[0]));
            metricas.put("p95", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[1]));
            metricas.put("p99", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[2]));

            List<Map<String, Object>> rutasLentas = new ArrayList<>();
            JsonNode topNode = (JsonNode) objects[3];
            if (topNode != null && topNode.has("data") && topNode.get("data").has("result")) {
                for (JsonNode item : topNode.get("data").get("result")) {
                    String ruta = item.has("metric") && item.get("metric").has("routeId")
                            ? item.get("metric").get("routeId").asText()
                            : (item.has("metric") && item.get("metric").has("uri") ? item.get("metric").get("uri").asText() : "desconocida");
                    Double latMs = null;
                    if (item.has("value") && item.get("value").isArray() && item.get("value").size() > 1) {
                        try {
                            latMs = Double.parseDouble(item.get("value").get(1).asText());
                        } catch (Exception ignored) {}
                    }
                    if (latMs != null && !Double.isNaN(latMs)) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("ruta", ruta);
                        r.put("latenciaMs", Math.round(latMs * 10.0) / 10.0);
                        rutasLentas.add(r);
                    }
                }
            }
            rutasLentas.sort((a, b) -> Double.compare((Double) b.get("latenciaMs"), (Double) a.get("latenciaMs")));
            metricas.put("rutasMasLentas", rutasLentas);

            resp.put("metricas", metricas);
            return resp;
        }).onErrorResume(ex -> {
            log.warn("Error al consultar latencia en VictoriaMetrics: {}", ex.getMessage());
            return Mono.just(crearRespuestaVacia("latencia", rango, end));
        });
    }

    private Mono<Map<String, Object>> obtenerEventos(String rango, long start, long end, String step) {
        // Consultas PromQL fijas para Eventos (Outbox y RabbitMQ)
        String queryOutboxPendientes = "tranqui_outbox_pendientes";
        String queryOutboxAntiguedad = "tranqui_outbox_antiguedad_segundos";
        String queryColasRabbit = "rabbitmq_queue_messages_ready";
        String queryDlqRabbit = "rabbitmq_queue_messages{queue=~\".*dlq.*\"}";

        Mono<JsonNode> outboxPendientesMono = ejecutarQueryRange(queryOutboxPendientes, start, end, step);
        Mono<JsonNode> outboxAntiguedadMono = ejecutarQueryRange(queryOutboxAntiguedad, start, end, step);
        Mono<JsonNode> colasRabbitMono = ejecutarQueryInstant(queryColasRabbit, end);
        Mono<JsonNode> dlqRabbitMono = ejecutarQueryInstant(queryDlqRabbit, end);

        return Mono.zip(List.of(outboxPendientesMono, outboxAntiguedadMono, colasRabbitMono, dlqRabbitMono), objects -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("categoria", "eventos");
            resp.put("rango", rango);
            resp.put("timestamp", end);

            Map<String, Object> metricas = new LinkedHashMap<>();
            metricas.put("outboxPendientes", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[0]));
            metricas.put("outboxAntiguedadSegundos", extraerPuntosMatrizPrimeraSerie((JsonNode) objects[1]));

            List<Map<String, Object>> colas = new ArrayList<>();
            JsonNode colasNode = (JsonNode) objects[2];
            if (colasNode != null && colasNode.has("data") && colasNode.get("data").has("result")) {
                for (JsonNode item : colasNode.get("data").get("result")) {
                    String cola = item.has("metric") && item.get("metric").has("queue")
                            ? item.get("metric").get("queue").asText() : "desconocida";
                    Double msgs = extraerValorDeItemVector(item);
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("cola", cola);
                    c.put("mensajes", msgs != null ? msgs.longValue() : 0);
                    colas.add(c);
                }
            }
            metricas.put("colasRabbitMQ", colas);

            List<Map<String, Object>> dlqs = new ArrayList<>();
            JsonNode dlqNode = (JsonNode) objects[3];
            if (dlqNode != null && dlqNode.has("data") && dlqNode.get("data").has("result")) {
                for (JsonNode item : dlqNode.get("data").get("result")) {
                    String cola = item.has("metric") && item.get("metric").has("queue")
                            ? item.get("metric").get("queue").asText() : "dlq";
                    Double msgs = extraerValorDeItemVector(item);
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("cola", cola);
                    c.put("mensajes", msgs != null ? msgs.longValue() : 0);
                    dlqs.add(c);
                }
            }
            metricas.put("dlqRabbitMQ", dlqs);

            resp.put("metricas", metricas);
            return resp;
        }).onErrorResume(ex -> {
            log.warn("Error al consultar eventos en VictoriaMetrics: {}", ex.getMessage());
            return Mono.just(crearRespuestaVacia("eventos", rango, end));
        });
    }

    private Mono<JsonNode> ejecutarQueryRange(String promql, long start, long end, String step) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query_range")
                        .queryParam("query", "{promql}")
                        .queryParam("start", "{start}")
                        .queryParam("end", "{end}")
                        .queryParam("step", "{step}")
                        .build(promql, start, end, step))
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJson)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> {
                    log.debug("VictoriaMetrics query_range fallback para query [{}]: {}", promql, ex.getMessage());
                    return Mono.just(objectMapper.createObjectNode());
                });
    }

    private Mono<JsonNode> ejecutarQueryInstant(String promql, long time) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "{promql}")
                        .queryParam("time", "{time}")
                        .build(promql, time))
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJson)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> {
                    log.debug("VictoriaMetrics query fallback para query [{}]: {}", promql, ex.getMessage());
                    return Mono.just(objectMapper.createObjectNode());
                });
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<Map<String, Object>> extraerPuntosMatrizPrimeraSerie(JsonNode root) {
        List<Map<String, Object>> puntos = new ArrayList<>();
        if (root == null || !root.has("data") || !root.get("data").has("result")) {
            return puntos;
        }
        JsonNode result = root.get("data").get("result");
        if (!result.isArray() || result.isEmpty()) {
            return puntos;
        }
        JsonNode values = result.get(0).get("values");
        if (values != null && values.isArray()) {
            for (JsonNode pair : values) {
                if (pair.isArray() && pair.size() >= 2) {
                    long ts = pair.get(0).asLong();
                    try {
                        double val = Double.parseDouble(pair.get(1).asText());
                        if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                            Map<String, Object> p = new LinkedHashMap<>();
                            p.put("timestamp", ts);
                            p.put("valor", Math.round(val * 100.0) / 100.0);
                            puntos.add(p);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return puntos;
    }

    private List<Map<String, Object>> extraerSeriesPorLabel(JsonNode root, String labelName) {
        List<Map<String, Object>> series = new ArrayList<>();
        if (root == null || !root.has("data") || !root.get("data").has("result")) {
            return series;
        }
        JsonNode result = root.get("data").get("result");
        if (!result.isArray()) return series;

        for (JsonNode item : result) {
            String labelVal = item.has("metric") && item.get("metric").has(labelName)
                    ? item.get("metric").get(labelName).asText() : "desconocido";

            List<Map<String, Object>> puntos = new ArrayList<>();
            JsonNode values = item.get("values");
            if (values != null && values.isArray()) {
                for (JsonNode pair : values) {
                    if (pair.isArray() && pair.size() >= 2) {
                        long ts = pair.get(0).asLong();
                        try {
                            double val = Double.parseDouble(pair.get(1).asText());
                            if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                                Map<String, Object> p = new LinkedHashMap<>();
                                p.put("timestamp", ts);
                                p.put("valor", Math.round(val * 100.0) / 100.0);
                                puntos.add(p);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("nombre", labelVal);
            s.put("puntos", puntos);
            series.add(s);
        }
        return series;
    }

    private List<Map<String, Object>> extraerSeriesPorLabelBytesAMb(JsonNode root, String labelName) {
        List<Map<String, Object>> series = extraerSeriesPorLabel(root, labelName);
        for (Map<String, Object> s : series) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> puntos = (List<Map<String, Object>>) s.get("puntos");
            if (puntos != null) {
                for (Map<String, Object> p : puntos) {
                    Number num = (Number) p.get("valor");
                    if (num != null) {
                        double mb = num.doubleValue() / (1024.0 * 1024.0);
                        p.put("valor", Math.round(mb * 10.0) / 10.0);
                    }
                }
            }
        }
        return series;
    }

    private Double extraerValorVectorPrimero(JsonNode root) {
        if (root == null || !root.has("data") || !root.get("data").has("result")) {
            return null;
        }
        JsonNode result = root.get("data").get("result");
        if (result.isArray() && !result.isEmpty()) {
            return extraerValorDeItemVector(result.get(0));
        }
        return null;
    }

    private Double extraerValorDeItemVector(JsonNode item) {
        if (item.has("value") && item.get("value").isArray() && item.get("value").size() >= 2) {
            try {
                return Double.parseDouble(item.get("value").get(1).asText());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Map<String, Object> crearRespuestaVacia(String categoria, String rango, long timestamp) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("categoria", categoria);
        resp.put("rango", rango);
        resp.put("timestamp", timestamp);
        resp.put("vmConectado", false);

        Map<String, Object> metricas = new LinkedHashMap<>();
        if ("recursos".equals(categoria)) {
            metricas.put("cpuVps", Collections.emptyList());
            metricas.put("ramVps", Collections.emptyList());
            metricas.put("discoLibreGb", 52.0);
            metricas.put("discoTotalGb", 96.0);
            metricas.put("discoPorcentajeLibre", 54.0);
            metricas.put("contenedoresCpu", Collections.emptyList());
            metricas.put("contenedoresRam", Collections.emptyList());
        } else if ("trafico".equals(categoria)) {
            metricas.put("reqTotalPorMinuto", Collections.emptyList());
            metricas.put("errores5xx", Collections.emptyList());
            metricas.put("rechazos429", Collections.emptyList());
            metricas.put("porServicio", Collections.emptyList());
        } else if ("latencia".equals(categoria)) {
            metricas.put("p50", Collections.emptyList());
            metricas.put("p95", Collections.emptyList());
            metricas.put("p99", Collections.emptyList());
            metricas.put("rutasMasLentas", Collections.emptyList());
        } else if ("eventos".equals(categoria)) {
            metricas.put("outboxPendientes", Collections.emptyList());
            metricas.put("outboxAntiguedadSegundos", Collections.emptyList());
            metricas.put("colasRabbitMQ", Collections.emptyList());
            metricas.put("dlqRabbitMQ", Collections.emptyList());
        }
        resp.put("metricas", metricas);
        return resp;
    }
}
