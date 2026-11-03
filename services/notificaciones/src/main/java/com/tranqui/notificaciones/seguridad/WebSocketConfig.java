package com.tranqui.notificaciones.seguridad;

import com.tranqui.notificaciones.contacto.ContactoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Notificaciones en vivo por STOMP en {@code /ws-notificaciones} (con SockJS, como el endpoint del
 * monolito). El chat sigue en {@code /ws-tranqui}.
 *
 * <p>Mismo control que el monolito ({@code WebSocketChannelInterceptor}): el CONNECT necesita un JWT
 * válido (cookie {@code SESSION-TOKEN} o {@code Authorization: Bearer}) y cada usuario solo puede
 * suscribirse a {@code /topic/notificaciones/{su id}}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String DESTINO = "/topic/notificaciones/";
    private static final String ATRIBUTO_TOKEN = UsuarioJwt.COOKIE_SESION;

    private final UsuarioJwt usuarioJwt;
    private final ContactoService contactos;

    public WebSocketConfig(UsuarioJwt usuarioJwt, ContactoService contactos) {
        this.usuarioJwt = usuarioJwt;
        this.contactos = contactos;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        HandshakeInterceptor guardarCookie = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> atributos) {
                if (request instanceof ServletServerHttpRequest servlet) {
                    HttpServletRequest http = servlet.getServletRequest();
                    usuarioJwt.extraerToken(http).ifPresent(token -> atributos.put(ATRIBUTO_TOKEN, token));
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
            }
        };
        registry.addEndpoint("/ws-notificaciones").setAllowedOriginPatterns("*").addInterceptors(guardarCookie);
        registry.addEndpoint("/ws-notificaciones").setAllowedOriginPatterns("*").addInterceptors(guardarCookie)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    autenticar(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    autorizarSuscripcion(accessor);
                }
                return message;
            }
        });
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String token = accessor.getSessionAttributes() != null
                ? (String) accessor.getSessionAttributes().get(ATRIBUTO_TOKEN) : null;
        if (token == null) {
            String autorizacion = accessor.getFirstNativeHeader("Authorization");
            if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
                token = autorizacion.substring(7);
            }
        }
        Optional<UsuarioJwt.Usuario> usuario = usuarioJwt.validar(token);
        usuario.ifPresent(u -> accessor.setUser(new UsuarioPrincipal(u.email())));
    }

    private void autorizarSuscripcion(StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        if (destino == null || !destino.startsWith(DESTINO)) {
            throw new MessageDeliveryException("Destino no permitido");
        }
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new MessageDeliveryException("Acceso no autorizado: no se encuentra autenticado");
        }
        long usuarioId;
        try {
            usuarioId = Long.parseLong(destino.substring(DESTINO.length()));
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException("Formato de id no válido");
        }
        boolean propio = contactos.buscarPorEmail(principal.getName())
                .map(c -> c.getId().equals(usuarioId)).orElse(false);
        if (!propio) {
            throw new MessageDeliveryException("Acceso no autorizado: no puede suscribirse a las notificaciones de otro usuario");
        }
    }

    record UsuarioPrincipal(String email) implements Principal {
        @Override
        public String getName() {
            return email;
        }
    }
}
