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
        if (paciente != null && paciente.getId() != null) {
            try { usuarioRepository.delete(paciente); } catch (Exception e) {}
        }
        if (medico != null && medico.getId() != null) {
            try { usuarioRepository.delete(medico); } catch (Exception e) {}
        }
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
        assertEquals("CONFIRMADO", response.getEstado());
        
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
}
