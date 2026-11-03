package com.tranqui.pagos.interno;

import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga inicial de las ocho tablas de suscripciones y facturación (Fase 8, S2a).
 * Copia del monolito planes, features, plan_features, subscriptions, subscription_events,
 * subscription_payments, invoices e invoice_sequences con sus ids, montos, números y CAE tal cual,
 * y mueve las secuencias arriba del máximo para que las nuevas altas no choquen y la vuelta atrás
 * conserve los mismos números.
 *
 * <p>Corre únicamente si la propiedad {@code pagos.suscripciones.habilitada} está activa (se prende en el corte,
 * en la ventana de mantenimiento) y si las ocho tablas están vacías.
 * Antes de insertar cualquier fila, si detecta suscripciones duplicadas para un mismo {@code professional_id},
 * aborta sin insertar nada registrando los ids involucrados (decisión 5).
 * Todo o nada en una sola transacción, con reintentos. El consumo de eventos espera a que termine.
 */
@Component
public class CargaInicialSuscripciones implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialSuscripciones.class);
    static final int PAGINA = 500;
    static final int PAGINA_FACTURAS = 20;

    private final Planes planes;
    private final Suscripciones suscripciones;
    private final EventosSuscripcion eventosSuscripcion;
    private final PagosSuscripcion pagosSuscripcion;
    private final Facturas facturas;
    private final SecuenciasFactura secuenciasFactura;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicialSuscripciones(Planes planes,
                                     Suscripciones suscripciones,
                                     EventosSuscripcion eventosSuscripcion,
                                     PagosSuscripcion pagosSuscripcion,
                                     Facturas facturas,
                                     SecuenciasFactura secuenciasFactura,
                                     ClienteInterno interno,
                                     TransactionTemplate tx,
                                     @Value("${pagos.suscripciones.habilitada:false}") boolean habilitada) {
        this.planes = planes;
        this.suscripciones = suscripciones;
        this.eventosSuscripcion = eventosSuscripcion;
        this.pagosSuscripcion = pagosSuscripcion;
        this.facturas = facturas;
        this.secuenciasFactura = secuenciasFactura;
        this.interno = interno;
        this.tx = tx;
        this.habilitada = habilitada;
        // Sin carga inicial de suscripciones (deshabilitada) no hay nada que esperar.
        this.terminada = !habilitada;
    }

    @Override
    public boolean listo() {
        return terminada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!habilitada) {
            return;
        }
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-suscripciones");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = planes.cantidad() == 0
                            && planes.cantidadFeatures() == 0
                            && planes.cantidadPlanFeatures() == 0
                            && suscripciones.cantidad() == 0
                            && eventosSuscripcion.cantidad() == 0
                            && pagosSuscripcion.cantidad() == 0
                            && facturas.cantidad() == 0
                            && secuenciasFactura.cantidad() == 0;
                }
                if (primerArranque) {
                    // Todo o nada: si se corta a la mitad, el próximo intento arranca de cero con la base vacía.
                    tx.executeWithoutResult(estado -> cargar());
                }
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de suscripciones fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
                try {
                    Thread.sleep(esperaMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                esperaMs = Math.min(esperaMs * 2, 60_000);
            }
        }
    }

    public int cargar() {
        // 1. Obtener todas las suscripciones primero para verificar unicidad por profesional ANTES de insertar nada (decisión 5).
        List<ClienteInterno.SubscriptionMonolito> listaSuscripciones = new ArrayList<>();
        Map<Long, List<Long>> subsPorProfesional = new HashMap<>();

        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SubscriptionMonolito> lote = interno.suscripciones(pagina, PAGINA);
            for (ClienteInterno.SubscriptionMonolito s : lote) {
                listaSuscripciones.add(s);
                subsPorProfesional.computeIfAbsent(s.professionalId(), k -> new ArrayList<>()).add(s.id());
            }
            if (lote.size() < PAGINA) {
                break;
            }
        }

        List<String> duplicados = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : subsPorProfesional.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicados.add("profesionalId=" + entry.getKey() + " (suscripciones: " + entry.getValue() + ")");
                log.error("Duplicado de suscripciones para el professional_id {}: ids de suscripción involucrados {}",
                        entry.getKey(), entry.getValue());
            }
        }

        if (!duplicados.isEmpty()) {
            throw new IllegalStateException("Carga inicial abortada por suscripciones duplicadas del mismo profesional: "
                    + String.join(", ", duplicados));
        }

        // 2. Copiar planes
        int totalPlanes = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.PlanMonolito> lote = interno.planes(pagina, PAGINA);
            for (ClienteInterno.PlanMonolito p : lote) {
                planes.insertar(p.aPlan());
            }
            totalPlanes += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }

        // 3. Copiar features
        int totalFeatures = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.FeatureMonolito> lote = interno.features(pagina, PAGINA);
            for (ClienteInterno.FeatureMonolito f : lote) {
                planes.insertarFeature(f.aFeature());
            }
            totalFeatures += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }

        // 4. Copiar plan_features
        int totalPlanFeatures = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.PlanFeatureMonolito> lote = interno.planFeatures(pagina, PAGINA);
            for (ClienteInterno.PlanFeatureMonolito pf : lote) {
                planes.insertarPlanFeature(pf.aPlanFeature());
            }
            totalPlanFeatures += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        planes.ajustarSecuencias();

        // 5. Insertar suscripciones verificadas
        for (ClienteInterno.SubscriptionMonolito s : listaSuscripciones) {
            suscripciones.insertar(s.aSuscripcion());
        }
        suscripciones.ajustarSecuencia();
        int totalSuscripciones = listaSuscripciones.size();

        // 6. Copiar eventos de suscripción
        int totalEventos = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SubscriptionEventMonolito> lote = interno.eventosSuscripcion(pagina, PAGINA);
            for (ClienteInterno.SubscriptionEventMonolito e : lote) {
                eventosSuscripcion.insertar(e.aEvento());
            }
            totalEventos += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        eventosSuscripcion.ajustarSecuencia();

        // 7. Copiar pagos de suscripción
        int totalPagos = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SubscriptionPaymentMonolito> lote = interno.pagosSuscripcion(pagina, PAGINA);
            for (ClienteInterno.SubscriptionPaymentMonolito p : lote) {
                pagosSuscripcion.insertar(p.aPago());
            }
            totalPagos += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        pagosSuscripcion.ajustarSecuencia();

        // 8. Copiar facturas
        int totalFacturas = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.InvoiceMonolito> lote = interno.facturas(pagina, PAGINA_FACTURAS);
            for (ClienteInterno.InvoiceMonolito f : lote) {
                facturas.insertar(f.aFactura());
            }
            totalFacturas += lote.size();
            if (lote.size() < PAGINA_FACTURAS) {
                break;
            }
        }
        facturas.ajustarSecuencia();

        // 9. Copiar secuencias de facturación
        int totalSecuencias = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.InvoiceSequenceMonolito> lote = interno.secuenciasFactura(pagina, PAGINA);
            for (ClienteInterno.InvoiceSequenceMonolito s : lote) {
                secuenciasFactura.insertar(s.aSecuencia());
            }
            totalSecuencias += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        secuenciasFactura.ajustarSecuencia();

        log.info("Carga inicial de suscripciones completada: {} planes, {} features, {} plan-features, "
                        + "{} suscripciones, {} eventos, {} pagos, {} facturas y {} secuencias",
                totalPlanes, totalFeatures, totalPlanFeatures, totalSuscripciones, totalEventos,
                totalPagos, totalFacturas, totalSecuencias);

        return totalPlanes + totalFeatures + totalPlanFeatures + totalSuscripciones
                + totalEventos + totalPagos + totalFacturas + totalSecuencias;
    }
}
