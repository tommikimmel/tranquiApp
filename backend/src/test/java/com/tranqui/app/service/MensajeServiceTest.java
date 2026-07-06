package com.tranqui.app.service;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MensajeServiceTest {

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario medico;
    private Usuario paciente;
    private Mensaje mensaje;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Marta")
                .email("marta.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .email("pedro.paciente@gmail.com")
                .rol(Rol.PACIENTE)
                .build();

        usuarioRepository.save(medico);
        usuarioRepository.save(paciente);

        mensaje = Mensaje.builder()
                .remitente(paciente)
                .destinatario(medico)
                .contenido("Hola doc")
                .build();
        mensajeRepository.save(mensaje);
    }

    @AfterEach
    void tearDown() {
        if (mensaje != null && mensaje.getId() != null) {
            try { mensajeRepository.delete(mensaje); } catch (Exception e) {}
        }
        if (paciente != null && paciente.getId() != null) {
            try { usuarioRepository.delete(paciente); } catch (Exception e) {}
        }
        if (medico != null && medico.getId() != null) {
            try { usuarioRepository.delete(medico); } catch (Exception e) {}
        }
    }

    @Test
    void testGuardarMensaje() {
        MensajeDto dto = MensajeDto.builder()
                .destinatarioId(medico.getId())
                .contenido("Otro mensaje")
                .build();

        Mensaje saved = mensajeService.guardarMensaje(dto, paciente.getEmail());
        assertNotNull(saved);
        assertEquals("Otro mensaje", saved.getContenido());

        // clean up
        try { mensajeRepository.delete(saved); } catch (Exception e) {}
    }

    @Test
    void testObtenerHistorial() {
        Page<Mensaje> history = mensajeService.obtenerHistorial(paciente.getId(), medico.getId(), PageRequest.of(0, 10));
        assertNotNull(history);
        assertFalse(history.isEmpty());
    }

    @Test
    void testObtenerCanalesPrioritarios() {
        List<CanalPrioritarioDto> channels = mensajeService.obtenerCanalesPrioritarios(medico.getEmail());
        assertNotNull(channels);
    }

    @Test
    void testObtenerCanalesVisitadores() {
        List<CanalPrioritarioDto> channels = mensajeService.obtenerCanalesVisitadores(medico.getEmail());
        assertNotNull(channels);
    }
}
