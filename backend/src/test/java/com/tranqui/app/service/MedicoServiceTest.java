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

    @Test
    void testObtenerPerfil_promotesNonPsiquiatraRoleToPsiquiatra() {
        Usuario misconfigured = usuarioRepository.save(Usuario.builder()
                .nombre("Alguien").email("alguien.mal-rol@gmail.com").rol(Rol.PACIENTE).build());
        try {
            medicoService.obtenerPerfil("alguien.mal-rol@gmail.com");

            Usuario updated = usuarioRepository.findByEmail("alguien.mal-rol@gmail.com").orElseThrow();
            assertEquals(Rol.PSIQUIATRA, updated.getRol());
        } finally {
            tarifaRepository.deleteAll(tarifaRepository.findByMedicoId(misconfigured.getId()));
            usuarioRepository.delete(misconfigured);
        }
    }

    @Test
    void testActualizarPerfil_withMatriculaInfoAndExistingTarifa() {
        // Pre-seed an existing tarifa so actualizarPerfil takes the "update" branch, not "create".
        TarifaMedico existente = tarifaRepository.save(TarifaMedico.builder()
                .medico(medico).servicioId("particular").label("Particular").precio(new BigDecimal("1")).habilitado(false).build());

        MedicoDto.TarifaDto tDto = MedicoDto.TarifaDto.builder()
                .id("particular").label("Particular").price(new BigDecimal("50000")).enabled(true).build();

        MedicoDto.MatriculaInfoDto matInfo = MedicoDto.MatriculaInfoDto.builder()
                .tipo("MP").provincia("Buenos Aires").numero(999)
                .especialidad(MedicoDto.EspecialidadDto.builder().textoLibre("Psiquiatria Infantil").build())
                .asociada(MedicoDto.AsociadaDto.builder().tipo("MN").provincia("Santa Fe").numero(111).build())
                .build();

        MedicoDto dto = MedicoDto.builder()
                .name("Marta").matriculaInfo(matInfo).tariffs(List.of(tDto)).build();

        medicoService.actualizarPerfil(medico.getEmail(), dto);

        Usuario updated = usuarioRepository.findByEmail(medico.getEmail()).orElseThrow();
        assertEquals("MP", updated.getMatriculaTipo());
        assertEquals("Buenos Aires", updated.getMatriculaProvincia());
        assertEquals(999, updated.getMatriculaNumero());
        assertEquals("Psiquiatria Infantil", updated.getMatriculaEspecialidad());
        assertEquals("MN", updated.getMatriculaAsocTipo());
        assertEquals("Santa Fe", updated.getMatriculaAsocProvincia());
        assertEquals(111, updated.getMatriculaAsocNumero());
        assertEquals("999", updated.getMatricula());

        TarifaMedico tarifaActualizada = tarifaRepository.findById(existente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50000").compareTo(tarifaActualizada.getPrecio()));
        assertTrue(tarifaActualizada.isHabilitado());
    }

    @Test
    void testConstruirMedicoDto_initialsFallbackWhenNameIsOnlyATitle() {
        Usuario soloTitulo = usuarioRepository.save(Usuario.builder()
                .nombre("Dr.").email("solo.titulo@gmail.com").rol(Rol.PSIQUIATRA).build());
        try {
            MedicoDto result = medicoService.obtenerPerfil("solo.titulo@gmail.com");
            assertEquals("DR", result.getInitials());
        } finally {
            tarifaRepository.deleteAll(tarifaRepository.findByMedicoId(soloTitulo.getId()));
            usuarioRepository.delete(soloTitulo);
        }
    }

    @Test
    void testObtenerStats_fewerSessionsTodayThanYesterday_reportsNegativeChange() {
        Usuario m = usuarioRepository.save(Usuario.builder().nombre("M2").email("m2@gmail.com").rol(Rol.PSIQUIATRA).build());
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("P2").email("p2@gmail.com").rol(Rol.PACIENTE).build());
        Turno hoy = turnoRepository.save(turnoFor(m, p, LocalDate.now()));
        Turno ayer1 = turnoRepository.save(turnoFor(m, p, LocalDate.now().minusDays(1)));
        Turno ayer2 = turnoRepository.save(turnoFor(m, p, LocalDate.now().minusDays(1)));
        try {
            DashboardStatsDto stats = medicoService.obtenerStats(m.getEmail());
            assertEquals("-1 vs ayer", stats.getSessionsTodayChange());
        } finally {
            turnoRepository.delete(hoy); turnoRepository.delete(ayer1); turnoRepository.delete(ayer2);
            usuarioRepository.delete(m); usuarioRepository.delete(p);
        }
    }

    @Test
    void testObtenerStats_sameSessionsTodayAndYesterday_reportsEqualChange() {
        Usuario m = usuarioRepository.save(Usuario.builder().nombre("M3").email("m3@gmail.com").rol(Rol.PSIQUIATRA).build());
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("P3").email("p3@gmail.com").rol(Rol.PACIENTE).build());
        Turno hoy = turnoRepository.save(turnoFor(m, p, LocalDate.now()));
        Turno ayer = turnoRepository.save(turnoFor(m, p, LocalDate.now().minusDays(1)));
        try {
            DashboardStatsDto stats = medicoService.obtenerStats(m.getEmail());
            assertEquals("igual que ayer", stats.getSessionsTodayChange());
        } finally {
            turnoRepository.delete(hoy); turnoRepository.delete(ayer);
            usuarioRepository.delete(m); usuarioRepository.delete(p);
        }
    }

    @Test
    void testObtenerStats_noEarningsEitherWeek_reportsZeroPercent() {
        Usuario m = usuarioRepository.save(Usuario.builder().nombre("M4").email("m4@gmail.com").rol(Rol.PSIQUIATRA).build());
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("P4").email("p4@gmail.com").rol(Rol.PACIENTE).build());
        // Unconfirmed appointment: excluded from earnings calculations entirely.
        Turno pendiente = turnoRepository.save(Turno.builder()
                .medico(m).paciente(p).fecha(LocalDate.now())
                .horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(11, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO).precio(new BigDecimal("10000"))
                .build());
        try {
            DashboardStatsDto stats = medicoService.obtenerStats(m.getEmail());
            assertEquals("0% vs sem. anterior", stats.getEarningsThisWeekChange());
        } finally {
            turnoRepository.delete(pendiente);
            usuarioRepository.delete(m); usuarioRepository.delete(p);
        }
    }

    @Test
    void testObtenerStats_earningsIncreaseVsLastWeek_reportsPositivePercent() {
        Usuario m = usuarioRepository.save(Usuario.builder().nombre("M5").email("m5@gmail.com").rol(Rol.PSIQUIATRA).build());
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("P5").email("p5@gmail.com").rol(Rol.PACIENTE).build());
        Turno estaSemana = turnoRepository.save(confirmadoCon(m, p, LocalDate.now(), new BigDecimal("20000")));
        Turno semanaPasada = turnoRepository.save(confirmadoCon(m, p, LocalDate.now().minusWeeks(1), new BigDecimal("10000")));
        try {
            DashboardStatsDto stats = medicoService.obtenerStats(m.getEmail());
            assertTrue(stats.getEarningsThisWeekChange().startsWith("+"));
        } finally {
            turnoRepository.delete(estaSemana); turnoRepository.delete(semanaPasada);
            usuarioRepository.delete(m); usuarioRepository.delete(p);
        }
    }

    @Test
    void testObtenerStats_earningsDecreaseVsLastWeek_reportsNegativePercent() {
        Usuario m = usuarioRepository.save(Usuario.builder().nombre("M6").email("m6@gmail.com").rol(Rol.PSIQUIATRA).build());
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("P6").email("p6@gmail.com").rol(Rol.PACIENTE).build());
        Turno estaSemana = turnoRepository.save(confirmadoCon(m, p, LocalDate.now(), new BigDecimal("5000")));
        Turno semanaPasada = turnoRepository.save(confirmadoCon(m, p, LocalDate.now().minusWeeks(1), new BigDecimal("20000")));
        try {
            DashboardStatsDto stats = medicoService.obtenerStats(m.getEmail());
            assertTrue(stats.getEarningsThisWeekChange().startsWith("-"));
        } finally {
            turnoRepository.delete(estaSemana); turnoRepository.delete(semanaPasada);
            usuarioRepository.delete(m); usuarioRepository.delete(p);
        }
    }

    private Turno turnoFor(Usuario m, Usuario p, LocalDate fecha) {
        return Turno.builder()
                .medico(m).paciente(p).fecha(fecha)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.CONFIRMADO).precio(new BigDecimal("1000"))
                .build();
    }

    private Turno confirmadoCon(Usuario m, Usuario p, LocalDate fecha, BigDecimal precio) {
        return Turno.builder()
                .medico(m).paciente(p).fecha(fecha)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.CONFIRMADO).precio(precio)
                .build();
    }
}
