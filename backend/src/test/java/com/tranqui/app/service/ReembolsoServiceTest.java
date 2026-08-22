package com.tranqui.app.service;

import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void whenMedicoCancelsLessThan48Hours_thenRefundStillApproved() throws Exception {
        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("dummy-token");

        boolean resultado = reembolsoService.procesarReembolso(turnoTardio, medico, true);

        assertTrue(resultado);
        assertEquals(EstadoTurno.CANCELADO, turnoTardio.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, turnoTardio.getPago().getEstado());
    }

    // ── invocación real al cliente de refund de Mercado Pago ─────────────────────────

    @Test
    void whenEnabledAndRealToken_shouldCallPaymentRefundClientWithTransactionIdAndAccessToken() throws Exception {
        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");

        PaymentRefundClient fakeRefundClient = mock(PaymentRefundClient.class);
        ReembolsoService spyService = new ReembolsoService() {
            @Override
            protected PaymentRefundClient buildPaymentRefundClient() {
                return fakeRefundClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(spyService, "isEnabled", true);

        boolean resultado = spyService.procesarReembolso(turnoConTiempo, medico);

        assertTrue(resultado);
        assertEquals(EstadoTurno.CANCELADO, turnoConTiempo.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, turnoConTiempo.getPago().getEstado());

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<MPRequestOptions> optionsCaptor = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(fakeRefundClient).refund(idCaptor.capture(), optionsCaptor.capture());
        assertEquals(123456789L, idCaptor.getValue());
        assertEquals("real-looking-token", optionsCaptor.getValue().getAccessToken());
    }

    @Test
    void whenDisabled_shouldSkipRealRefundCallEntirely() throws Exception {
        ReflectionTestUtils.setField(reembolsoService, "isEnabled", false);
        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("dummy-token");

        PaymentRefundClient fakeRefundClient = mock(PaymentRefundClient.class);
        ReembolsoService spyService = new ReembolsoService() {
            @Override
            protected PaymentRefundClient buildPaymentRefundClient() {
                return fakeRefundClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(spyService, "isEnabled", false);

        boolean resultado = spyService.procesarReembolso(turnoConTiempo, medico);

        assertTrue(resultado);
        assertEquals(EstadoTurno.CANCELADO, turnoConTiempo.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, turnoConTiempo.getPago().getEstado());
        verifyNoInteractions(fakeRefundClient);
    }

    // ── guard: turno.getPago() == null no debe tirar NPE ──────────────────────────────

    @Test
    void whenEnabledAndTurnoSinPagoAsociado_shouldNotThrowAndStillCancelaElTurno() throws Exception {
        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");

        Turno turnoSinPago = Turno.builder()
                .id(3L)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.CONFIRMADO)
                .build();
        // turnoSinPago.getPago() queda null a propósito: simula la inconsistencia de datos que
        // este guard cubre (turno aprobado/confirmado sin una fila de Pago asociada).

        PaymentRefundClient fakeRefundClient = mock(PaymentRefundClient.class);
        ReembolsoService spyService = new ReembolsoService() {
            @Override
            protected PaymentRefundClient buildPaymentRefundClient() {
                return fakeRefundClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(spyService, "isEnabled", true);

        boolean resultado = assertDoesNotThrow(() -> spyService.procesarReembolso(turnoSinPago, medico));

        assertTrue(resultado);
        assertEquals(EstadoTurno.CANCELADO, turnoSinPago.getEstado());
        verifyNoInteractions(fakeRefundClient);
    }
}
