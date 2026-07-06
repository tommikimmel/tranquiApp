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
                .fechaCreacion(LocalDateTime.now().minusMinutes(5))
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
}
