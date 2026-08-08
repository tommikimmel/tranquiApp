package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.DisponibilidadDto;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DisponibilidadServiceTest {

    @Autowired
    private DisponibilidadService disponibilidadService;

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario medico;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Marta")
                .email("marta.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();
        usuarioRepository.save(medico);
    }

    @AfterEach
    void tearDown() {
        List<Disponibilidad> disp = disponibilidadRepository.findByMedicoId(medico.getId());
        disponibilidadRepository.deleteAll(disp);
        usuarioRepository.delete(medico);
    }

    @Test
    void testObtenerDisponibilidadesEmpty() {
        // medico here doesn't set ofrecePresencial/ofreceOnline, so isOfreceOnline() defaults
        // true and isOfrecePresencial() defaults false — ONLINE is the prioritized modalidad.
        List<DisponibilidadDto> result = disponibilidadService.obtenerDisponibilidades(medico.getEmail(), Modalidad.ONLINE);
        assertFalse(result.isEmpty());
        assertEquals(10, result.size());
    }

    @Test
    void testObtenerDisponibilidadesWithData() {
        disponibilidadService.obtenerDisponibilidades(medico.getEmail(), Modalidad.ONLINE); // initializes default
        List<DisponibilidadDto> result = disponibilidadService.obtenerDisponibilidades(medico.getEmail(), Modalidad.ONLINE);
        assertEquals(10, result.size());
    }

    @Test
    void testGuardarDisponibilidades() {
        List<DisponibilidadDto> dtos = Arrays.asList(
                new DisponibilidadDto(1, "09:00", "12:00")
        );
        List<DisponibilidadDto> result = disponibilidadService.guardarDisponibilidades(medico.getEmail(), Modalidad.ONLINE, dtos);
        assertEquals(1, result.size());
        assertEquals(1, disponibilidadRepository.findByMedicoId(medico.getId()).size());
    }
}
