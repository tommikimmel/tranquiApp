package com.tranqui.app.service;

import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resumen de chat de un profesional (Fase 2). El chat vive en su propio servicio: el monolito lo
 * consulta por la ruta interna del gateway con un JWT de servicio (sección 4.2).
 *
 * <ul>
 *   <li>Sin {@code tranqui.gateway.url} (tests, monolito solo) lo calcula con sus propias tablas.</li>
 *   <li>Si el chat no responde, devuelve un resumen vacío: la lista de pacientes sale igual, sin
 *       los no leídos (sección 7: un servicio caído no tira a otro).</li>
 *   <li>Vuelta atrás: con la ruta del gateway de vuelta al monolito, la misma consulta la responde
 *       {@code InternoChatController} con {@link #calcularLocal}.</li>
 * </ul>
 */
@Component
public class ResumenChatCliente {

    private static final Logger log = LoggerFactory.getLogger(ResumenChatCliente.class);

    private final MensajeRepository mensajeRepository;
    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ResumenChatCliente(MensajeRepository mensajeRepository,
                              @Value("${tranqui.gateway.url:}") String gatewayUrl,
                              @Value("${tranqui.jwt.service-secret:}") String claveServicio) {
        this.mensajeRepository = mensajeRepository;
        boolean remoto = gatewayUrl != null && !gatewayUrl.isBlank() && claveServicio != null && !claveServicio.isBlank();
        if (remoto) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(2_000);
            fabrica.setReadTimeout(5_000);
            this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
            this.serviceJwt = new ServiceJwt(claveServicio);
        } else {
            this.http = null;
            this.serviceJwt = null;
        }
    }

    public ResumenChat resumen(Long profesionalId) {
        if (http == null) {
            return calcularLocal(profesionalId);
        }
        try {
            ResumenChat resumen = http.get()
                    .uri("/internal/chat/profesionales/{id}/resumen", profesionalId)
                    .headers(h -> {
                        h.setBearerAuth(serviceJwt.emitirToken("monolito"));
                        String correlacion = MDC.get("correlationId");
                        h.set("X-Correlation-Id", correlacion != null ? correlacion : UUID.randomUUID().toString());
                    })
                    .retrieve()
                    .body(ResumenChat.class);
            return resumen != null ? resumen : ResumenChat.vacio();
        } catch (RuntimeException e) {
            log.warn("El chat no respondió el resumen del profesional {}; la lista sale sin no leídos: {}",
                    profesionalId, e.getMessage());
            return ResumenChat.vacio();
        }
    }

    /** Lo mismo que hacía {@code ClinicalService} con las tablas del monolito. */
    @Transactional(readOnly = true)
    public ResumenChat calcularLocal(Long profesionalId) {
        List<Long> pacientes = mensajeRepository.findPacientesConMensajesConMedico(profesionalId).stream()
                .map(u -> u.getId()).toList();
        Map<Long, Integer> noLeidos = mensajeRepository.countUnreadMessagesGroupedByRemitente(profesionalId).stream()
                .collect(Collectors.toMap(MensajeRepository.UnreadCountPorRemitente::getRemitenteId,
                        MensajeRepository.UnreadCountPorRemitente::getCantidad));
        return new ResumenChat(pacientes, noLeidos);
    }
}
