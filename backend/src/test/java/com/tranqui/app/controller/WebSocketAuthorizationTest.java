package com.tranqui.app.controller;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketAuthorizationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    private WebSocketStompClient stompClient;
    private Usuario medico1;
    private Usuario medico2;
    private String tokenMedico1;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        medico1 = Usuario.builder()
                .nombre("Dr. Uno")
                .email("uno@test.com")
                .rol(Rol.PSIQUIATRA)
                .build();
        medico2 = Usuario.builder()
                .nombre("Dr. Dos")
                .email("dos@test.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(medico1);
        usuarioRepository.save(medico2);

        tokenMedico1 = jwtService.generateToken(medico1);
    }

    @AfterEach
    void tearDown() {
        if (medico1 != null && medico1.getId() != null) {
            try { usuarioRepository.delete(medico1); } catch (Exception e) {}
        }
        if (medico2 != null && medico2.getId() != null) {
            try { usuarioRepository.delete(medico2); } catch (Exception e) {}
        }
    }

    @Test
    void whenSubscribeToOwnChannel_shouldSucceed() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenMedico1);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-tranqui",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(session);

        // Subscribing to own notifications channel should succeed without throwing transport errors
        StompSession.Subscription subscription = session.subscribe(
                "/topic/notificaciones/" + medico1.getId(),
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return Object.class;
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {}
                }
        );

        assertNotNull(subscription);
        assertTrue(session.isConnected());
        
        try {
            session.disconnect();
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    void whenSubscribeToOtherDoctorChannel_shouldFail() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenMedico1);

        // Set up session with handlers to monitor errors
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-tranqui",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                        errorFuture.complete(exception);
                    }
                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        errorFuture.complete(exception);
                    }
                }
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(session);

        // Subscribing to other doctor's notifications channel should trigger access denied exception
        session.subscribe(
                "/topic/notificaciones/" + medico2.getId(),
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return Object.class;
                    }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {}
                }
        );

        // Wait a moment for broker to reject the subscription and propagate exception
        // The exception object itself is returned by errorFuture.get() when it completes
        Throwable exception = errorFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(exception);
        
        try {
            session.disconnect();
        } catch (Exception e) {
            // Ignore if connection was already closed by the server
        }
    }
}
