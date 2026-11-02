package com.tranqui.eventos.consumo;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.RabbitMQConstantes;
import com.tranqui.eventos.Topicos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Consume la cola {@code <servicio>.eventos} con ack manual (sección 5.1).
 *
 * <p>Por cada evento corre los handlers de su tópico, cada uno en su propia transacción con el
 * inbox. Si todos terminan, confirma (ack). Si alguno falla, el evento pasa a la cola de espera
 * siguiente (10 s, 1 min, 5 min) y después a la DLQ; los handlers que ya habían terminado no se
 * repiten porque el inbox los reconoce. Un evento mal formado va directo a la DLQ.
 */
public class ConsumidorEventos implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    static final String HEADER_INTENTOS = "x-tranqui-intentos";
    static final String HEADER_ERROR = "x-tranqui-error";
    static final String HEADER_TOPICO = "x-tranqui-topico";
    private static final int LARGO_MAXIMO_ERROR = 500;
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final String servicio;
    private final Map<String, List<ManejadorEvento<?>>> manejadoresPorTopico = new HashMap<>();
    private final InboxHelper inbox;
    private final TransactionTemplate transacciones;
    private final ObjectMapper mapper;
    private final MeterRegistry metricas;

    public ConsumidorEventos(String servicio, List<ManejadorEvento<?>> manejadores, InboxHelper inbox,
                             TransactionTemplate transacciones, ObjectMapper mapper, MeterRegistry metricas) {
        this.servicio = servicio;
        this.inbox = inbox;
        this.transacciones = transacciones;
        this.mapper = mapper;
        this.metricas = metricas;
        for (ManejadorEvento<?> manejador : manejadores) {
            for (String topico : manejador.topicos()) {
                if (!Topicos.esValido(topico)) {
                    throw new IllegalArgumentException("Tópico inválido en " + manejador.consumidor() + ": " + topico);
                }
                manejadoresPorTopico.computeIfAbsent(topico, t -> new ArrayList<>()).add(manejador);
            }
        }
    }

    /** Tópicos que el servicio tiene que enlazar a su cola. */
    public Set<String> topicos() {
        return manejadoresPorTopico.keySet();
    }

    @Override
    public void onMessage(Message mensaje, Channel canal) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        int intentos = intentosPrevios(mensaje);
        String topico = null;
        try {
            JsonNode raiz = leer(mensaje.getBody());
            topico = Topicos.armarTopico(texto(raiz, "eventType"), raiz.path("version").asInt(1));
            MDC.put("correlationId", raiz.path("correlationId").asText(null));

            List<ManejadorEvento<?>> manejadores = manejadoresPorTopico.getOrDefault(topico, List.of());
            if (manejadores.isEmpty()) {
                log.debug("Evento {} sin handlers en '{}', se confirma sin procesar", topico, servicio);
            }
            for (ManejadorEvento<?> manejador : manejadores) {
                procesar(manejador, raiz);
            }
            canal.basicAck(tag, false);
            contar(topico, "ok");
        } catch (EventoInvalidoException e) {
            log.error("Evento inválido en '{}' ({}): va a la DLQ. {}", servicio, topico, e.getMessage());
            reenviar(mensaje, canal, RabbitMQConstantes.EXCHANGE_DLX, RabbitMQConstantes.colaDlq(servicio),
                    intentos, topico, e);
            contar(topico, "dlq");
        } catch (Exception e) {
            if (intentos < TopologiaConsumo.NIVELES.size()) {
                String cola = servicio + TopologiaConsumo.NIVELES.get(intentos).sufijo();
                log.warn("Falló el evento {} en '{}' (intento {}): se reintenta por {}. {}",
                        topico, servicio, intentos + 1, cola, e.toString());
                reenviar(mensaje, canal, RabbitMQConstantes.EXCHANGE_REINTENTOS, cola, intentos + 1, topico, e);
                contar(topico, "reintento");
            } else {
                log.error("El evento {} falló {} veces en '{}': va a la DLQ", topico, intentos + 1, servicio, e);
                reenviar(mensaje, canal, RabbitMQConstantes.EXCHANGE_DLX, RabbitMQConstantes.colaDlq(servicio),
                        intentos, topico, e);
                contar(topico, "dlq");
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    private <T> void procesar(ManejadorEvento<T> manejador, JsonNode raiz) {
        EventEnvelope<T> evento = convertir(raiz, manejador.tipoData());
        if (evento.eventId() == null) {
            throw new EventoInvalidoException("El evento no tiene eventId");
        }
        transacciones.executeWithoutResult(estado -> {
            if (!inbox.registrar(evento.eventId(), manejador.consumidor())) {
                return;
            }
            if (manejador.descartarVersionesViejas() && evento.aggregateType() != null && evento.aggregateId() != null
                    && !inbox.avanzarVersion(manejador.consumidor(), evento.aggregateType(),
                    evento.aggregateId(), evento.aggregateVersion())) {
                return;
            }
            manejador.manejar(evento);
        });
    }

    private <T> EventEnvelope<T> convertir(JsonNode raiz, Class<T> tipoData) {
        JavaType tipo = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, tipoData);
        try {
            return mapper.convertValue(raiz, tipo);
        } catch (IllegalArgumentException e) {
            throw new EventoInvalidoException("El data no respeta el contrato de " + tipoData.getSimpleName(), e);
        }
    }

    private JsonNode leer(byte[] cuerpo) {
        try {
            JsonNode raiz = mapper.readTree(cuerpo);
            if (raiz == null || !raiz.isObject()) {
                throw new EventoInvalidoException("El mensaje no es un objeto JSON");
            }
            return raiz;
        } catch (IOException e) {
            throw new EventoInvalidoException("El mensaje no es JSON válido", e);
        }
    }

    private static String texto(JsonNode raiz, String campo) {
        String valor = raiz.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new EventoInvalidoException("Falta el campo " + campo);
        }
        return valor;
    }

    /**
     * Publica una copia del mensaje en la cola de espera o en la DLQ y recién después confirma el
     * original. Si la copia no se pudo publicar, devuelve el original a la cola (nack) para no perderlo.
     */
    private void reenviar(Message mensaje, Channel canal, String exchange, String clave, int intentos,
                          String topico, Exception error) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> originales = mensaje.getMessageProperties().getHeaders();
        if (originales != null) {
            originales.forEach((k, v) -> {
                if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                    headers.put(k, v);
                }
            });
        }
        headers.put(HEADER_INTENTOS, intentos);
        headers.put(HEADER_ERROR, recortar(error.toString()));
        if (topico != null) {
            headers.put(HEADER_TOPICO, topico);
        }
        AMQP.BasicProperties propiedades = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding(StandardCharsets.UTF_8.name())
                .deliveryMode(2)
                .messageId(mensaje.getMessageProperties().getMessageId())
                .headers(headers)
                .build();
        try {
            canal.confirmSelect();
            canal.basicPublish(exchange, clave, propiedades, mensaje.getBody());
            canal.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
            canal.basicAck(tag, false);
        } catch (IOException | InterruptedException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("No se pudo mover el evento a {}; vuelve a la cola de '{}'", clave, servicio, e);
            canal.basicNack(tag, false, true);
        }
    }

    private static int intentosPrevios(Message mensaje) {
        Object valor = mensaje.getMessageProperties().getHeaders().get(HEADER_INTENTOS);
        return valor instanceof Number numero ? numero.intValue() : 0;
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_MAXIMO_ERROR ? texto : texto.substring(0, LARGO_MAXIMO_ERROR);
    }

    private void contar(String topico, String resultado) {
        if (metricas == null) {
            return;
        }
        Counter.builder("eventos.consumidos")
                .description("Eventos consumidos por resultado")
                .tag("servicio", servicio)
                .tag("topico", topico != null ? topico : "desconocido")
                .tag("resultado", resultado)
                .register(metricas)
                .increment();
    }
}
