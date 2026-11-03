package com.tranqui.notificaciones.contacto;

import com.tranqui.notificaciones.interno.ClienteInterno;
import com.tranqui.notificaciones.notificacion.Notificacion;
import com.tranqui.notificaciones.notificacion.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sección 6.6: si el servicio arranca con la copia de contactos vacía, la reconstruye desde el
 * dueño del dato (hoy el monolito) por la ruta interna. Corre en segundo plano y reintenta con
 * espera creciente si el monolito todavía no está listo; mientras tanto, los eventos que necesitan
 * un contacto se reintentan solos.
 */
@Component
public class CargaInicialContactos {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialContactos.class);
    private static final int TAMANIO_PAGINA = 500;

    private final ContactoService contactos;
    private final ClienteInterno interno;
    private final NotificacionRepository notificaciones;
    private final boolean habilitada;

    public CargaInicialContactos(ContactoService contactos, ClienteInterno interno,
                                 NotificacionRepository notificaciones,
                                 @Value("${notificaciones.carga-inicial.habilitada:true}") boolean habilitada) {
        this.contactos = contactos;
        this.interno = interno;
        this.notificaciones = notificaciones;
        this.habilitada = habilitada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!habilitada) {
            return;
        }
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-contactos");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = contactos.cantidad() == 0;
                }
                if (!primerArranque) {
                    return;
                }
                int total = contactos.cantidad() == 0 ? cargar() : 0;
                int vigentes = notificaciones.count() == 0 ? copiarNotificacionesVigentes() : 0;
                log.info("Carga inicial terminada: {} contactos y {} notificaciones vigentes", total, vigentes);
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de contactos fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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

    /**
     * Notificaciones de las últimas 24 horas del monolito, para que la campanita no quede vacía al
     * pasar al servicio. Solo en el primer arranque (copia de contactos vacía): después las crea el
     * servicio y las del monolito se borran solas a las 24 horas.
     */
    int copiarNotificacionesVigentes() {
        List<ClienteInterno.NotificacionInterna> vigentes = interno.notificacionesVigentes();
        for (ClienteInterno.NotificacionInterna n : vigentes) {
            notificaciones.save(Notificacion.copia(n.usuarioId(), n.titulo(), n.mensaje(), n.leido(), n.tipo(),
                    n.fechaCreacion()));
        }
        return vigentes.size();
    }

    /** Copia todas las páginas. Es idempotente: un usuario que ya estaba se actualiza. */
    public int cargar() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.ContactoInterno> lote = interno.contactos(pagina, TAMANIO_PAGINA);
            for (ClienteInterno.ContactoInterno c : lote) {
                contactos.guardar(c.id(), c.email(), c.nombre(), c.apellido(), c.rol(), c.emailHabilitado());
            }
            total += lote.size();
            if (lote.size() < TAMANIO_PAGINA) {
                return total;
            }
        }
    }
}
