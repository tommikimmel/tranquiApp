package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.data.TurnoActualizadoData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6: la copia del monolito de los turnos del servicio de turnos (lo que aplican turno.actualizado y la
 * resincronización): conserva el id del servicio, copia el pago y deja la versión por encima.
 */
@SpringBootTest
class CopiaTurnosTest {

    @Autowired private CopiaTurnos copia;
    @Autowired private ResincronizacionTurnos resincronizacion;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TransactionTemplate tx;

    private Usuario usuario(Rol rol) {
        return usuarioRepository.save(Usuario.builder().nombre("U").email("copia-turno." + System.nanoTime() + "@mail.com")
                .rol(rol).build());
    }

    private static TurnoActualizadoData dato(long id, Usuario paciente, Usuario medico, String estado, String pagoEstado) {
        return new TurnoActualizadoData(id, paciente.getId(), medico.getId(), LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                LocalTime.of(10, 45), "PARTICULAR", "ONLINE", estado, "ESPERANDO", new BigDecimal("60000"), null, "particular",
                true, null, "https://meet.test/abc", null, null, false, false, LocalDateTime.now().minusMinutes(2), false, false,
                pagoEstado != null ? "mp-" + id : null, pagoEstado, pagoEstado != null ? new BigDecimal("60000") : null,
                pagoEstado != null ? LocalDateTime.now() : null);
    }

    @Test
    void unTurnoNuevo_seCopiaConElMismoIdYSuPago() {
        Usuario paciente = usuario(Rol.PACIENTE);
        Usuario medico = usuario(Rol.PSIQUIATRA);
        long id = 900_000L + System.nanoTime() % 100_000;

        copia.aplicar(dato(id, paciente, medico, "CONFIRMADO", "APROBADO"), 7);

        tx.executeWithoutResult(e -> {
            Turno t = turnoRepository.findById(id).orElseThrow();
            assertThat(t.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
            assertThat(t.getPaciente().getId()).isEqualTo(paciente.getId());
            assertThat(t.getTelemedicinaUrl()).isEqualTo("https://meet.test/abc");
            assertThat(t.getAggregateVersion()).isGreaterThan(7);
            assertThat(t.getPago()).isNotNull();
            assertThat(t.getPago().getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(t.getPago().getTransactionId()).isEqualTo("mp-" + id);
        });
    }

    @Test
    void unTurnoQueYaEstaba_seActualizaYElPagoPasaAReembolsado() {
        Usuario paciente = usuario(Rol.PACIENTE);
        Usuario medico = usuario(Rol.PSIQUIATRA);
        long id = 910_000L + System.nanoTime() % 100_000;
        copia.aplicar(dato(id, paciente, medico, "CONFIRMADO", "APROBADO"), 3);

        copia.aplicar(dato(id, paciente, medico, "CANCELADO", "REEMBOLSADO"), 5);

        tx.executeWithoutResult(e -> {
            Turno t = turnoRepository.findById(id).orElseThrow();
            assertThat(t.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(t.getPago().getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
        });
        assertThat(pagoRepository.findByTransactionId("mp-" + id)).isPresent();
    }

    @Test
    void resincronizacion_aplicaCadaTurnoYSaltaLosQueNoSePueden() {
        Usuario paciente = usuario(Rol.PACIENTE);
        Usuario medico = usuario(Rol.PSIQUIATRA);
        long id = 920_000L + System.nanoTime() % 100_000;
        Usuario fantasma = Usuario.builder().id(-5L).build();

        int aplicados = resincronizacion.aplicar(List.of(
                new ResincronizacionTurnos.TurnoServicio(4, dato(id, paciente, medico, "PENDIENTE_PAGO", null)),
                new ResincronizacionTurnos.TurnoServicio(1, dato(id + 1, fantasma, medico, "CONFIRMADO", null))));

        assertThat(aplicados).isEqualTo(2);
        assertThat(turnoRepository.findById(id)).isPresent();
        assertThat(turnoRepository.findById(id + 1)).isEmpty();
    }
}
