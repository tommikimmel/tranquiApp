package com.tranqui.profesionales.interno;

import com.tranqui.eventos.ValidadorEventos;
import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.Solicitudes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Carga inicial de la historia clínica, las recetas y las solicitudes de documentos (Fase 7).
 * Copia del monolito las cuatro tablas con sus ids, estados y hashes de integridad tal cual,
 * y mueve las secuencias arriba del máximo para que las nuevas altas no choquen y la vuelta atrás
 * conserve los ids.
 *
 * <p>Corre solo si la propiedad {@code profesionales.clinica.habilitada} está activa (se prende en el corte,
 * en la ventana de mantenimiento) y si las cuatro tablas están vacías. Todo o nada en una sola transacción,
 * con reintentos. El consumo de eventos espera a que termine.
 */
@Component
public class CargaInicialClinica implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialClinica.class);
    static final int PAGINA = 500;

    private final Seguimientos seguimientos;
    private final Informes informes;
    private final Recetas recetas;
    private final Solicitudes solicitudes;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicialClinica(Seguimientos seguimientos, Informes informes, Recetas recetas,
                               Solicitudes solicitudes, ClienteInterno interno, TransactionTemplate tx,
                               @Value("${profesionales.clinica.habilitada:false}") boolean habilitada) {
        this.seguimientos = seguimientos;
        this.informes = informes;
        this.recetas = recetas;
        this.solicitudes = solicitudes;
        this.interno = interno;
        this.tx = tx;
        this.habilitada = habilitada;
        // Sin carga inicial (deshabilitada) no hay nada que esperar.
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
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-clinica");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = seguimientos.cantidad() == 0
                            && informes.cantidad() == 0
                            && recetas.cantidad() == 0
                            && solicitudes.cantidad() == 0;
                }
                if (primerArranque) {
                    // Todo o nada: si se corta a la mitad, el próximo intento arranca de cero con la base vacía.
                    tx.executeWithoutResult(estado -> cargar());
                }
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial clínica fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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
        int totalSeguimientos = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SeguimientoMonolito> lote = interno.seguimientos(pagina, PAGINA);
            for (ClienteInterno.SeguimientoMonolito s : lote) {
                seguimientos.insertar(s.seguimiento());
            }
            totalSeguimientos += lote.size();
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }
        seguimientos.ajustarSecuencia();

        int totalInformes = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.InformeMonolito> lote = interno.informes(pagina, PAGINA);
            for (ClienteInterno.InformeMonolito inf : lote) {
                informes.insertar(inf.informe());
            }
            totalInformes += lote.size();
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }
        informes.ajustarSecuencia();

        int totalRecetas = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.RecetaMonolito> lote = interno.recetas(pagina, PAGINA);
            for (ClienteInterno.RecetaMonolito r : lote) {
                recetas.insertar(r.receta());
            }
            totalRecetas += lote.size();
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }
        recetas.ajustarSecuencia();

        int totalSolicitudes = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SolicitudMonolito> lote = interno.solicitudes(pagina, PAGINA);
            for (ClienteInterno.SolicitudMonolito s : lote) {
                solicitudes.insertar(s.solicitud());
            }
            totalSolicitudes += lote.size();
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }
        solicitudes.ajustarSecuencia();

        log.info("Carga inicial clínica completada: {} seguimientos, {} informes, {} recetas y {} solicitudes",
                totalSeguimientos, totalInformes, totalRecetas, totalSolicitudes);
        return totalSeguimientos + totalInformes + totalRecetas + totalSolicitudes;
    }
}
