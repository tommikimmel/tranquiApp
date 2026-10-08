package com.tranqui.app.config;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.MedicoService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDemoSeederTest {

    // Producción: sin el perfil "local" el seeder ni siquiera existe como bean.
    @Nested
    @SpringBootTest
    class SinPerfilLocal {
        @Autowired
        ApplicationContext context;

        @Test
        void noExisteElSeeder() {
            assertTrue(context.getBeansOfType(LocalDemoSeeder.class).isEmpty());
        }
    }

    @Nested
    @SpringBootTest(properties = "app.demo-data=false")
    @ActiveProfiles("local")
    class PerfilLocalSinFlag {
        @Autowired
        ApplicationContext context;

        @Test
        void noExisteElSeeder() {
            assertTrue(context.getBeansOfType(LocalDemoSeeder.class).isEmpty());
        }
    }

    @Nested
    @SpringBootTest(properties = "app.demo-data=true")
    @ActiveProfiles("local")
    @Transactional
    class PerfilLocalConFlag {
        @Autowired
        LocalDemoSeeder seeder;
        @Autowired
        UsuarioRepository usuarioRepository;
        @Autowired
        TurnoRepository turnoRepository;
        @Autowired
        MensajeRepository mensajeRepository;
        @Autowired
        SubscriptionRepository subscriptionRepository;
        @Autowired
        MedicoService medicoService;

        private List<Usuario> demo() {
            return usuarioRepository.findAll().stream().filter(u -> u.getEmail().endsWith("@demo.tranqui")).toList();
        }

        @Test
        void siembraProfesionalesPacientesTurnosYChats() {
            List<Usuario> demo = demo();
            assertEquals(5, demo.stream().filter(u -> u.getRol() == Rol.PSIQUIATRA).count());
            assertEquals(8, demo.stream().filter(u -> u.getRol() == Rol.PACIENTE).count());
            assertTrue(turnoRepository.count() >= 30);
            assertTrue(mensajeRepository.count() >= 8);
            assertEquals(4, subscriptionRepository.findAll().stream()
                    .filter(s -> s.getProfessional().getEmail().endsWith("@demo.tranqui")).count());

            // Variedad de estados y turnos de hoy.
            assertTrue(turnoRepository.findAll().stream().anyMatch(t -> t.getEstado() == EstadoTurno.CANCELADO));
            assertTrue(turnoRepository.findAll().stream().anyMatch(t -> t.getEstado() == EstadoTurno.PENDIENTE_PAGO));
            assertTrue(turnoRepository.findAll().stream().anyMatch(t -> t.getFecha().equals(LocalDate.now())));
            assertTrue(turnoRepository.findAll().stream().anyMatch(t -> !t.isOcupaAgenda()));
        }

        @Test
        void martinaQuedaSinTurnosParaProbarUnaReserva() {
            Usuario martina = usuarioRepository.findByEmail("martina.lopez@demo.tranqui").orElseThrow();
            assertTrue(turnoRepository.findAll().stream().noneMatch(t -> t.getPaciente().getId().equals(martina.getId())));
        }

        @Test
        void losProfesionalesActivosQuedanPublicadosYElNuevoNo() {
            Usuario lucia = usuarioRepository.findByEmail(LocalDemoSeeder.MARCADOR).orElseThrow();
            Usuario nuevo = usuarioRepository.findByEmail("tomas.ledesma@demo.tranqui").orElseThrow();
            assertTrue(medicoService.isMedicoVerificado(lucia));
            assertFalse(medicoService.isMedicoVerificado(nuevo));
        }

        @Test
        void esIdempotente() throws Exception {
            long usuarios = usuarioRepository.count();
            long turnos = turnoRepository.count();
            seeder.run();
            assertEquals(usuarios, usuarioRepository.count());
            assertEquals(turnos, turnoRepository.count());
        }
    }
}
