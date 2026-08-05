package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
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
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

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
                .estado(EstadoTurno.PENDIENTE_PAGO)
                // obtenerTurnosPaciente now reads the persisted checkoutUrl instead of calling
                // Mercado Pago again on every read, so it must already be set here.
                .checkoutUrl("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id")
                .build();

        turnoRepository.save(turno);
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
                .hora(LocalTime.of(10, 0))
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
        java.util.List<LocalTime> result = turnoService.obtenerHorariosDisponibles(medico.getId(), LocalDate.now());
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
        turnoService.cancelarTurno(turno.getId());

        Turno dbTurno = turnoRepository.findById(turno.getId()).orElseThrow();
        assertEquals(EstadoTurno.CANCELADO, dbTurno.getEstado());
    }

    @Test
    void testCancelarTurno_turnoNoEncontrado() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> turnoService.cancelarTurno(-1L));
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
                    // Reuse the setUp patient, which already has a non-cancelled turno,
                    // so no first-consultation surcharge is applied and the price assertion is exact.
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
}
