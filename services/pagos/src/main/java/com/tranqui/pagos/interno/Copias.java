package com.tranqui.pagos.interno;

import com.tranqui.pagos.datos.Suscriptores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * Copia de los suscriptores (identidad: rol, profesión y datos fiscales de cada profesional).
 * Se copia en el arranque y se mantiene al día con los eventos de identidad y una resincronización periódica.
 */
@Component
public class Copias {

    private static final Logger log = LoggerFactory.getLogger(Copias.class);
    static final int PAGINA = 500;

    private final ClienteInterno interno;
    private final Suscriptores suscriptores;
    private final TransactionTemplate tx;
    private final ObjectProvider<CargaInicial> cargaInicial;

    public Copias(ClienteInterno interno, Suscriptores suscriptores, TransactionTemplate tx,
                  ObjectProvider<CargaInicial> cargaInicial) {
        this.interno = interno;
        this.suscriptores = suscriptores;
        this.tx = tx;
        this.cargaInicial = cargaInicial;
    }

    /**
     * Trae del monolito a un suscriptor tras registrarse o actualizar sus datos.
     * Si el usuario no es suscriptor (404), se ignora. Si el monolito falla, tira la excepción para reintentar.
     */
    public void refrescarSuscriptor(Long id) {
        ClienteInterno.SuscriptorMonolito s;
        try {
            s = interno.suscriptor(id);
        } catch (HttpClientErrorException.NotFound e) {
            return;
        }
        if (s != null) {
            suscriptores.guardar(s.aSuscriptor());
        }
    }

    /** Copia paginada de todos los suscriptores (carga inicial y resincronización). */
    public int copiarSuscriptores() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.SuscriptorMonolito> lote = interno.suscriptores(pagina, PAGINA);
            for (ClienteInterno.SuscriptorMonolito s : lote) {
                suscriptores.guardar(s.aSuscriptor());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    @Scheduled(initialDelayString = "#{${pagos.suscripciones.resincronizacion-suscriptores-minutos:15} * 60000}",
            fixedDelayString = "#{${pagos.suscripciones.resincronizacion-suscriptores-minutos:15} * 60000}")
    public void resincronizarSuscriptores() {
        CargaInicial carga = cargaInicial.getIfAvailable();
        if (carga == null || !carga.listo()) {
            return;
        }
        try {
            int n = tx.execute(estado -> copiarSuscriptores());
            log.debug("Resincronización de suscriptores desde el monolito: {}", n);
        } catch (RuntimeException e) {
            log.warn("El monolito no respondió la resincronización de suscriptores; la copia sigue como está: {}", e.getMessage());
        }
    }
}
