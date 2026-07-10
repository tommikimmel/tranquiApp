package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.DashboardStatsDto;
import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MedicoServiceTest {

    @Autowired
    private MedicoService medicoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TarifaMedicoRepository tarifaRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    private Usuario medico;
    private Usuario paciente;
    private Turno turno;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Marta")
                .apellido("Rossi")
                .email("marta.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("12345")
                .precio(new BigDecimal("35000"))
                .sexo("F")
                .fechaNacimiento(LocalDate.of(1980, 5, 10))
                .cuil(27123456789L)
                .tipoDocumento("DNI")
                .numeroDocumento(12345678)
                .domicilioAtencion("Calle Falsa 123")
                .codigoReFeps(987654L)
                .matriculaTipo("Nacional")
                .matriculaProvincia("Córdoba")
                .matriculaNumero(12345)
                .fotoUrl("http://example.com/foto.jpg")
                .verificadoAdmin(true)
                .build();
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .email("pedro.paciente@gmail.com")
                .rol(Rol.PACIENTE)
                .build();

        usuarioRepository.save(medico);
        usuarioRepository.save(paciente);

        turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .precio(new BigDecimal("35000"))
                .build();
        turnoRepository.save(turno);
    }

    @AfterEach
    void tearDown() {
        turnoRepository.delete(turno);
        List<TarifaMedico> tarifas = tarifaRepository.findByMedicoId(medico.getId());
        tarifaRepository.deleteAll(tarifas);
        usuarioRepository.delete(medico);
        usuarioRepository.delete(paciente);
    }

    @Test
    void testObtenerMedicosActivos() {
        List<MedicoDto> result = medicoService.obtenerMedicosActivos();
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(m -> m.getEmail().equals(medico.getEmail())));
    }

    @Test
    void testObtenerPerfil() {
        MedicoDto result = medicoService.obtenerPerfil(medico.getEmail());
        assertNotNull(result);
        assertEquals(medico.getNombre() + " " + medico.getApellido(), result.getName());
    }

    @Test
    void testActualizarPerfil() {
        MedicoDto.TarifaDto tDto = MedicoDto.TarifaDto.builder()
                .id("particular")
                .label("Particular")
                .price(new BigDecimal("40000"))
                .enabled(true)
                .build();

        MedicoDto dto = MedicoDto.builder()
                .name("Lic. Marta Rossi Mod")
                .matricula("54321")
                .degree("Psiquiatra")
                .specialty("Ansiedad")
                .cuit("27-12345678-9")
                .price(new BigDecimal("40000"))
                .color("#FFFFFF")
                .tags(Arrays.asList("Ansiedad", "Depresion"))
                .tariffs(Arrays.asList(tDto))
                .build();

        MedicoDto result = medicoService.actualizarPerfil(medico.getEmail(), dto);
        assertNotNull(result);
        assertEquals("Lic. Marta Rossi Mod", result.getName());
        assertEquals("54321", result.getMatricula());
        assertEquals(2, result.getTags().size());
    }

    @Test
    void testObtenerStats() {
        DashboardStatsDto stats = medicoService.obtenerStats(medico.getEmail());
        assertNotNull(stats);
        assertEquals(1, stats.getSessionsToday());
        assertEquals(0, new BigDecimal("35000").compareTo(stats.getEarningsThisWeek()));
    }
}
