package com.tranqui.app.controller;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.NotificacionDocDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketNotificationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    private WebSocketStompClient stompClient;
    private Usuario paciente;
    private Usuario medico;
    private SolicitudDocumento solicitud;

    @BeforeEach
    void setup() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        paciente = Usuario.builder()
                .nombre("Paciente Pepito")
                .email("pepito@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        medico = Usuario.builder()
                .nombre("Dr. Juan")
                .email("juan@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);

        solicitud = SolicitudDocumento.builder()
                .paciente(paciente)
                .medico(medico)
                .tipoConcepto(TipoConcepto.RECETA_CONTROL)
                .precio(BigDecimal.valueOf(2500.00))
                .estado(EstadoPago.PENDIENTE)
                .build();

        solicitudRepository.save(solicitud);
    }

    @AfterEach
    void tearDown() {
        solicitudRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void shouldReceiveDocumentNotificationOnPaidWebhook() throws Exception {
        BlockingQueue<NotificacionDocDto> blockingQueue = new ArrayBlockingQueue<>(1);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-tranqui",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        System.err.println("STOMP Exception: " + exception.getMessage());
                        exception.printStackTrace();
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        System.err.println("STOMP Transport Error: " + exception.getMessage());
                        exception.printStackTrace();
                    }
                }
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(session);

        session.subscribe("/topic/notificaciones/" + medico.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificacionDocDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.add((NotificacionDocDto) payload);
            }
        });

        // Give SimpleBrokerMessageHandler a brief moment to process the subscription frame
        Thread.sleep(500);

        // Trigger paid webhook simulating Mercado Pago approval callback
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-signature", "valid-signature-12345");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String requestBody = String.format("{\"external_reference\": \"doc-%d\", \"transaction_id\": \"tx-test-999\"}", solicitud.getId());
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity("/api/payments/webhook", request, Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Wait for WebSocket notification to be received
        NotificacionDocDto received = blockingQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals(solicitud.getId(), received.getId());
        assertEquals("Paciente Pepito", received.getPacienteNombre());
        assertEquals("RECETA_CONTROL", received.getTipo());

        // Validate state was updated to APROBADO in the database
        SolicitudDocumento updated = solicitudRepository.findById(solicitud.getId()).orElse(null);
        assertNotNull(updated);
        assertEquals(EstadoPago.APROBADO, updated.getEstado());
        assertEquals("tx-test-999", updated.getTransactionId());
        
        session.disconnect();
    }
}
