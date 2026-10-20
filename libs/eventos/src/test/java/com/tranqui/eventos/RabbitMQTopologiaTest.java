package com.tranqui.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQTopologiaTest {

    @Test
    void constantesExchanges_coincidenConSeccion5_1() {
        assertThat(RabbitMQConstantes.EXCHANGE_EVENTOS).isEqualTo("tranqui.eventos");
        assertThat(RabbitMQConstantes.EXCHANGE_REINTENTOS).isEqualTo("tranqui.reintentos");
        assertThat(RabbitMQConstantes.EXCHANGE_DLX).isEqualTo("tranqui.dlx");
    }

    @Test
    void generadoresDeNombresDeColas_formanSufijosCorrectos() {
        String servicio = "notificaciones";

        assertThat(RabbitMQConstantes.colaEventos(servicio)).isEqualTo("notificaciones.eventos");
        assertThat(RabbitMQConstantes.colaReintento10s(servicio)).isEqualTo("notificaciones.reintento.10s");
        assertThat(RabbitMQConstantes.colaReintento1m(servicio)).isEqualTo("notificaciones.reintento.1m");
        assertThat(RabbitMQConstantes.colaReintento5m(servicio)).isEqualTo("notificaciones.reintento.5m");
        assertThat(RabbitMQConstantes.colaDlq(servicio)).isEqualTo("notificaciones.dlq");
    }

    @Test
    void definitionsJson_coincideConConstantesJavaYPoliticaLazy() throws Exception {
        Path path = Path.of("infra/rabbitmq/definitions.json");
        if (!Files.exists(path)) {
            path = Path.of("../../infra/rabbitmq/definitions.json");
        }
        assertThat(Files.exists(path))
                .as("El archivo infra/rabbitmq/definitions.json debe existir")
                .isTrue();

        ObjectMapper mapper = EventObjectMapperFactory.create();
        JsonNode root = mapper.readTree(path.toFile());

        JsonNode exchanges = root.get("exchanges");
        assertThat(exchanges).isNotNull();

        Map<String, JsonNode> exchangeMap = new HashMap<>();
        for (JsonNode exchange : exchanges) {
            exchangeMap.put(exchange.get("name").asText(), exchange);
        }

        assertThat(exchangeMap).containsKey(RabbitMQConstantes.EXCHANGE_EVENTOS);
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_EVENTOS).get("type").asText()).isEqualTo("topic");
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_EVENTOS).get("durable").asBoolean()).isTrue();

        assertThat(exchangeMap).containsKey(RabbitMQConstantes.EXCHANGE_REINTENTOS);
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_REINTENTOS).get("type").asText()).isEqualTo("direct");
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_REINTENTOS).get("durable").asBoolean()).isTrue();

        assertThat(exchangeMap).containsKey(RabbitMQConstantes.EXCHANGE_DLX);
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_DLX).get("type").asText()).isEqualTo("direct");
        assertThat(exchangeMap.get(RabbitMQConstantes.EXCHANGE_DLX).get("durable").asBoolean()).isTrue();

        JsonNode policies = root.get("policies");
        assertThat(policies).isNotNull();

        boolean hasLazyQueuePolicy = false;
        for (JsonNode policy : policies) {
            JsonNode def = policy.get("definition");
            if (def != null && "lazy".equals(def.path("queue-mode").asText())) {
                hasLazyQueuePolicy = true;
                break;
            }
        }
        assertThat(hasLazyQueuePolicy)
                .as("definitions.json debe configurar la política de colas lazy")
                .isTrue();
    }
}
