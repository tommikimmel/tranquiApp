package com.tranqui.app.config;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String jwt = authHeader.substring(7);
                    if (jwtService.validateToken(jwt)) {
                        String userEmail = jwtService.extractEmail(jwt);
                        if (userEmail != null && accessor.getUser() == null) {
                            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                            accessor.setUser(authToken);
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/topic/notificaciones/")) {
                    String medicoIdStr = destination.substring("/topic/notificaciones/".length());
                    try {
                        Long medicoId = Long.parseLong(medicoIdStr);
                        java.security.Principal principal = accessor.getUser();
                        if (principal == null) {
                            throw new MessageDeliveryException("Acceso no autorizado: No se encuentra autenticado");
                        }

                        String email = principal.getName();
                        Usuario medico = usuarioRepository.findById(medicoId)
                                .orElseThrow(() -> new MessageDeliveryException("Médico no encontrado"));

                        if (!medico.getEmail().equals(email)) {
                            throw new MessageDeliveryException("Acceso no autorizado: No puede suscribirse al canal de notificaciones de otro médico");
                        }
                    } catch (NumberFormatException e) {
                        throw new MessageDeliveryException("Formato de ID de médico no válido");
                    }
                }
            }
        }

        return message;
    }
}
