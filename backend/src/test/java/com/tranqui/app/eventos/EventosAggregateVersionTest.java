package com.tranqui.app.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.controller.AdminController;
import com.tranqui.app.model.*;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.TurnoService;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@WithMockUser(roles = "ADMIN")
class EventosAggregateVersionTest {

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private AdminController adminController;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper mapper = EventObjectMapperFactory.create();
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("Dos actualizaciones seguidas del mismo Turno publican aggregateVersion n y n+1 estrictamente crecientes")
    void dosActualizacionesSeguidasDeTurno_publicanVersionesEstrictamenteCrecientes() throws Exception {
        // 1. Crear médico, paciente y turno inicial persistido
        Long turnoId = tx.execute(status -> {
            Usuario medico = usuarioRepository.save(Usuario.builder()
                    .nombre("Dr. Turno")
                    .apellido("Test")
                    .email("dr.turno." + System.nanoTime() + "@tranqui.com")
                    .rol(Rol.PSIQUIATRA)
                    .build());

            Usuario paciente = usuarioRepository.save(Usuario.builder()
                    .nombre("Paciente Turno")
                    .apellido("Test")
                    .email("pac.turno." + System.nanoTime() + "@tranqui.com")
                    .rol(Rol.PACIENTE)
                    .build());

            Turno t = Turno.builder()
                    .medico(medico)
                    .paciente(paciente)
                    .fecha(LocalDate.now().plusDays(5))
                    .horaInicio(LocalTime.of(10, 0))
                    .horaFin(LocalTime.of(10, 45))
                    .tipo(TipoTurno.PARTICULAR)
                    .estado(EstadoTurno.PENDIENTE_PAGO)
                    .modalidad(Modalidad.ONLINE)
                    .precio(new BigDecimal("25000.00"))
                    .ocupaAgenda(true)
                    .build();
            return turnoRepository.saveAndFlush(t).getId();
        });

        outboxRepository.deleteAll();

        // 2. Primera actualización: confirmar turno (publica TURNOS_TURNO_CONFIRMADO_V1)
        tx.execute(status -> {
            turnoService.confirmarTurnoOsde(turnoId, "OSDE-TEST-99");
            return null;
        });

        // 3. Segunda actualización: reprogramar turno (publica TURNOS_TURNO_REPROGRAMADO_V1)
        tx.execute(status -> {
            turnoService.reprogramarTurno(turnoId, LocalDate.now().plusDays(6).toString(), "11:00");
            return null;
        });

        // 4. Verificar que se publicaron eventos con aggregateVersion n y n+1 estrictamente crecientes
        List<OutboxRecord> records = outboxRepository.findByAggregateIdOrderByIdAsc(String.valueOf(turnoId))
                .stream()
                .filter(r -> r.getTopic().startsWith("turnos."))
                .toList();
        assertThat(records).hasSizeGreaterThanOrEqualTo(2);

        List<Integer> versiones = new ArrayList<>();
        for (OutboxRecord record : records) {
            JsonNode root = mapper.readTree(record.getPayload());
            versiones.add(root.get("aggregateVersion").asInt());
        }

        // Cada versión sucesiva debe ser estrictamente mayor que la anterior (+1)
        for (int i = 0; i < versiones.size() - 1; i++) {
            int vActual = versiones.get(i);
            int vSiguiente = versiones.get(i + 1);
            assertThat(vSiguiente)
                    .as("Versión del evento siguiente debe ser estrictamente creciente (+1)")
                    .isGreaterThan(vActual);
        }

        // Verificar específicamente las dos actualizaciones consecutivas
        int vConfirmado = versiones.get(0);
        int vReprogramado = versiones.get(1);
        assertThat(vReprogramado).isEqualTo(vConfirmado + 1);
    }

    @Test
    @DisplayName("Dos actualizaciones seguidas del mismo Usuario publican aggregateVersion n y n+1 estrictamente crecientes")
    void dosActualizacionesSeguidasDeUsuario_publicanVersionesEstrictamenteCrecientes() throws Exception {
        // 1. Crear profesional persistido
        Long usuarioId = tx.execute(status -> {
            Usuario u = Usuario.builder()
                    .nombre("Dr. Concurrente")
                    .apellido("Test")
                    .email("dr.conc." + System.nanoTime() + "@tranqui.com")
                    .rol(Rol.PSIQUIATRA)
                    .build();
            return usuarioRepository.saveAndFlush(u).getId();
        });

        outboxRepository.deleteAll();

        // 2. Primera actualización: verificar profesional (publica PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1)
        tx.execute(status -> {
            adminController.verifyProfessional(usuarioId);
            return null;
        });

        // 3. Segunda actualización: rechazar profesional (publica PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1)
        tx.execute(status -> {
            adminController.rejectProfessional(usuarioId);
            return null;
        });

        // 4. Verificar aggregateVersions de los eventos de Usuario
        // Cada actualización publica también identidad.usuario.actualizado.v1 con la misma versión
        // (es el mismo cambio): se comparan los eventos de visibilidad, uno por actualización.
        List<OutboxRecord> records = outboxRepository.findByAggregateIdOrderByIdAsc(String.valueOf(usuarioId)).stream()
                .filter(r -> Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1.equals(r.getTopic()))
                .toList();
        assertThat(records).hasSizeGreaterThanOrEqualTo(2);

        List<Integer> versiones = new ArrayList<>();
        for (OutboxRecord record : records) {
            JsonNode root = mapper.readTree(record.getPayload());
            versiones.add(root.get("aggregateVersion").asInt());
        }

        int vPrimeraActualizacion = versiones.get(0);
        int vSegundaActualizacion = versiones.get(1);

        assertThat(vSegundaActualizacion)
                .as("La segunda actualización debe tener aggregateVersion n + 1")
                .isEqualTo(vPrimeraActualizacion + 1);
        assertThat(vSegundaActualizacion).isGreaterThan(vPrimeraActualizacion);
    }
}
