package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void whenUserExists_shouldLoadUserByUsername() {
        Usuario mockUsuario = Usuario.builder()
                .nombre("Juan")
                .email("juan@test.com")
                .rol(Rol.PACIENTE)
                .build();

        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(mockUsuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("juan@test.com");

        assertNotNull(userDetails);
        assertEquals("juan@test.com", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PACIENTE")));
    }

    @Test
    void whenUserDoesNotExist_shouldThrowException() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userDetailsService.loadUserByUsername("unknown@test.com");
        });
    }

    // CustomUserDetailsService doesn't check cuentaEliminada — that gate lives in
    // AuthController.login (blocks the initial login). Once logged in, the JWT stays valid until
    // it expires, and JwtAuthenticationFilter calls this service on every subsequent request, so
    // a soft-deleted account's existing session still resolves to a UserDetails today. Documented
    // here rather than asserted as "correct" — it's the current behavior, not a guarantee.
    @Test
    void whenCuentaEliminada_shouldStillLoadUserDetails() {
        Usuario mockUsuario = Usuario.builder()
                .nombre("Juan")
                .email("juan@test.com")
                .rol(Rol.PACIENTE)
                .cuentaEliminada(true)
                .build();

        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(mockUsuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("juan@test.com");

        assertNotNull(userDetails);
        assertEquals("juan@test.com", userDetails.getUsername());
    }

    // The service delegates straight to UsuarioRepository.findByEmail without any
    // trim/lowercase normalization itself — that normalization happens upstream, in
    // AuthController.login, before the JWT is even generated. So if a differently-cased email
    // ever reached this service directly, it simply wouldn't match what's stored.
    @Test
    void whenEmailCasingDiffersFromStored_shouldNotFindUser() {
        when(usuarioRepository.findByEmail("Juan@Test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("Juan@Test.com"));
    }
}
