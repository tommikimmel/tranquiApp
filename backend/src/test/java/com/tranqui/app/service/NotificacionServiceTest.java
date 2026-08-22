package com.tranqui.app.service;

import com.tranqui.app.model.Notificacion;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NotificacionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
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
        when(notificacionRepository.findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(eq(1L), any())).thenReturn(expected);

        assertEquals(expected, notificacionService.obtenerNotificaciones("ana@mail.com"));
        // obtenerNotificaciones is a read-only path (@Transactional(readOnly = true)) — it no
        // longer runs cleanup inline (that DELETE used to run on every GET, deleting expired
        // notifications for ALL users, not just the caller — see the comment on
        // NotificacionService.obtenerNotificaciones). Cleanup now lives exclusively in
        // limpiarNotificacionesAntiguas(), invoked hourly by NotificationScheduler. Asserting
        // deleteByFechaCreacionBefore was called here was testing behavior the code doesn't have
        // anymore.
        verify(notificacionRepository, never()).deleteByFechaCreacionBefore(any());
    }

    // Boundary on the 24h read window: the query bound passed to the repository must be ~24h in
    // the past (not, say, 0h or 48h) — captured and compared with a tolerance instead of an exact
    // LocalDateTime.now() match, since that would be flaky by construction.
    @Test
    void obtenerNotificaciones_shouldQueryWithTwentyFourHourCutoff() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));
        when(notificacionRepository.findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(eq(1L), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusHours(24);
        notificacionService.obtenerNotificaciones("ana@mail.com");
        LocalDateTime after = LocalDateTime.now().minusHours(24);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificacionRepository).findByUsuarioIdAndFechaCreacionAfterOrderByFechaCreacionDesc(eq(1L), captor.capture());

        LocalDateTime cutoffUsed = captor.getValue();
        assertFalse(cutoffUsed.isBefore(before));
        assertFalse(cutoffUsed.isAfter(after));
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

    @Test
    void limpiarNotificacionesAntiguas_shouldDeleteWithTwentyFourHourCutoff() {
        LocalDateTime before = LocalDateTime.now().minusHours(24);
        notificacionService.limpiarNotificacionesAntiguas();
        LocalDateTime after = LocalDateTime.now().minusHours(24);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificacionRepository).deleteByFechaCreacionBefore(captor.capture());

        LocalDateTime cutoffUsed = captor.getValue();
        assertFalse(cutoffUsed.isBefore(before));
        assertFalse(cutoffUsed.isAfter(after));
    }
}
