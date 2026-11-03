package com.tranqui.app.eventos;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.PagoWebhookHandler;
import com.tranqui.app.service.ReembolsoService;
import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fase 4: lo que el monolito hace con los eventos del servicio de pagos. */
class ManejadoresPagosTest {

    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("pagos.x").data(data).build();
    }

    @Test
    void checkout_seGuardaEnElTurnoPendiente() {
        Turno turno = Turno.builder().id(7L).estado(EstadoTurno.PENDIENTE_PAGO).build();
        when(turnos.findById(7L)).thenReturn(Optional.of(turno));

        ManejadoresPagos.guardarCheckout(turnos, new CheckoutCreadoData("7", 7L, null, "https://mp/checkout?pref_id=1"));

        assertThat(turno.getCheckoutUrl()).isEqualTo("https://mp/checkout?pref_id=1");
        verify(turnos).save(turno);
    }

    @Test
    void checkout_noTocaUnTurnoYaConfirmadoNiLosDocumentos() {
        Turno confirmado = Turno.builder().id(8L).estado(EstadoTurno.CONFIRMADO).build();
        when(turnos.findById(8L)).thenReturn(Optional.of(confirmado));

        ManejadoresPagos.guardarCheckout(turnos, new CheckoutCreadoData("8", 8L, null, "https://mp/1"));
        ManejadoresPagos.guardarCheckout(turnos, new CheckoutCreadoData("doc-3", null, 3L, "https://mp/2"));

        assertThat(confirmado.getCheckoutUrl()).isNull();
        verify(turnos, never()).save(any());
    }

    @Test
    void cuentaVinculada_yDesvinculada() {
        Usuario lucia = Usuario.builder().id(16L).mpAccessTokenEncrypted("v2:viejo").build();
        when(usuarios.findById(16L)).thenReturn(Optional.of(lucia));
        ManejadoresPagos manejadores = new ManejadoresPagos();

        manejadores.cuentaMpVinculada(usuarios).manejar(evento(new CuentaMpVinculadaData(16L, "123")));
        assertThat(lucia.tieneMercadoPagoVinculado()).isTrue();
        assertThat(lucia.getMpUserId()).isEqualTo("123");

        manejadores.cuentaMpDesvinculada(usuarios).manejar(evento(new CuentaMpDesvinculadaData(16L)));
        assertThat(lucia.tieneMercadoPagoVinculado()).isFalse();
        assertThat(lucia.getMpAccessTokenEncrypted()).isNull();
        assertThat(lucia.getMpUserId()).isNull();
    }

    @Test
    void politicaDeReembolso() {
        Turno enUnaSemana = Turno.builder().fecha(LocalDate.now().plusDays(7)).horaInicio(LocalTime.of(10, 0)).build();
        Turno manana = Turno.builder().fecha(LocalDate.now().plusDays(1)).horaInicio(LocalTime.of(10, 0)).build();

        assertThat(ReembolsoService.correspondeReembolso(enUnaSemana, false)).isTrue();
        assertThat(ReembolsoService.correspondeReembolso(manana, false)).isFalse();
        assertThat(ReembolsoService.correspondeReembolso(manana, true)).isTrue();
    }

    @Test
    void conceptoPagado_conClinicaExternoTrue_noAplicaPago() {
        PagoWebhookHandler pagos = mock(PagoWebhookHandler.class);
        ManejadoresPagos manejadores = new ManejadoresPagos();
        ConceptoAprobadoData data = new ConceptoAprobadoData(1L, 2L, 3L, "RECETA", "MP-1");

        manejadores.conceptoPagado(pagos, true).manejar(evento(data));
        verify(pagos, never()).aplicarConceptoAprobado(any());

        manejadores.conceptoPagado(pagos, false).manejar(evento(data));
        verify(pagos).aplicarConceptoAprobado(data);
    }
}
