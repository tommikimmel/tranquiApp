package com.tranqui.app.service;

import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.model.dto.RecetaResponseDto;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
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
    private Usuario otroPaciente;

    // RecetaService.emitirReceta blocks on a specific set of "datos faltantes" before ever
    // calling QBI2 — every field below is required for the happy-path tests to actually reach
    // Qbi2RecipeClientMock instead of failing validation first.
    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Marta")
                .apellido("Rossi")
                .email("marta.medico." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("12345")
                .tipoDocumento("DNI")
                .numeroDocumento(20123456)
                .codigoRefeps("123456789012")
                .domicilioAtencion("Av. Siempre Viva 742, Córdoba")
                .build();
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .apellido("Gómez")
                .email("pedro.paciente." + System.nanoTime() + "@gmail.com")
                .telefono("+5491100001111")
                .rol(Rol.PACIENTE)
                .tipoDocumento("DNI")
                .numeroDocumento(30123456)
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .build();
        otroPaciente = Usuario.builder()
                .nombre("Otro Paciente")
                .email("otro.paciente." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        usuarioRepository.save(medico);
        usuarioRepository.save(paciente);
        usuarioRepository.save(otroPaciente);
    }

    @AfterEach
    void tearDown() {
        List<Receta> recetas = recetaRepository.findAll();
        recetaRepository.deleteAll(recetas);
        usuarioRepository.delete(medico);
        usuarioRepository.delete(paciente);
        usuarioRepository.delete(otroPaciente);
    }

    private RecetaDto.MedicamentoDto medicamento(String nombre, String laboratorio) {
        RecetaDto.MedicamentoDto med = new RecetaDto.MedicamentoDto();
        med.setName(nombre);
        med.setDosage("400mg");
        med.setFrequency("Cada 8hs");
        med.setDuration("3 dias");
        med.setLaboratorio(laboratorio);
        return med;
    }

    @Test
    void testEmitirReceta() {
        RecetaDto.MedicamentoDto med1 = medicamento("Ibuprofeno", null);

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Dolor")
                .notes("Tomar con comida")
                .medications(Arrays.asList(med1))
                .build();

        RecetaResponseDto result = recetaService.emitirReceta(medico.getEmail(), dto);
        assertNotNull(result);
        assertEquals(paciente.getId(), result.getPaciente().getId());
        assertEquals(medico.getId(), result.getMedico().getId());
        assertNotNull(result.getPdfUrl());
    }

    @Test
    void testEmitirReceta_persisteLaboratoriosCargadosPorElMedico() {
        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Ansiedad")
                .notes("Control en 30 días")
                .medications(Arrays.asList(
                        medicamento("Escitalopram", "Bagó"),
                        medicamento("Clonazepam", null),
                        medicamento("Sertralina", "Gador")))
                .build();

        RecetaResponseDto result = recetaService.emitirReceta(medico.getEmail(), dto);

        assertNotNull(result.getLaboratorios());
        assertEquals(2, result.getLaboratorios().size());
        assertTrue(result.getLaboratorios().contains("Bagó"));
        assertTrue(result.getLaboratorios().contains("Gador"));
        assertTrue(result.getMedicamentos().contains("Laboratorio: Bagó"));
    }

    @Test
    void testEmitirReceta_faltaDniDelPaciente_lanzaRecetaElectronicaException() {
        Usuario pacienteSinDni = usuarioRepository.save(Usuario.builder()
                .nombre("Sin Dni")
                .email("sin.dni." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(pacienteSinDni.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medico.getEmail(), dto));
            assertTrue(ex.getMessage().contains("DNI del paciente"));
        } finally {
            usuarioRepository.delete(pacienteSinDni);
        }
    }

    @Test
    void testObtenerMisRecetas_devuelveLoEmitidoParaElMedico() {
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        List<RecetaResponseDto> misRecetas = recetaService.obtenerMisRecetas(medico.getEmail());

        assertFalse(misRecetas.isEmpty());
        assertTrue(misRecetas.stream().anyMatch(r -> r.getPaciente().getId().equals(paciente.getId())));
    }

    @Test
    void testObtenerRecetaPorId_usuarioAjeno_lanzaAccessDenied() {
        RecetaResponseDto receta = recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        assertThrows(AccessDeniedException.class,
                () -> recetaService.obtenerRecetaPorId(receta.getId(), otroPaciente.getEmail()));
    }
}
