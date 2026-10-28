package com.tranqui.pagos.interno;

import com.tranqui.pagos.datos.Cobros;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.Pagos;
import com.tranqui.pagos.datos.Participantes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La carga inicial copia lo del monolito tal cual y el consumo la espera. */
class CargaInicialTest {

    private final Participantes participantes = mock(Participantes.class);
    private final CuentasMp cuentas = mock(CuentasMp.class);
    private final Pagos pagos = mock(Pagos.class);
    private final Cobros cobros = mock(Cobros.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    @SuppressWarnings("unchecked")
    private CargaInicial carga(boolean habilitada) {
        doAnswer(i -> {
            ((Consumer<Object>) i.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        return new CargaInicial(participantes, cuentas, pagos, cobros, interno, tx, habilitada);
    }

    @Test
    void sinCargaInicial_noHayQueEsperar() {
        assertThat(carga(false).listo()).isTrue();
    }

    @Test
    void primerArranque_copiaYRecienDespuesEstaLista() {
        when(interno.contactos(anyInt(), anyInt())).thenReturn(List.of());
        when(interno.cuentas()).thenReturn(List.of(new ClienteInterno.Cuenta(16L, "v2:acceso", "v2:refresh",
                LocalDateTime.now().plusMonths(6), "777")));
        when(interno.pagos(anyInt(), anyInt())).thenReturn(List.of(new ClienteInterno.Pago(5L, 63L, null, 16L, 20L, "mp-1",
                "APROBADO", new BigDecimal("60000"), LocalDateTime.now())));
        when(interno.solicitudes()).thenReturn(List.of(new ClienteInterno.Solicitud(3L, 16L, 20L, "CERTIFICADO",
                new BigDecimal("55000"), "APROBADO", "mp-doc", "CERTIFICADO - Lucía")));
        when(interno.cobrosPendientes()).thenReturn(List.of(new ClienteInterno.CobroPendiente(70L, 16L, 20L,
                new BigDecimal("60000"), "Consulta Psiquiátrica - Lucía", "https://mp/70", LocalDateTime.now())));
        CargaInicial carga = carga(true);
        assertThat(carga.listo()).isFalse();

        carga.cargarConReintentos();

        assertThat(carga.listo()).isTrue();
        // Los tokens se copian cifrados tal cual.
        verify(cuentas).guardar(new CuentasMp.Cuenta(16L, "v2:acceso", "v2:refresh",
                interno.cuentas().get(0).tokenVenceEn(), "777"));
        ArgumentCaptor<Pagos.Pago> pago = ArgumentCaptor.forClass(Pagos.Pago.class);
        verify(pagos).registrar(pago.capture(), eq(5L));
        assertThat(pago.getValue().referencia()).isEqualTo("63");
        verify(pagos).registrar(any(), isNull());
        ArgumentCaptor<Cobros.Cobro> cobro = ArgumentCaptor.forClass(Cobros.Cobro.class);
        verify(cobros, times(2)).crear(cobro.capture());
        assertThat(cobro.getAllValues()).extracting(Cobros.Cobro::referencia, Cobros.Cobro::estado)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("doc-3", "PAGADO"),
                        org.assertj.core.groups.Tuple.tuple("70", "LISTO"));
    }

    @Test
    void conDatos_listaSinCopiar() {
        when(participantes.cantidad()).thenReturn(28L);
        CargaInicial carga = carga(true);

        carga.cargarConReintentos();

        assertThat(carga.listo()).isTrue();
        verify(interno, never()).cuentas();
    }
}
