package com.tranqui.app.controller;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerUnitTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private com.tranqui.app.repository.SubscriptionRepository subscriptionRepository;
    @Mock private com.tranqui.app.service.SubscriptionService subscriptionService;

    @InjectMocks
    private AdminController controller;

    @Test
    void getUsers_shouldReturnAllUsers() {
        when(usuarioRepository.findAll()).thenReturn(List.of(Usuario.builder().id(1L).build()));

        ResponseEntity<List<Usuario>> response = controller.getUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void updateRol_shouldThrowWhenUsuarioNotFound() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> controller.updateRol(1L, Map.of("rol", "ADMIN")));
    }

    @Test
    void updateRol_shouldReturnBadRequestForInvalidRol() {
        Usuario usuario = Usuario.builder().id(1L).rol(Rol.PACIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = controller.updateRol(1L, Map.of("rol", "NO_EXISTE"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Rol inválido", response.getBody());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void updateRol_shouldSetVerificadoAdminTrueWhenPromotingToPsiquiatra() {
        Usuario usuario = Usuario.builder().id(1L).rol(Rol.PACIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = controller.updateRol(1L, Map.of("rol", "psiquiatra"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Rol.PSIQUIATRA, usuario.getRol());
        assertEquals(Boolean.TRUE, usuario.getVerificadoAdmin());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void updateRol_shouldNotTouchVerificadoAdminForOtherRoles() {
        Usuario usuario = Usuario.builder().id(1L).rol(Rol.PACIENTE).verificadoAdmin(null).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        controller.updateRol(1L, Map.of("rol", "ADMIN"));

        assertEquals(Rol.ADMIN, usuario.getRol());
        assertNull(usuario.getVerificadoAdmin());
    }

    @Test
    void verifyProfessional_shouldThrowWhenUsuarioNotFound() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> controller.verifyProfessional(1L));
    }

    @Test
    void verifyProfessional_shouldSetRolAndVerify() {
        Usuario usuario = Usuario.builder().id(1L).rol(Rol.PACIENTE).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = controller.verifyProfessional(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Rol.PSIQUIATRA, usuario.getRol());
        assertEquals(Boolean.TRUE, usuario.getVerificadoAdmin());
    }

    @Test
    void rejectProfessional_shouldThrowWhenUsuarioNotFound() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> controller.rejectProfessional(1L));
    }

    @Test
    void rejectProfessional_shouldSetVerificadoAdminFalse() {
        Usuario usuario = Usuario.builder().id(1L).rol(Rol.PSIQUIATRA).verificadoAdmin(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = controller.rejectProfessional(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.FALSE, usuario.getVerificadoAdmin());
    }
}
