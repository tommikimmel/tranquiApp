package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Pago;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReembolsoServiceTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private ReembolsoService reembolsoService;

    private Usuario medico;
    private Turno turnoConTiempo;
    private Turno turnoTardio;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reembolsoService, "isEnabled", false);

        medico = Usuario.builder()
                .nombre("Dr. Carlos")
                .email("carlos@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();

        // 1. Booking > 48h in advance (3 days from now)
        LocalDate fechaLejana = LocalDate.now().plusDays(3);
        turnoConTiempo = Turno.builder()
                .id(1L)
                .fecha(fechaLejana)
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .build();

        Pago pagoLejano = Pago.builder()
                .id(1L)
                .turno(turnoConTiempo)
                .transactionId("123456789")
                .estado(EstadoPago.APROBADO)
                .monto(BigDecimal.valueOf(15000.00))
                .fechaPago(LocalDateTime.now())
                .build();
        turnoConTiempo.setPago(pagoLejano);

        // 2. Booking < 48h in advance (1 day from now)
        LocalDate fechaCercana = LocalDate.now().plusDays(1);
        turnoTardio = Turno.builder()
                .id(2L)
                .fecha(fechaCercana)
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR)
                .estado(EstadoTurno.CONFIRMADO)
                .build();

        Pago pagoTardio = Pago.builder()
                .id(2L)
                .turno(turnoTardio)
                .transactionId("987654321")
                .estado(EstadoPago.APROBADO)
                .monto(BigDecimal.valueOf(15000.00))
                .fechaPago(LocalDateTime.now())
                .build();
        turnoTardio.setPago(pagoTardio);
    }

    @Test
    void whenCancelationIsMoreThan48Hours_thenRefundApproved() throws Exception {
        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("dummy-token");

        boolean resultado = reembolsoService.procesarReembolso(turnoConTiempo, medico);

        assertTrue(resultado);
        assertEquals(EstadoTurno.CANCELADO, turnoConTiempo.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, turnoConTiempo.getPago().getEstado());
    }

    @Test
    void whenCancelationIsLessThan48Hours_thenRefundBlocked() throws Exception {
        boolean resultado = reembolsoService.procesarReembolso(turnoTardio, medico);

        assertFalse(resultado);
        assertEquals(EstadoTurno.CONFIRMADO, turnoTardio.getEstado());
        assertEquals(EstadoPago.APROBADO, turnoTardio.getPago().getEstado());
    }
}
