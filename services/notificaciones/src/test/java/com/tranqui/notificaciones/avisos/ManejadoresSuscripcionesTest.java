package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Cada tipo de aviso de suscripción manda su mail; un tipo desconocido es un evento inválido. */
class ManejadoresSuscripcionesTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 5, 9, 0);

    private final Avisos avisos = mock(Avisos.class);
    private final ResendEmailService mails = mock(ResendEmailService.class);
    private final Contacto prof = new Contacto(2L, "lucia@mail.com");

    @BeforeEach
    void setUp() {
        prof.setNombre("Lucía");
        when(avisos.contacto(2L)).thenReturn(prof);
        doAnswer(i -> { ((Runnable) i.getArgument(2)).run(); return null; }).when(avisos).mail(any(), anyString(), any());
    }

    private void aviso(String tipo, Integer dias) {
        new ManejadoresSuscripciones().mailAvisoSuscripcion(avisos, mails).manejar(EventEnvelope.<SuscripcionAvisoData>builder()
                .eventId(UUID.randomUUID()).eventType("x")
                .data(new SuscripcionAvisoData(1L, 2L, tipo, "Pro", BigDecimal.TEN, FECHA, FECHA.plusMonths(1), "monthly", "MP-1", dias))
                .build());
    }

    @Test
    void cadaTipoDeAviso() {
        aviso(SuscripcionAvisoData.PREAVISO_RENOVACION, null);
        aviso(SuscripcionAvisoData.PAGO_CONFIRMADO, null);
        aviso(SuscripcionAvisoData.PAGO_FALLIDO, null);
        aviso(SuscripcionAvisoData.CANCELACION_CONFIRMADA, null);
        aviso(SuscripcionAvisoData.SUSPENSION, null);
        aviso(SuscripcionAvisoData.VENCIMIENTO_MANUAL, 3);
        aviso(SuscripcionAvisoData.VENCIMIENTO_MANUAL, null);
        verify(mails).enviarPreavisoRenovacionSuscripcion("lucia@mail.com", "Lucía", "Pro", BigDecimal.TEN, FECHA, "monthly");
        verify(mails).enviarConfirmacionPagoSuscripcion("lucia@mail.com", "Lucía", "Pro", BigDecimal.TEN, FECHA, FECHA.plusMonths(1), "MP-1");
        verify(mails).enviarAvisoPagoFallidoSuscripcion("lucia@mail.com", "Lucía", "Pro", BigDecimal.TEN, FECHA);
        verify(mails).enviarConfirmacionCancelacionSuscripcion("lucia@mail.com", "Lucía", "Pro", FECHA);
        verify(mails).enviarAvisoSuspensionSuscripcion("lucia@mail.com", "Lucía", "Pro");
        verify(mails).enviarAvisoVencimientoManualSuscripcion("lucia@mail.com", "Lucía", "Pro", FECHA, 3);
        verify(mails).enviarAvisoVencimientoManualSuscripcion("lucia@mail.com", "Lucía", "Pro", FECHA, 0);
    }

    @Test
    void tipoDesconocido_esInvalido() {
        assertThatThrownBy(() -> aviso("OTRO", null)).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void sinEmail_noManda() {
        prof.setEmail(null);
        aviso(SuscripcionAvisoData.SUSPENSION, null);
        verifyNoInteractions(mails);
    }
}
