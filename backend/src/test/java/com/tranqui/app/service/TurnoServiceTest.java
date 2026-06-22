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

    private Usuario paciente;
    private Usuario medico;
    private Turno turno;

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

        turno = Turno.builder()
                .paciente(paciente)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .build();

        turnoRepository.save(turno);
    }

    @AfterEach
    void tearDown() {
        turnoRepository.deleteAll();
        usuarioRepository.deleteAll();
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
}
