package com.tranqui.profesionales.interno;

import com.tranqui.eventos.ValidadorEventos;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.profesionales.datos.Agendas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Tarifas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lo que es del monolito (identidad, verificación del administrador, suscripción y Mercado Pago), copiado
 * acá. La suscripción no publica eventos y el acceso vence con la hora: cada
 * {@code profesionales.sincronizacion-segundos} se pide el estado de todos los profesionales. Además, los
 * eventos de identidad y de verificación refrescan a uno solo enseguida ({@link #refrescar}).
 *
 * <p>La foto (base64, puede pesar varios MB) no viaja en el estado: se pide aparte cuando cambia su hash.
 *
 * <p>Fase 9, con la fuente de identidad ({@code TRANQUI_FUENTE_IDENTIDAD=identidad}): la identidad y la verificación
 * salen del servicio de identidad, y Mercado Pago y la suscripción de pagos (su dueño), por eventos
 * ({@link Manejadores}) y por la resincronización de cada pasada. Ningún dato de pagos se pisa con lo de identidad.
 */
@Component
public class SincronizacionMonolito {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionMonolito.class);
    static final String ROL_PROFESIONAL = "PSIQUIATRA";
    static final int PAGINA_PAGOS = 200;

    private final ClienteInterno interno;
    private final Profesionales profesionales;
    private final Agendas agendas;
    private final Tarifas tarifas;
    private final TransactionTemplate tx;
    // La prende la carga inicial cuando termina: antes no hay con qué comparar.
    private volatile boolean habilitada;

    public SincronizacionMonolito(ClienteInterno interno, Profesionales profesionales, Agendas agendas, Tarifas tarifas,
                                  TransactionTemplate tx) {
        this.interno = interno;
        this.profesionales = profesionales;
        this.agendas = agendas;
        this.tarifas = tarifas;
        this.tx = tx;
    }

    @Scheduled(initialDelayString = "#{${profesionales.sincronizacion-segundos:60} * 1000}",
            fixedDelayString = "#{${profesionales.sincronizacion-segundos:60} * 1000}")
    public void sincronizar() {
        if (!habilitada) {
            return;
        }
        try {
            int cambios = sincronizarEstado(interno.estado());
            if (cambios > 0) {
                log.info("Sincronización de identidad: {} profesionales actualizados", cambios);
            }
        } catch (RuntimeException e) {
            log.warn("No respondió la sincronización de identidad de profesionales; se reintenta en la próxima pasada: {}",
                    e.getMessage());
        }
        if (interno.fuenteIdentidad()) {
            sincronizarPagos();
        }
    }

    /** Fase 9: Mercado Pago y la suscripción de cada profesional, de pagos (su dueño). */
    void sincronizarPagos() {
        int cambios = 0;
        try {
            for (int pagina = 0; ; pagina++) {
                List<ClienteInterno.CuentaMp> lote = interno.cuentasMp(pagina, PAGINA_PAGOS);
                for (ClienteInterno.CuentaMp c : lote) {
                    // Nunca se confía en lo que manda otro servicio: una fila inválida se saltea.
                    if (c == null || c.profesionalId() == null || c.profesionalId() <= 0 || c.version() < 0) {
                        log.warn("Resincronización de Mercado Pago: se saltea una fila inválida");
                        continue;
                    }
                    if (profesionales.guardarMercadoPago(c.profesionalId(), c.vinculada(), c.version())) cambios++;
                }
                if (ValidadorEventos.recibidas(lote) < PAGINA_PAGOS) break;
            }
            for (int pagina = 0; ; pagina++) {
                List<ClienteInterno.EstadoSuscripcion> lote = interno.estadoSuscripciones(pagina, PAGINA_PAGOS);
                for (ClienteInterno.EstadoSuscripcion e : lote) {
                    if (e == null || e.professionalId() == null || e.professionalId() <= 0 || e.version() == null
                            || e.version() < 0 || e.estado() == null || e.estado().isBlank()) {
                        log.warn("Resincronización de suscripciones: se saltea una fila inválida");
                        continue;
                    }
                    if (profesionales.guardarSuscripcion(e.professionalId(), e.estado(), e.periodoHasta(), e.puedeRecetar(),
                            e.version())) cambios++;
                }
                if (ValidadorEventos.recibidas(lote) < PAGINA_PAGOS) break;
            }
            if (cambios > 0) {
                log.info("Sincronización con pagos: {} datos de Mercado Pago o suscripción actualizados", cambios);
            }
        } catch (RuntimeException e) {
            log.warn("Pagos no respondió la sincronización de Mercado Pago y suscripciones; se reintenta en la próxima pasada: {}",
                    e.getMessage());
        }
    }

    public void habilitar() {
        this.habilitada = true;
    }

    int sincronizarEstado(List<ClienteInterno.ProfesionalMonolito> estado) {
        Map<Long, Profesionales.Profesional> locales = profesionales.profesionales().stream()
                .collect(Collectors.toMap(Profesionales.Profesional::id, Function.identity()));
        Set<Long> vistos = new HashSet<>();
        int cambios = 0;
        for (ClienteInterno.ProfesionalMonolito p : estado) {
            vistos.add(p.id());
            Profesionales.Profesional local = locales.get(p.id());
            try {
                if (local == null || !Objects.equals(local.fotoHash(), p.fotoHash())) {
                    // Nuevo para el servicio, o le cambió la foto: se pide completo.
                    refrescar(p.id());
                    cambios++;
                } else if (distinto(local, p)) {
                    guardar(p);
                    cambios++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo sincronizar el profesional {}: {}", p.id(), e.getMessage());
            }
        }
        // Los que el monolito ya no lista como profesionales (cambió el rol o se dio de baja).
        for (Long id : locales.keySet()) {
            if (!vistos.contains(id)) {
                try {
                    refrescar(id);
                    cambios++;
                } catch (RuntimeException e) {
                    log.warn("No se pudo sincronizar el profesional {}: {}", id, e.getMessage());
                }
            }
        }
        return cambios;
    }

    /**
     * Trae del monolito a un usuario y lo copia: si es un profesional nuevo, con su perfil, disponibilidad y
     * tarifas de allá; si ya estaba, solo lo del monolito. Si el monolito ya no lo tiene, se borra.
     */
    public void refrescar(Long id) {
        ClienteInterno.ProfesionalMonolito p;
        try {
            p = interno.profesional(id);
        } catch (HttpClientErrorException.NotFound e) {
            profesionales.eliminar(id);
            log.info("El usuario {} ya no existe en el monolito: se borra el profesional", id);
            return;
        }
        if (p == null) {
            return;
        }
        boolean existe = profesionales.porId(id).isPresent();
        if (existe) {
            guardar(p);
        } else if (ROL_PROFESIONAL.equals(p.rol())) {
            alta(p);
        }
    }

    /** Con la fuente de identidad, solo lo de identidad: Mercado Pago y la suscripción son de pagos. */
    private void guardar(ClienteInterno.ProfesionalMonolito p) {
        if (interno.fuenteIdentidad()) {
            profesionales.guardarDatosIdentidad(p.datos());
        } else {
            profesionales.guardarDatosMonolito(p.datos());
        }
    }

    /**
     * Alta con lo que tenía el monolito: así su copia y la del servicio arrancan iguales. Con la fuente de identidad,
     * el perfil es el que cargó al registrarse y Mercado Pago y la suscripción llegan después de pagos.
     */
    void alta(ClienteInterno.ProfesionalMonolito p) {
        tx.executeWithoutResult(estado -> {
            profesionales.alta(p.datos(), p.perfil());
            if (p.disponibilidad() != null) {
                agendas.reemplazarTodas(p.id(), p.disponibilidad());
            }
            if (p.tarifas() != null) {
                for (TarifasProfesionalData.Tarifa t : p.tarifas()) {
                    tarifas.guardar(p.id(), t);
                }
            }
        });
    }

    private boolean distinto(Profesionales.Profesional l, ClienteInterno.ProfesionalMonolito p) {
        boolean identidad = l.version() < p.version()
                || !Objects.equals(l.email(), p.email()) || !Objects.equals(l.rol(), p.rol())
                || !Objects.equals(l.profession(), p.profession()) || !Objects.equals(l.nombre(), p.nombre())
                || !Objects.equals(l.apellido(), p.apellido()) || !Objects.equals(l.telefono(), p.telefono())
                || !Objects.equals(l.sexo(), p.sexo()) || !Objects.equals(l.fechaNacimiento(), p.fechaNacimiento())
                || !Objects.equals(l.cuil(), p.cuil()) || !Objects.equals(l.tipoDocumento(), p.tipoDocumento())
                || !Objects.equals(l.numeroDocumento(), p.numeroDocumento())
                || !Objects.equals(l.verificadoAdmin(), p.verificadoAdmin());
        if (interno.fuenteIdentidad()) {
            return identidad;
        }
        return identidad || l.mpVinculada() != p.mpVinculada()
                || !Objects.equals(l.suscripcionEstado(), p.suscripcionEstado())
                || !Objects.equals(l.suscripcionPeriodoHasta(), p.suscripcionPeriodoHasta())
                || l.puedeRecetar() != p.puedeRecetar();
    }
}
