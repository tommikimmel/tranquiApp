package com.tranqui.notificaciones.contacto;

import com.tranqui.notificaciones.interno.ClienteInterno;
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
    private final boolean habilitada;

    public CargaInicialContactos(ContactoService contactos, ClienteInterno interno,
                                 @Value("${notificaciones.carga-inicial.habilitada:true}") boolean habilitada) {
        this.contactos = contactos;
        this.interno = interno;
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
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (contactos.cantidad() > 0) {
                    return;
                }
                int total = cargar();
                log.info("Carga inicial de contactos terminada: {} usuarios", total);
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
