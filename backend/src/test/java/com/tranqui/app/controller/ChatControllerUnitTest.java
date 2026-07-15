package com.tranqui.app.controller;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MensajeService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerUnitTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MensajeService mensajeService;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ChatController controller;

    @Test
    void procesarMensaje_shouldSaveAndDispatchToBothUsersQueues() {
        Usuario remitente = Usuario.builder().id(1L).email("dra@mail.com").rol(Rol.PSIQUIATRA).build();
        Usuario destinatario = Usuario.builder().id(2L).email("ana@mail.com").rol(Rol.PACIENTE).build();
        Mensaje mensaje = Mensaje.builder().id(99L).remitente(remitente).destinatario(destinatario)
                .contenido("Hola").fechaEnvio(LocalDateTime.now()).build();

        MensajeDto dto = MensajeDto.builder().destinatarioId(2L).contenido("Hola").build();
        Principal principal = () -> "dra@mail.com";
        when(mensajeService.guardarMensaje(dto, "dra@mail.com")).thenReturn(mensaje);

        controller.procesarMensaje(dto, principal);

        assertEquals(99L, dto.getId());
        assertEquals(1L, dto.getRemitenteId());
        assertNotNull(dto.getFechaEnvio());
        verify(messagingTemplate).convertAndSendToUser("ana@mail.com", "/queue/mensajes", dto);
        verify(messagingTemplate).convertAndSendToUser("dra@mail.com", "/queue/mensajes", dto);
    }

    @Test
    void obtenerHistorial_shouldThrowWhenUsuarioNotFound() {
        UserDetails userDetails = new User("x@mail.com", "x", Collections.emptyList());
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> controller.obtenerHistorial(2L, 0, 20, userDetails));
    }

    @Test
    void obtenerHistorial_shouldDelegateToMensajeService() {
        Usuario usuario = Usuario.builder().id(1L).email("dra@mail.com").build();
        UserDetails userDetails = new User("dra@mail.com", "x", Collections.emptyList());
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(usuario));
        Page<Mensaje> page = new PageImpl<>(List.of());
        when(mensajeService.obtenerHistorial(eq(1L), eq(2L), any())).thenReturn(page);

        var response = controller.obtenerHistorial(2L, 0, 20, userDetails);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(page, response.getBody());
    }

    @Test
    void obtenerCanales_shouldDelegateToMensajeService_forPsiquiatra() {
        UserDetails userDetails = new User("dra@mail.com", "x", Collections.emptyList());
        Usuario medico = Usuario.builder().id(1L).email("dra@mail.com").rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        List<CanalPrioritarioDto> canales = List.of();
        when(mensajeService.obtenerCanalesPrioritarios("dra@mail.com")).thenReturn(canales);

        var response = controller.obtenerCanales(userDetails);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(canales, response.getBody());
        verify(mensajeService, never()).obtenerCanalesPaciente(any());
    }

    @Test
    void obtenerCanales_shouldDelegateToPatientChannels_forPaciente() {
        UserDetails userDetails = new User("paciente@mail.com", "x", Collections.emptyList());
        Usuario paciente = Usuario.builder().id(2L).email("paciente@mail.com").rol(Rol.PACIENTE).build();
        when(usuarioRepository.findByEmail("paciente@mail.com")).thenReturn(Optional.of(paciente));
        List<CanalPrioritarioDto> canales = List.of();
        when(mensajeService.obtenerCanalesPaciente("paciente@mail.com")).thenReturn(canales);

        var response = controller.obtenerCanales(userDetails);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(canales, response.getBody());
        verify(mensajeService, never()).obtenerCanalesPrioritarios(any());
    }

    @Test
    void obtenerCanalesVisitadores_shouldDelegateToMensajeService() {
        UserDetails userDetails = new User("dra@mail.com", "x", Collections.emptyList());
        List<CanalPrioritarioDto> canales = List.of();
        when(mensajeService.obtenerCanalesVisitadores("dra@mail.com")).thenReturn(canales);

        var response = controller.obtenerCanalesVisitadores(userDetails);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(canales, response.getBody());
    }
}
