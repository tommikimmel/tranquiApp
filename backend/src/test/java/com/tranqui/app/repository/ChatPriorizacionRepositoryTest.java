package com.tranqui.app.repository;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatPriorizacionRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    private Usuario medico;
    private Usuario pacienteA;
    private Usuario pacienteB;
    private Usuario pacienteC;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Dr. House")
                .email("house@test.com")
                .rol(Rol.PSIQUIATRA)
                .build();
        pacienteA = Usuario.builder()
                .nombre("Paciente A (Urgente)")
                .email("pacientea@test.com")
                .rol(Rol.PACIENTE)
                .build();
        pacienteB = Usuario.builder()
                .nombre("Paciente B (Planificado)")
                .email("pacienteb@test.com")
                .rol(Rol.PACIENTE)
                .build();
        pacienteC = Usuario.builder()
                .nombre("Paciente C (Sin Turno)")
                .email("pacientec@test.com")
                .rol(Rol.PACIENTE)
                .build();

        usuarioRepository.save(medico);
        usuarioRepository.save(pacienteA);
        usuarioRepository.save(pacienteB);
        usuarioRepository.save(pacienteC);

        // Turno A: CONFIRMADO, para mañana (dentro de las 72 hs)
        Turno turnoA = Turno.builder()
                .paciente(pacienteA)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .build();

        // Turno B: CONFIRMADO, en 5 días (fuera de las 72 hs)
        Turno turnoB = Turno.builder()
                .paciente(pacienteB)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(5))
                .horaInicio(LocalTime.of(11, 0))
                .horaFin(LocalTime.of(11, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .build();

        // Turno C: PENDIENTE_PAGO, para mañana (no cuenta como prioridad porque no está CONFIRMADO)
        Turno turnoC = Turno.builder()
                .paciente(pacienteC)
                .medico(medico)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(12, 0))
                .horaFin(LocalTime.of(12, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .build();

        turnoRepository.save(turnoA);
        turnoRepository.save(turnoB);
        turnoRepository.save(turnoC);

        // Crear al menos un mensaje con cada paciente para que aparezcan en los canales activos del doctor
        Mensaje msgA = Mensaje.builder().remitente(pacienteA).destinatario(medico).contenido("Hola Doc").build();
        Mensaje msgB = Mensaje.builder().remitente(pacienteB).destinatario(medico).contenido("Buen día").build();
        Mensaje msgC = Mensaje.builder().remitente(pacienteC).destinatario(medico).contenido("Consulta").build();

        mensajeRepository.save(msgA);
        mensajeRepository.save(msgB);
        mensajeRepository.save(msgC);
    }

    @AfterEach
    void tearDown() {
        mensajeRepository.deleteAll();
        turnoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void shouldPrioritizeChannelsCorrectly() {
        LocalDate fechaActual = LocalDate.now();
        LocalTime horaActual = LocalTime.now();
        LocalDate fechaLimite = fechaActual.plusDays(3);
        LocalTime horaLimite = horaActual;

        List<CanalPrioritarioDto> result = mensajeRepository.findPrioritizedChannels(
                medico.getId(),
                fechaActual,
                horaActual,
                fechaLimite,
                horaLimite
        );

        assertNotNull(result);
        assertEquals(3, result.size());

        // El primer elemento debe ser el Paciente A (Alta prioridad)
        CanalPrioritarioDto primerCanal = result.get(0);
        assertEquals(pacienteA.getId(), primerCanal.getId());
        assertEquals("PRIORIDAD_ALTA", primerCanal.getPrioridadClinica());

        // Los otros dos deben ser prioridad baja
        assertEquals("PRIORIDAD_BAJA", result.get(1).getPrioridadClinica());
        assertEquals("PRIORIDAD_BAJA", result.get(2).getPrioridadClinica());
    }
}
