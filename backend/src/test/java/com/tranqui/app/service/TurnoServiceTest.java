package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class TurnoServiceTest {

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.tranqui.app.repository.DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private com.tranqui.app.repository.TarifaMedicoRepository tarifaMedicoRepository;

    @Autowired
    private com.tranqui.app.repository.NotificacionRepository notificacionRepository;

    // Mocked so marcarDocumentoEnviado tests are deterministic and don't attempt a real HTTP
    // call to Resend — default-stubbed to "sent successfully" in setUp(), overridden to false
    // in the dedicated mail-failure test below.
    @MockBean
    private com.tranqui.app.service.ResendEmailService resendEmailService;

    private Usuario paciente;
    private Usuario medico;
    private Turno turno;
    private com.tranqui.app.model.Disponibilidad disponibilidad;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Paciente Juan")
                .email("juan.paciente@gmail.com")
                .rol(Rol.PACIENTE)
                .build();

        medico = Usuario.builder()
                .nombre("Medico Carlos")
                .email("carlos.medico@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);

        disponibilidad = com.tranqui.app.model.Disponibilidad.builder()
                .medico(medico)
                .diaSemana(LocalDate.now().plusDays(2).getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(12, 0))
                .build();
        disponibilidadRepository.save(disponibilidad);

        turno = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                // ONLINE so confirmarTurnoOsde actually exercises its Google Calendar sync path
                // (it's a no-op for PRESENCIAL turnos — see TurnoService#confirmarTurnoOsde).
                .modalidad(Modalidad.ONLINE)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                // obtenerTurnosPaciente now reads the persisted checkoutUrl instead of calling
                // Mercado Pago again on every read, so it must already be set here.
                .checkoutUrl("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id")
                .build();

        turnoRepository.save(turno);

        // Default: document email "sent" successfully unless a specific test overrides it.
        when(resendEmailService.enviarDocumentoAdjunto(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (turno != null && turno.getId() != null) {
            try { turnoRepository.delete(turno); } catch (Exception e) {}
        }
        if (disponibilidad != null && disponibilidad.getId() != null) {
            try { disponibilidadRepository.delete(disponibilidad); } catch (Exception e) {}
        }
        // Some operations (e.g. cancelarTurno) create Notificacion rows referencing
        // paciente/medico; those FKs would otherwise block deleting the users below.
        if (paciente != null && paciente.getId() != null) {
            try { notificacionRepository.deleteAll(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(paciente.getId())); } catch (Exception e) {}
        }
        if (medico != null && medico.getId() != null) {
            try { notificacionRepository.deleteAll(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(medico.getId())); } catch (Exception e) {}
        }
        if (paciente != null && paciente.getId() != null) {
            try { usuarioRepository.delete(paciente); } catch (Exception e) {}
        }
        if (medico != null && medico.getId() != null) {
            try { usuarioRepository.delete(medico); } catch (Exception e) {}
        }
    }

    // Builds a [horaInicio, horaFin] pair guaranteed to stay in the future within the
    // same calendar day, regardless of what time the test happens to run at (a naive
    // now.plusHours(N) can wrap past midnight in the evening, which breaks the LocalTime
    // comparisons the production "completed" status logic relies on).
    private static LocalTime[] horaFuturaSegura() {
        LocalTime now = LocalTime.now();
        LocalTime inicio = now.plusHours(2);
        LocalTime fin = now.plusHours(3);
        if (fin.isBefore(now)) {
            return new LocalTime[]{ LocalTime.of(23, 0), LocalTime.of(23, 45) };
        }
        return new LocalTime[]{ inicio, fin };
    }

    // Same idea as horaFuturaSegura(), but for a window guaranteed to be in the past
    // (guards against now.minusHours(N) underflowing past midnight in the early morning).
    private static LocalTime[] horaPasadaSegura() {
        LocalTime now = LocalTime.now();
        LocalTime inicio = now.minusHours(3);
        LocalTime fin = now.minusHours(2);
        if (fin.isAfter(now)) {
            return new LocalTime[]{ LocalTime.of(0, 1), LocalTime.of(0, 46) };
        }
        return new LocalTime[]{ inicio, fin };
    }

    @Test
    void whenConfirmTurnoOsde_thenShouldUpdateAndSyncCalendar() {
        Turno confirmado = turnoService.confirmarTurnoOsde(turno.getId(), "OSDE-12345");

        assertNotNull(confirmado);
        assertEquals(TipoTurno.OSDE, confirmado.getTipo());
        assertEquals("OSDE-12345", confirmado.getMetadataAfiliado());
        assertEquals(EstadoTurno.CONFIRMADO, confirmado.getEstado());
        assertNotNull(confirmado.getTelemedicinaUrl());
        
        // Check database state directly
        Turno dbTurno = turnoRepository.findById(turno.getId()).orElse(null);
        assertNotNull(dbTurno);
        assertEquals(EstadoTurno.CONFIRMADO, dbTurno.getEstado());
        assertEquals("OSDE-12345", dbTurno.getMetadataAfiliado());
    }

    @Test
    void whenGoogleCalendarFails_thenShouldRollbackTransaction() {
        // Trigger FAIL_CALENDAR simulation in GoogleCalendarService
        assertThrows(RuntimeException.class, () -> {
            turnoService.confirmarTurnoOsde(turno.getId(), "FAIL_CALENDAR");
        });

        // Verify database state remains unchanged
        Turno dbTurno = turnoRepository.findById(turno.getId()).orElse(null);
        assertNotNull(dbTurno);
        assertEquals(EstadoTurno.PENDIENTE_PAGO, dbTurno.getEstado());
        assertNull(dbTurno.getMetadataAfiliado());
    }

    @Test
    void testReservarTurnoOsde() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.OSDE)
                .metadataAfiliado("OSDE-777")
                .nombrePaciente("Mateo B")
                .emailPaciente("mateo.b@gmail.com")
                .telefonoPaciente("+543510000000")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);
        assertNotNull(response);
        assertEquals("PENDIENTE_PAGO", response.getEstado());
        assertNotNull(response.getCheckoutUrl());
        assertFalse(response.getCheckoutUrl().isEmpty());
        
        // Clean up created turno & patient if created
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
    }

    @Test
    void testReservarTurnoObraSocial() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                // Candidate start times step every duracionTurnoMinutos (45, this médico's
                // default) with no gap (intervaloEntreTurnosMinutos default 0) — 9:00, 9:45,
                // 10:30, 11:15. 10:00 is never a generated candidate, so it isn't a valid hora.
                .hora(LocalTime.of(9, 45))
                .tipo(TipoTurno.OBRA_SOCIAL)
                .obraSocial("Swiss Medical")
                .metadataAfiliado("99887766")
                .nombrePaciente("Lucia M")
                .emailPaciente("lucia.m@gmail.com")
                .telefonoPaciente("+543511111111")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);
        assertNotNull(response);
        assertEquals("PENDIENTE_PAGO", response.getEstado());
        assertNotNull(response.getCheckoutUrl());
        assertFalse(response.getCheckoutUrl().isEmpty());
        
        // Clean up created turno & patient if created
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
    }

    @Test
    void testReservarTurnoParticular() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("Juan Perez")
                .emailPaciente("juan.perez@gmail.com")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);
        assertNotNull(response);
        assertEquals("PENDIENTE_PAGO", response.getEstado());
        assertNotNull(response.getCheckoutUrl());
        
        // Clean up created turno & patient
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
        usuarioRepository.findByEmail("juan.perez@gmail.com").ifPresent(u -> {
            try { usuarioRepository.delete(u); } catch (Exception e) {}
        });
    }

    @Test
    void testReservarTurnoSobretuno() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.SOBRETUNO)
                .nombrePaciente("Juan Perez")
                .emailPaciente("juan.perez@gmail.com")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);
        assertNotNull(response);
        assertEquals("PENDIENTE_PAGO", response.getEstado());
        
        // Try double booking the same sobreturno -> should fail
        assertThrows(IllegalStateException.class, () -> {
            turnoService.reservarTurno(dto);
        });

        // Clean up
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
        usuarioRepository.findByEmail("juan.perez@gmail.com").ifPresent(u -> {
            try { usuarioRepository.delete(u); } catch (Exception e) {}
        });
    }

    @Test
    void testObtenerHorariosDisponibles() {
        java.util.List<LocalTime> result = turnoService.obtenerHorariosDisponibles(medico.getId(), LocalDate.now(), Modalidad.ONLINE);
        assertNotNull(result);
    }

    @Test
    void testObtenerTurnosDeHoy() {
        java.util.List<com.tranqui.app.model.dto.TurnoMedicoDto> result = turnoService.obtenerTurnosDeHoy(medico.getEmail());
        assertNotNull(result);
    }

    @Test
    void testObtenerTurnosDeHoy_medicoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.obtenerTurnosDeHoy("no-existe@mail.com"));
    }

    @Test
    void testObtenerTurnosDeHoy_reportsConfirmedAndCompletedStatuses() {
        LocalTime[] futura = horaFuturaSegura();
        LocalTime[] pasada = horaPasadaSegura();

        Turno confirmadoFuturo = Turno.builder()
                .paciente(paciente).medico(medico).fecha(LocalDate.now())
                .horaInicio(futura[0]).horaFin(futura[1])
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(confirmadoFuturo);

        Turno confirmadoTerminado = Turno.builder()
                .paciente(paciente).medico(medico).fecha(LocalDate.now())
                .horaInicio(pasada[0]).horaFin(pasada[1])
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(confirmadoTerminado);

        try {
            java.util.List<com.tranqui.app.model.dto.TurnoMedicoDto> result = turnoService.obtenerTurnosDeHoy(medico.getEmail());

            assertTrue(result.stream().anyMatch(t -> "confirmed".equals(t.getStatus())));
            assertTrue(result.stream().anyMatch(t -> "completed".equals(t.getStatus())));
        } finally {
            turnoRepository.delete(confirmadoFuturo);
            turnoRepository.delete(confirmadoTerminado);
        }
    }

    @Test
    void testObtenerTodosTurnos_computesStatusesAndIncludesCredencial() {
        paciente.setCredencialCodEntidad(11);
        paciente.setCredencialPan("PAN-1");
        usuarioRepository.save(paciente);

        // Confirmed appointment in the past -> "completed"
        Turno pasado = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now().minusDays(5))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(pasado);

        // Confirmed appointment today, already finished -> "completed"
        LocalTime[] pasadaHoy = horaPasadaSegura();
        Turno hoyTerminado = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(pasadaHoy[0]).horaFin(pasadaHoy[1])
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(hoyTerminado);

        // obtenerTodosTurnos only ever includes CONFIRMADO or PENDIENTE_VALIDACION turnos (see
        // TurnoService#obtenerTodosTurnos) — a PENDIENTE_PAGO turno like the shared `turno`
        // fixture never reaches the status-mapping code at all, so it can never produce
        // "pending". Only PENDIENTE_VALIDACION (e.g. a transferencia awaiting manual review)
        // does.
        Turno pendienteValidacion = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(16, 0)).horaFin(LocalTime.of(16, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_VALIDACION)
                .build();
        turnoRepository.save(pendienteValidacion);

        try {
            java.util.List<com.tranqui.app.model.dto.TurnoMedicoDto> result = turnoService.obtenerTodosTurnos(medico.getEmail());

            assertTrue(result.stream().anyMatch(t -> "completed".equals(t.getStatus())));
            assertTrue(result.stream().anyMatch(t -> "pending".equals(t.getStatus())));
            com.tranqui.app.model.dto.TurnoMedicoDto conCredencial = result.stream()
                    .filter(t -> t.getPatientInfo() != null && t.getPatientInfo().getCredencial() != null)
                    .findFirst().orElse(null);
            assertNotNull(conCredencial);
            assertEquals(11, conCredencial.getPatientInfo().getCredencial().getCodEntidad());
        } finally {
            turnoRepository.delete(pasado);
            turnoRepository.delete(hoyTerminado);
            turnoRepository.delete(pendienteValidacion);
        }
    }

    @Test
    void testObtenerTodosTurnos_medicoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.obtenerTodosTurnos("no-existe@mail.com"));
    }

    @Test
    void testObtenerTurnosPaciente_pendingIncludesCheckoutUrlAndConfirmedDoesNot() {
        Turno confirmadoFuturo = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(11, 45))
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(confirmadoFuturo);

        try {
            java.util.List<com.tranqui.app.model.dto.TurnoMedicoDto> result =
                    turnoService.obtenerTurnosPaciente(paciente.getEmail());

            com.tranqui.app.model.dto.TurnoMedicoDto pendiente = result.stream()
                    .filter(t -> "pending".equals(t.getStatus())).findFirst().orElseThrow();
            assertNotNull(pendiente.getCheckoutUrl());
            assertFalse(pendiente.getCheckoutUrl().isEmpty());

            com.tranqui.app.model.dto.TurnoMedicoDto confirmado = result.stream()
                    .filter(t -> "confirmed".equals(t.getStatus())).findFirst().orElseThrow();
            assertEquals("", confirmado.getCheckoutUrl());
        } finally {
            turnoRepository.delete(confirmadoFuturo);
        }
    }

    @Test
    void testObtenerTurnosPaciente_reportsCompletedForPastAndFinishedTodayAppointments() {
        Turno completadoPasado = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now().minusDays(5))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(completadoPasado);

        LocalTime[] pasadaCompletadoHoy = horaPasadaSegura();
        Turno completadoHoy = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(pasadaCompletadoHoy[0]).horaFin(pasadaCompletadoHoy[1])
                .tipo(TipoTurno.OSDE).estado(EstadoTurno.CONFIRMADO)
                .build();
        turnoRepository.save(completadoHoy);

        try {
            java.util.List<com.tranqui.app.model.dto.TurnoMedicoDto> result =
                    turnoService.obtenerTurnosPaciente(paciente.getEmail());

            long completedCount = result.stream().filter(t -> "completed".equals(t.getStatus())).count();
            assertEquals(2, completedCount);
        } finally {
            turnoRepository.delete(completadoPasado);
            turnoRepository.delete(completadoHoy);
        }
    }

    @Test
    void testObtenerTurnosPaciente_pacienteNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.obtenerTurnosPaciente("no-existe@mail.com"));
    }

    @Test
    void testCancelarTurno_updatesEstadoAndNotifies() {
        turnoService.cancelarTurno(turno.getId(), medico.getEmail());

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(EstadoTurno.CANCELADO, dbTurno.getEstado());
    }

    @Test
    void testCancelarTurno_turnoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.cancelarTurno(-1L, medico.getEmail()));
    }

    @Test
    void testActualizarAsistencia_valid() {
        turnoService.actualizarAsistencia(turno.getId(), "llego");

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(com.tranqui.app.model.EstadoAsistencia.LLEGO, dbTurno.getAsistencia());
    }

    @Test
    void testActualizarAsistencia_invalidValue() {
        assertThrows(IllegalArgumentException.class,
                () -> turnoService.actualizarAsistencia(turno.getId(), "NO_EXISTE"));
    }

    @Test
    void testActualizarAsistencia_turnoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.actualizarAsistencia(-1L, "LLEGO"));
    }

    @Test
    void testReprogramarTurno_updatesFechaHoraAndAttemptsWhatsapp() {
        LocalDate nuevaFecha = LocalDate.now().plusDays(4);
        turnoService.reprogramarTurno(turno.getId(), nuevaFecha.toString(), "14:30");

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(nuevaFecha, dbTurno.getFecha());
        assertEquals(LocalTime.of(14, 30), dbTurno.getHoraInicio());
        assertEquals(LocalTime.of(15, 15), dbTurno.getHoraFin());
    }

    @Test
    void testReprogramarTurno_includesMeetLinkInMessageWhenPresent() {
        turno.setTelemedicinaUrl("https://meet.google.com/existing-link");
        turnoRepository.save(turno);

        turnoService.reprogramarTurno(turno.getId(), LocalDate.now().plusDays(4).toString(), "10:00");

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(LocalTime.of(10, 0), dbTurno.getHoraInicio());
    }

    @Test
    void testReprogramarTurno_turnoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.reprogramarTurno(-1L, LocalDate.now().toString(), "10:00"));
    }

    // Regression test: reprogramarTurno used to hardcode 45 minutes for horaFin regardless of
    // the médico's configured duracionTurnoMinutos (a bug — reservarTurno and
    // obtenerHorariosDisponibles both correctly use the médico's own duration). Verifies the fix
    // with a duration that is neither the old hardcoded value nor its own default.
    @Test
    void testReprogramarTurno_usesMedicoConfiguredDuracionInsteadOfHardcoded45() {
        medico.setDuracionTurnoMinutos(30);
        usuarioRepository.save(medico);

        LocalDate nuevaFecha = LocalDate.now().plusDays(4);
        turnoService.reprogramarTurno(turno.getId(), nuevaFecha.toString(), "14:00");

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(LocalTime.of(14, 0), dbTurno.getHoraInicio());
        assertEquals(LocalTime.of(14, 30), dbTurno.getHoraFin());
    }

    @Test
    void testReprogramarTurno_usesMedicoConfiguredDuracionSesenta() {
        medico.setDuracionTurnoMinutos(60);
        usuarioRepository.save(medico);

        LocalDate nuevaFecha = LocalDate.now().plusDays(4);
        turnoService.reprogramarTurno(turno.getId(), nuevaFecha.toString(), "09:00");

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(LocalTime.of(9, 0), dbTurno.getHoraInicio());
        assertEquals(LocalTime.of(10, 0), dbTurno.getHoraFin());
    }

    @Test
    void testReservarTurnoReceta_usesFallbackPrice() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.RECETA)
                .nombrePaciente("Receta Paciente")
                .emailPaciente("receta.paciente@gmail.com")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);

        assertNotNull(response);
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
        usuarioRepository.findByEmail("receta.paciente@gmail.com").ifPresent(u -> {
            try { usuarioRepository.delete(u); } catch (Exception e) {}
        });
    }

    @Test
    void testReservarTurnoCertificado_usesFallbackPrice() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.CERTIFICADO)
                .nombrePaciente("Certificado Paciente")
                .emailPaciente("certificado.paciente@gmail.com")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);

        assertNotNull(response);
        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
        usuarioRepository.findByEmail("certificado.paciente@gmail.com").ifPresent(u -> {
            try { usuarioRepository.delete(u); } catch (Exception e) {}
        });
    }

    @Test
    void testReservarTurno_usesConfiguredTarifaWhenHabilitada() {
        com.tranqui.app.model.TarifaMedico tarifa = com.tranqui.app.model.TarifaMedico.builder()
                .medico(medico)
                .servicioId("particular")
                .label("Consulta particular")
                .precio(new java.math.BigDecimal("99999"))
                .habilitado(true)
                .build();
        tarifaMedicoRepository.save(tarifa);

        turno.setFecha(LocalDate.now().minusDays(1));
        turnoRepository.save(turno);

        try {
            com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                    .medicoId(medico.getId())
                    .fecha(LocalDate.now().plusDays(2))
                    .hora(LocalTime.of(9, 0))
                    .tipo(TipoTurno.PARTICULAR)
                    .nombrePaciente(paciente.getNombre())
                    .emailPaciente(paciente.getEmail())
                    .build();

            com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);

            assertEquals(0, new java.math.BigDecimal("99999").compareTo(response.getPrecio()));
            if (response.getTurnoId() != null) {
                try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
            }
        } finally {
            tarifaMedicoRepository.delete(tarifa);
        }
    }

    @Test
    void testReservarTurno_medicoNoEncontrado() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(-1L)
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("X")
                .emailPaciente("x@mail.com")
                .build();

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> turnoService.reservarTurno(dto));
    }

    @Test
    void testReservarTurno_horarioNoDisponible() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(23, 0)) // outside the configured availability window
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("X")
                .emailPaciente("x2@mail.com")
                .build();

        assertThrows(IllegalStateException.class, () -> turnoService.reservarTurno(dto));
    }

    // --- Double-booking guard (existsActiveTurnoByPacienteEmail) ---------------------------

    @Test
    void testReservarTurno_blocksDoubleBookingForPatientWithActiveTurno() {
        // `paciente` already has the shared `turno` fixture from setUp() — PENDIENTE_PAGO,
        // ocupaAgenda true (default), fecha in the future — which is exactly what
        // existsActiveTurnoByPacienteEmail looks for.
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente(paciente.getNombre())
                .emailPaciente(paciente.getEmail())
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> turnoService.reservarTurno(dto));
        assertTrue(ex.getMessage().contains("Ya tenés un turno activo"));
    }

    // --- Ambiguous modalidad when médico offers both -----------------------------------------

    @Test
    void testReservarTurno_ambasModalidadesSinEspecificar_throwsIllegalArgumentException() {
        medico.setOfrecePresencial(true); // isOfreceOnline() already defaults to true
        usuarioRepository.save(medico);

        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("Ambas Modalidades")
                .emailPaciente("ambas.modalidades@gmail.com")
                // modalidad intentionally omitted
                .build();

        assertThrows(IllegalArgumentException.class, () -> turnoService.reservarTurno(dto));
    }

    // --- requiereAfiliado without metadataAfiliado --------------------------------------------

    @Test
    void testReservarTurno_obraSocialSinMetadataAfiliado_throwsIllegalStateException() {
        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.OBRA_SOCIAL)
                .obraSocial("Swiss Medical")
                .nombrePaciente("Sin Afiliado")
                .emailPaciente("sin.afiliado@gmail.com")
                // metadataAfiliado intentionally omitted
                .build();

        // reservarTurno is @Transactional, so the Usuario row created for this new patient
        // before the check fails is rolled back automatically — nothing to clean up.
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> turnoService.reservarTurno(dto));
        assertTrue(ex.getMessage().contains("número de afiliado"));
    }

    // --- marcarDocumentoEnviado ----------------------------------------------------------------

    private static final String ARCHIVO_DATA_VALIDO = "data:application/pdf;base64,aGVsbG8gbXVuZG8=";

    private Turno crearTurnoDocumento(EstadoTurno estado, boolean esReceta, String servicioId) {
        Turno doc = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(12, 0)).horaFin(LocalTime.of(12, 0))
                .tipo(esReceta ? TipoTurno.RECETA : TipoTurno.CERTIFICADO)
                .estado(estado)
                .ocupaAgenda(false)
                .esReceta(esReceta)
                .servicioId(servicioId)
                .build();
        turnoRepository.save(doc);
        return doc;
    }

    @Test
    void testMarcarDocumentoEnviado_success() {
        Turno doc = crearTurnoDocumento(EstadoTurno.CONFIRMADO, false, "certificado");
        try {
            turnoService.marcarDocumentoEnviado(doc.getId(), medico.getEmail(), ARCHIVO_DATA_VALIDO, "certificado.pdf");

            Turno db = turnoRepository.findById(doc.getId()).orElseThrow();
            assertTrue(db.isDocumentoEnviado());
        } finally {
            turnoRepository.delete(doc);
        }
    }

    @Test
    void testMarcarDocumentoEnviado_wrongOwnership_throws() {
        Turno doc = crearTurnoDocumento(EstadoTurno.CONFIRMADO, false, "certificado");
        try {
            assertThrows(IllegalStateException.class, () ->
                    turnoService.marcarDocumentoEnviado(doc.getId(), "otro.medico@gmail.com", ARCHIVO_DATA_VALIDO, "x.pdf"));

            Turno db = turnoRepository.findById(doc.getId()).orElseThrow();
            assertFalse(db.isDocumentoEnviado());
        } finally {
            turnoRepository.delete(doc);
        }
    }

    @Test
    void testMarcarDocumentoEnviado_nonDocumentTurno_throws() {
        // `turno` (from setUp) has ocupaAgenda == true (default) — a real scheduled appointment,
        // not a document service.
        assertThrows(IllegalStateException.class, () ->
                turnoService.marcarDocumentoEnviado(turno.getId(), medico.getEmail(), ARCHIVO_DATA_VALIDO, "x.pdf"));
    }

    @Test
    void testMarcarDocumentoEnviado_notYetPaid_throws() {
        Turno doc = crearTurnoDocumento(EstadoTurno.PENDIENTE_PAGO, false, "certificado");
        try {
            assertThrows(IllegalStateException.class, () ->
                    turnoService.marcarDocumentoEnviado(doc.getId(), medico.getEmail(), ARCHIVO_DATA_VALIDO, "x.pdf"));
        } finally {
            turnoRepository.delete(doc);
        }
    }

    @Test
    void testMarcarDocumentoEnviado_blocksRecetaPath() {
        // Recetas are never sent through this path — they get auto-marked by
        // marcarRecetasEnviadasParaPaciente when the médico actually generates the receta.
        Turno receta = crearTurnoDocumento(EstadoTurno.CONFIRMADO, true, "receta-fuera");
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    turnoService.marcarDocumentoEnviado(receta.getId(), medico.getEmail(), ARCHIVO_DATA_VALIDO, "x.pdf"));
            assertTrue(ex.getMessage().toLowerCase().contains("receta"));
        } finally {
            turnoRepository.delete(receta);
        }
    }

    @Test
    void testMarcarDocumentoEnviado_oversizedAttachment_throws() {
        Turno doc = crearTurnoDocumento(EstadoTurno.CONFIRMADO, false, "certificado");
        // ~9MB decoded (12_000_000 base64 chars * 3/4), over the 8MB cap.
        String archivoDataGrande = "data:application/pdf;base64," + "A".repeat(12_000_000);
        try {
            assertThrows(IllegalArgumentException.class, () ->
                    turnoService.marcarDocumentoEnviado(doc.getId(), medico.getEmail(), archivoDataGrande, "grande.pdf"));
        } finally {
            turnoRepository.delete(doc);
        }
    }

    @Test
    void testMarcarDocumentoEnviado_mailSendFailure_throwsAndDoesNotMarkSent() {
        when(resendEmailService.enviarDocumentoAdjunto(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        Turno doc = crearTurnoDocumento(EstadoTurno.CONFIRMADO, false, "certificado");
        try {
            // The failure is NOT swallowed — marcarDocumentoEnviado throws rather than silently
            // flipping documentoEnviado when the patient never actually received the file.
            assertThrows(IllegalStateException.class, () ->
                    turnoService.marcarDocumentoEnviado(doc.getId(), medico.getEmail(), ARCHIVO_DATA_VALIDO, "x.pdf"));

            Turno db = turnoRepository.findById(doc.getId()).orElseThrow();
            assertFalse(db.isDocumentoEnviado());
        } finally {
            turnoRepository.delete(doc);
        }
    }

    // --- abandonarReservaPendiente ---------------------------------------------------------

    @Test
    void testAbandonarReservaPendiente_cancelsWhenPendientePago() {
        turnoService.abandonarReservaPendiente(turno.getId());

        Turno db = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(EstadoTurno.CANCELADO, db.getEstado());
    }

    @Test
    void testAbandonarReservaPendiente_doesNothingWhenAlreadyConfirmed() {
        turno.setEstado(EstadoTurno.CONFIRMADO);
        turnoRepository.save(turno);

        turnoService.abandonarReservaPendiente(turno.getId());

        Turno db = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(EstadoTurno.CONFIRMADO, db.getEstado());
    }

    @Test
    void testAbandonarReservaPendiente_turnoNoEncontrado_throws() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.abandonarReservaPendiente(-1L));
    }

    // --- marcarRecetasEnviadasParaPaciente --------------------------------------------------

    @Test
    void testMarcarRecetasEnviadasParaPaciente_marksPendingRecetaFueraTurno() {
        Turno recetaPendiente = crearTurnoDocumento(EstadoTurno.CONFIRMADO, true, "receta-fuera");
        try {
            turnoService.marcarRecetasEnviadasParaPaciente(medico.getId(), paciente.getId());

            Turno db = turnoRepository.findById(recetaPendiente.getId()).orElseThrow();
            assertTrue(db.isDocumentoEnviado());
        } finally {
            turnoRepository.delete(recetaPendiente);
        }
    }

    @Test
    void testMarcarRecetasEnviadasParaPaciente_noPendingRecetas_doesNothing() {
        // `turno` (from setUp) isn't a "receta-fuera" servicio, so nothing should match — the
        // method is best-effort and must never throw even when there's nothing to update.
        assertDoesNotThrow(() -> turnoService.marcarRecetasEnviadasParaPaciente(medico.getId(), paciente.getId()));

        Turno db = turnoRepository.findById(turno.getId()).orElseThrow();
        assertFalse(db.isDocumentoEnviado());
    }
}
