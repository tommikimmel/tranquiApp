package com.tranqui.app.service;

import com.tranqui.app.model.Notificacion;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NotificacionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock private NotificacionRepository notificacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificacionService notificacionService;

    private Usuario usuario() {
        return Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).build();
    }

    @Test
    void crearNotificacion_shouldSaveAndBroadcastViaWebSocket() {
        Usuario usuario = usuario();
        when(notificacionRepository.save(any())).thenAnswer(inv -> {
            Notificacion n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });

        Notificacion result = notificacionService.crearNotificacion(usuario, "Titulo", "Mensaje", "TIPO");

        assertEquals("Titulo", result.getTitulo());
        assertFalse(result.getLeido());
        verify(messagingTemplate).convertAndSend(eq("/topic/notificaciones/1"), any(Object.class));
    }

    @Test
    void crearNotificacion_shouldSwallowWebSocketErrors() {
        Usuario usuario = usuario();
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("ws down")).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertDoesNotThrow(() -> notificacionService.crearNotificacion(usuario, "T", "M", "TIPO"));
    }

    @Test
    void obtenerNotificaciones_shouldThrowWhenUsuarioNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> notificacionService.obtenerNotificaciones("x@mail.com"));
    }

    @Test
    void obtenerNotificaciones_shouldReturnRepositoryResult() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));
        List<Notificacion> expected = List.of(Notificacion.builder().id(1L).build());
        when(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(1L)).thenReturn(expected);

        assertEquals(expected, notificacionService.obtenerNotificaciones("ana@mail.com"));
    }

    @Test
    void marcarTodasComoLeidas_shouldThrowWhenUsuarioNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> notificacionService.marcarTodasComoLeidas("x@mail.com"));
    }

    @Test
    void marcarTodasComoLeidas_shouldDelegateToRepository() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));

        notificacionService.marcarTodasComoLeidas("ana@mail.com");

        verify(notificacionRepository).markAllAsRead(1L);
    }
}
