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
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class LiberarTurnosSchedulerTest {

    @Autowired
    private LiberarTurnosScheduler scheduler;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario paciente;
    private Usuario medico;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Paciente")
                .email("pac@test.com")
                .rol(Rol.PACIENTE)
                .build();
        medico = Usuario.builder()
                .nombre("Medico")
                .email("med@test.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);
    }



    @Test
    void shouldCancelOnlyExpiredPendingPayments() {
        // 1. Expired (15 mins ago) - should be cancelled
        Turno expirado = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .fechaCreacion(LocalDateTime.now().minusMinutes(15))
                .build();

        // 2. Active (5 mins ago) - should remain PENDIENTE_PAGO
        Turno activo = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(11, 0))
                .horaFin(LocalTime.of(11, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .fechaCreacion(LocalDateTime.now().minusMinutes(3))
                .build();

        // 3. Confirmed (15 mins ago) - should remain CONFIRMADO
        Turno confirmado = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(12, 0))
                .horaFin(LocalTime.of(12, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .fechaCreacion(LocalDateTime.now().minusMinutes(15))
                .build();

        turnoRepository.save(expirado);
        turnoRepository.save(activo);
        turnoRepository.save(confirmado);

        // Run scheduler
        scheduler.liberarTurnosExpirados();

        // Fetch back and assert
        Turno dbExpirado = turnoRepository.findById(expirado.getId()).orElse(null);
        Turno dbActivo = turnoRepository.findById(activo.getId()).orElse(null);
        Turno dbConfirmado = turnoRepository.findById(confirmado.getId()).orElse(null);

        assertNotNull(dbExpirado);
        assertEquals(EstadoTurno.CANCELADO, dbExpirado.getEstado());

        assertNotNull(dbActivo);
        assertEquals(EstadoTurno.PENDIENTE_PAGO, dbActivo.getEstado());

        assertNotNull(dbConfirmado);
        assertEquals(EstadoTurno.CONFIRMADO, dbConfirmado.getEstado());
    }

    // The scheduler's query is scoped to EstadoTurno.PENDIENTE_PAGO only (see
    // findByEstadoAndFechaCreacionBefore) — turnos in any other estado must never be touched,
    // no matter how old fechaCreacion is.
    @Test
    void shouldNeverTouchTurnosThatAreNotPendienteDePago() {
        Turno pendienteValidacionAntiguo = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(15, 0)).horaFin(LocalTime.of(15, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_VALIDACION)
                .fechaCreacion(LocalDateTime.now().minusHours(2))
                .build();

        Turno expiradoAntiguo = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(16, 0)).horaFin(LocalTime.of(16, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.EXPIRADO)
                .fechaCreacion(LocalDateTime.now().minusHours(2))
                .build();

        Turno canceladoAntiguo = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(17, 0)).horaFin(LocalTime.of(17, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CANCELADO)
                .fechaCreacion(LocalDateTime.now().minusHours(2))
                .build();

        turnoRepository.save(pendienteValidacionAntiguo);
        turnoRepository.save(expiradoAntiguo);
        turnoRepository.save(canceladoAntiguo);

        scheduler.liberarTurnosExpirados();

        assertEquals(EstadoTurno.PENDIENTE_VALIDACION,
                turnoRepository.findById(pendienteValidacionAntiguo.getId()).orElseThrow().getEstado());
        assertEquals(EstadoTurno.EXPIRADO,
                turnoRepository.findById(expiradoAntiguo.getId()).orElseThrow().getEstado());
        assertEquals(EstadoTurno.CANCELADO,
                turnoRepository.findById(canceladoAntiguo.getId()).orElseThrow().getEstado());
    }

    // Boundary exactly around the 5-minute grace window (findByEstadoAndFechaCreacionBefore uses
    // a strict "before", i.e. "<").
    @Test
    void shouldNotCancelTurnoStillJustInsideTheFiveMinuteGraceWindow() {
        Turno casiExpirado = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(13, 0)).horaFin(LocalTime.of(13, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                // Created just under 5 minutes ago — still inside the grace window.
                .fechaCreacion(LocalDateTime.now().minusMinutes(4).minusSeconds(50))
                .build();
        turnoRepository.save(casiExpirado);

        scheduler.liberarTurnosExpirados();

        assertEquals(EstadoTurno.PENDIENTE_PAGO,
                turnoRepository.findById(casiExpirado.getId()).orElseThrow().getEstado());
    }

    @Test
    void shouldCancelTurnoJustPastTheFiveMinuteGraceWindow() {
        Turno recienExpirado = Turno.builder()
                .paciente(paciente).medico(medico)
                .fecha(LocalDate.now())
                .horaInicio(LocalTime.of(14, 0)).horaFin(LocalTime.of(14, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                // Created just over 5 minutes ago — past the grace window.
                .fechaCreacion(LocalDateTime.now().minusMinutes(5).minusSeconds(10))
                .build();
        turnoRepository.save(recienExpirado);

        scheduler.liberarTurnosExpirados();

        assertEquals(EstadoTurno.CANCELADO,
                turnoRepository.findById(recienExpirado.getId()).orElseThrow().getEstado());
    }
}
