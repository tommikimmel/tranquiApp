package com.tranqui.app.service;

import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.repository.RecetaRepository;
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
class RecetaServiceTest {

    @Autowired
    private RecetaService recetaService;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario medico;
    private Usuario paciente;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Marta")
                .email("marta.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("12345")
                .build();
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .email("pedro.paciente@gmail.com")
                .telefono("+5491100001111")
                .rol(Rol.PACIENTE)
                .build();
        usuarioRepository.save(medico);
        usuarioRepository.save(paciente);
    }

    @AfterEach
    void tearDown() {
        List<Receta> recetas = recetaRepository.findAll();
        recetaRepository.deleteAll(recetas);
        usuarioRepository.delete(medico);
        usuarioRepository.delete(paciente);
    }

    @Test
    void testEmitirReceta() {
        RecetaDto.MedicamentoDto med1 = new RecetaDto.MedicamentoDto();
        med1.setName("Ibuprofeno");
        med1.setDosage("400mg");
        med1.setFrequency("Cada 8hs");
        med1.setDuration("3 dias");

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Dolor")
                .notes("Tomar con comida")
                .medications(Arrays.asList(med1))
                .build();

        Receta result = recetaService.emitirReceta(medico.getEmail(), dto);
        assertNotNull(result);
        assertEquals(paciente.getId(), result.getPaciente().getId());
        assertEquals(medico.getId(), result.getMedico().getId());
        assertNotNull(result.getPdfUrl());
    }
}
