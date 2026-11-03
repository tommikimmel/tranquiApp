package com.tranqui.pagos.cobros;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.ConceptoSolicitadoData;
import com.tranqui.eventos.data.TurnoReservadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.Cobros;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.Vinculacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * El checkout de cada cobro (decisión 1 de la spec): a partir de la reserva del turno o del pedido del
 * documento crea la preferencia de Mercado Pago, guarda el link y avisa con {@code pagos.checkout.creado}.
 * Las mismas reglas que MercadoPagoService del monolito: sin Mercado Pago o con la simulación prendida, el
 * link es el de prueba que el frontend muestra como "Simular pago".
 */
@Service
public class Checkout {

    private static final Logger log = LoggerFactory.getLogger(Checkout.class);
    static final String SIMULADO_TURNO = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id";
    static final String SIMULADO_DOCUMENTO = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-doc-preference-id";
    static final String SIN_CUENTA = "El profesional todavía no vinculó su cuenta de Mercado Pago.";

    private final Cobros cobros;
    private final Vinculacion vinculacion;
    private final MercadoPagoApi mercadoPago;
    private final OutboxPublisher outbox;
    private final boolean habilitado;
    private final boolean simulacion;

    public Checkout(Cobros cobros, Vinculacion vinculacion, MercadoPagoApi mercadoPago, OutboxPublisher outbox,
                    @Value("${mercadopago.enabled:false}") boolean habilitado,
                    @Value("${payment.simulation.enabled:false}") boolean simulacion) {
        this.cobros = cobros;
        this.vinculacion = vinculacion;
        this.mercadoPago = mercadoPago;
        this.outbox = outbox;
        this.habilitado = habilitado;
        this.simulacion = simulacion;
    }

    @Transactional
    public void paraTurno(TurnoReservadoData d) {
        String referencia = Cobros.referenciaTurno(d.turnoId());
        String titulo = d.tituloCobro() != null ? d.tituloCobro() : "Consulta Psiquiátrica";
        cobros.crear(new Cobros.Cobro(referencia, d.turnoId(), null, d.profesionalId(), d.pacienteId(), d.precio(), titulo,
                null, null, Cobros.PENDIENTE, null));
        crearPreferencia(referencia, SIMULADO_TURNO);
    }

    @Transactional
    public void paraDocumento(ConceptoSolicitadoData d) {
        String referencia = Cobros.referenciaDocumento(d.solicitudId());
        cobros.crear(new Cobros.Cobro(referencia, null, d.solicitudId(), d.profesionalId(), d.pacienteId(), d.precio(),
                d.titulo(), d.tipoConcepto(), null, Cobros.PENDIENTE, null));
        crearPreferencia(referencia, SIMULADO_DOCUMENTO);
    }

    /** Venció el bloqueo de 5 minutos sin pago. Si el pago llega igual, se procesa (como hoy). */
    @Transactional
    public void vencer(Long turnoId) {
        cobros.cambiarEstado(Cobros.referenciaTurno(turnoId), Cobros.VENCIDO);
    }

    private void crearPreferencia(String referencia, String simulado) {
        Cobros.Cobro cobro = cobros.buscar(referencia).orElseThrow();
        if (Cobros.LISTO.equals(cobro.estado()) || Cobros.PAGADO.equals(cobro.estado())) {
            return;
        }
        String url;
        if (!habilitado || simulacion) {
            url = simulado;
        } else {
            try {
                Optional<String> token = vinculacion.accessTokenValido(cobro.profesionalId());
                if (token.isEmpty() || Vinculacion.esDePrueba(token.get())) {
                    cobros.error(referencia, SIN_CUENTA);
                    log.warn("Cobro {} sin checkout: el profesional {} no tiene Mercado Pago vinculado", referencia,
                            cobro.profesionalId());
                    return;
                }
                url = mercadoPago.crearPreferencia(token.get(), cobro.titulo(), cobro.monto() != null ? cobro.monto() : BigDecimal.ZERO,
                        referencia);
            } catch (Exception e) {
                // Como el error de la reserva en el monolito: el paciente ve que no se pudo y vuelve a intentar.
                cobros.error(referencia, "Error al conectar con la pasarela de Mercado Pago: " + e.getMessage());
                log.error("No se pudo crear la preferencia de Mercado Pago del cobro {}", referencia, e);
                return;
            }
        }
        cobros.listo(referencia, url);
        outbox.publicar(Topicos.PAGOS_CHECKOUT_CREADO_V1, "cobro", referencia, 1,
                new CheckoutCreadoData(referencia, cobro.turnoId(), cobro.solicitudId(), url));
    }
}
