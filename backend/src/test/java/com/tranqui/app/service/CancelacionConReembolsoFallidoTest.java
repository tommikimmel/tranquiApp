package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Pago;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Encontrado en la verificación de la Fase 4: si el reembolso tira una excepción (acá, un token de Mercado
 * Pago que no se puede descifrar), la cancelación tiene que confirmarse igual con el reembolso PENDIENTE.
 * Antes la transacción quedaba marcada para deshacerse y la cancelación entera fallaba con 500.
 */
@SpringBootTest
class CancelacionConReembolsoFallidoTest {

    @Autowired private TurnoService turnoService;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @AfterEach
    void limpiar() {
        pagoRepository.deleteAll();
        turnoRepository.deleteAll();
        usuarioRepository.findByEmail("medica.reembolso@gmail.com").ifPresent(usuarioRepository::delete);
        usuarioRepository.findByEmail("paciente.reembolso@gmail.com").ifPresent(usuarioRepository::delete);
    }

    @Test
    void unReembolsoQueFalla_noImpideCancelar() {
        Usuario medica = usuarioRepository.save(Usuario.builder().nombre("Medica").email("medica.reembolso@gmail.com")
                .rol(Rol.PSIQUIATRA).mpAccessTokenEncrypted("no-es:un-token-cifrado").build());
        Usuario paciente = usuarioRepository.save(Usuario.builder().nombre("Paciente").email("paciente.reembolso@gmail.com")
                .rol(Rol.PACIENTE).build());
        Turno turno = turnoRepository.save(Turno.builder().medico(medica).paciente(paciente)
                .fecha(LocalDate.now().plusDays(5)).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).modalidad(Modalidad.ONLINE).estado(EstadoTurno.CONFIRMADO)
                .precio(new BigDecimal("60000")).ocupaAgenda(true).build());
        pagoRepository.save(Pago.builder().turno(turno).transactionId("mp-falla-1").estado(EstadoPago.APROBADO)
                .monto(new BigDecimal("60000")).fechaPago(LocalDateTime.now()).build());

        assertDoesNotThrow(() -> turnoService.cancelarTurno(turno.getId(), paciente.getEmail()));

        assertEquals(EstadoTurno.CANCELADO, turnoRepository.findById(turno.getId()).orElseThrow().getEstado());
        assertEquals(EstadoPago.APROBADO, pagoRepository.findByTransactionId("mp-falla-1").orElseThrow().getEstado());
    }
}
