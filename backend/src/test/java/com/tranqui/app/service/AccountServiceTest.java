package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.MiCuentaDto;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario paciente;

    @BeforeEach
    void setUp() {
        paciente = usuarioRepository.save(Usuario.builder()
                .nombre("Pedro")
                .apellido("Gómez")
                .email("pedro.cuenta." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .password(passwordEncoder.encode("ClaveVieja1"))
                .build());
    }

    @AfterEach
    void tearDown() {
        usuarioRepository.delete(paciente);
    }

    @Test
    void obtenerMiCuenta_devuelveLosDatosDelUsuario() {
        MiCuentaDto dto = accountService.obtenerMiCuenta(paciente.getEmail());
        assertEquals(paciente.getId(), dto.getId());
        assertEquals("Pedro", dto.getNombre());
        assertTrue(dto.isTienePassword());
    }

    @Test
    void obtenerMiCuenta_usuarioInexistente_lanzaEntityNotFound() {
        assertThrows(EntityNotFoundException.class,
                () -> accountService.obtenerMiCuenta("no-existe-" + System.nanoTime() + "@gmail.com"));
    }

    @Test
    void actualizarDatosPersonales_normalizaTelefonoConCodigoDePais() {
        MiCuentaDto dto = MiCuentaDto.builder()
                .nombre("Pedro")
                .apellido("Gómez")
                .telefono("11 2222 3333")
                .build();

        MiCuentaDto actualizado = accountService.actualizarDatosPersonales(paciente.getEmail(), dto);

        assertEquals("+54 11 2222 3333", actualizado.getTelefono());
    }

    @Test
    void actualizarDatosPersonales_fotoDemasiadoGrande_lanzaIllegalArgument() {
        String fotoGigante = "data:image/png;base64," + "A".repeat(5 * 1024 * 1024);
        MiCuentaDto dto = MiCuentaDto.builder().nombre("Pedro").fotoUrl(fotoGigante).build();

        assertThrows(IllegalArgumentException.class,
                () -> accountService.actualizarDatosPersonales(paciente.getEmail(), dto));
    }

    @Test
    void actualizarPreferenciasNotificacion_persisteLaBanderaDeEmail() {
        MiCuentaDto dto = accountService.actualizarPreferenciasNotificacion(paciente.getEmail(), false);
        assertFalse(dto.isNotificacionesEmailHabilitadas());
    }

    @Test
    void cambiarPassword_conActualCorrecta_actualizaElHash() {
        accountService.cambiarPassword(paciente.getEmail(), "ClaveVieja1", "ClaveNueva2");

        Usuario actualizado = usuarioRepository.findByEmail(paciente.getEmail()).orElseThrow();
        assertTrue(passwordEncoder.matches("ClaveNueva2", actualizado.getPassword()));
    }

    @Test
    void cambiarPassword_conActualIncorrecta_lanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.cambiarPassword(paciente.getEmail(), "ClaveMala", "ClaveNueva2"));
        assertTrue(ex.getMessage().contains("incorrecta"));
    }

    @Test
    void cambiarPassword_nuevaDemasiadoDebil_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.cambiarPassword(paciente.getEmail(), "ClaveVieja1", "corta1"));
    }

    @Test
    void cambiarPassword_cuentaSoloGoogleSinPasswordPrevia_noExigeActual() {
        Usuario soloGoogle = usuarioRepository.save(Usuario.builder()
                .nombre("Google User")
                .email("solo.google." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .build());
        try {
            accountService.cambiarPassword(soloGoogle.getEmail(), null, "ClaveNueva2");
            Usuario actualizado = usuarioRepository.findByEmail(soloGoogle.getEmail()).orElseThrow();
            assertTrue(passwordEncoder.matches("ClaveNueva2", actualizado.getPassword()));
        } finally {
            usuarioRepository.delete(soloGoogle);
        }
    }

    @Test
    void eliminarCuenta_anonimizaEnVezDeBorrarLaFila() {
        Long idOriginal = paciente.getId();
        String emailOriginal = paciente.getEmail();

        accountService.eliminarCuenta(emailOriginal, "ClaveVieja1");

        Usuario anonimizado = usuarioRepository.findById(idOriginal).orElseThrow();
        assertEquals("Usuario eliminado", anonimizado.getNombre());
        assertNull(anonimizado.getApellido());
        assertNull(anonimizado.getPassword());
        assertTrue(anonimizado.isCuentaEliminada());
        assertNotNull(anonimizado.getAnonimizadoEn());
        assertNotEquals(emailOriginal, anonimizado.getEmail());
        assertTrue(anonimizado.getEmail().startsWith("deleted-" + idOriginal));
    }

    @Test
    void eliminarCuenta_passwordIncorrecta_lanzaIllegalArgumentYNoAnonimiza() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.eliminarCuenta(paciente.getEmail(), "ClaveMala"));

        Usuario intacto = usuarioRepository.findByEmail(paciente.getEmail()).orElseThrow();
        assertFalse(intacto.isCuentaEliminada());
        assertEquals("Pedro", intacto.getNombre());
    }
}
