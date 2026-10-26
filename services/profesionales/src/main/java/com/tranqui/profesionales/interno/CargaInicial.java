package com.tranqui.profesionales.interno;

import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.profesionales.datos.Profesionales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Primer arranque de profesionales (paso 1 de "Migración de datos en cada fase"): copia del monolito los
 * profesionales con su perfil, foto, disponibilidad y tarifas, y lo que es del monolito (identidad,
 * verificación, suscripción y Mercado Pago). Todo o nada, reintenta con espera creciente si el monolito no
 * está listo, y solo corre con la base vacía. La versión de cada profesional arranca en la del usuario en
 * el monolito: calendario, chat, notificaciones y pagos ya vieron esos números en
 * {@code profesional.actualizado}.
 *
 * <p>El consumo de eventos y la sincronización de cada minuto esperan a que termine.
 */
@Component
public class CargaInicial implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    // La foto va en base64 dentro de cada profesional: páginas chicas.
    static final int PAGINA = 50;

    private final Profesionales profesionales;
    private final ClienteInterno interno;
    private final SincronizacionMonolito sincronizacion;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicial(Profesionales profesionales, ClienteInterno interno, SincronizacionMonolito sincronizacion,
                        TransactionTemplate tx, @Value("${profesionales.carga-inicial.habilitada:true}") boolean habilitada) {
        this.profesionales = profesionales;
        this.interno = interno;
        this.sincronizacion = sincronizacion;
        this.tx = tx;
        this.habilitada = habilitada;
        // Sin carga inicial (tests) no hay nada que esperar.
        this.terminada = !habilitada;
        if (!habilitada) {
            sincronizacion.habilitar();
        }
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
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-profesionales");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = profesionales.cantidad() == 0;
                }
                if (primerArranque) {
                    // Todo o nada: si se corta a la mitad, el próximo intento arranca de cero con la base vacía.
                    tx.executeWithoutResult(estado -> cargar());
                }
                terminada = true;
                sincronizacion.habilitar();
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de profesionales fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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
        int total = 0;
        int franjas = 0;
        int tarifas = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.ProfesionalMonolito> lote = interno.profesionales(pagina, PAGINA);
            for (ClienteInterno.ProfesionalMonolito p : lote) {
                sincronizacion.alta(p);
                franjas += p.disponibilidad() != null ? p.disponibilidad().size() : 0;
                tarifas += p.tarifas() != null ? p.tarifas().size() : 0;
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        log.info("Carga inicial de profesionales: {} profesionales, {} franjas de disponibilidad y {} tarifas",
                total, franjas, tarifas);
        return total;
    }
}
