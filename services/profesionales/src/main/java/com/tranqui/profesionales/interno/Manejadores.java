package com.tranqui.profesionales.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.data.ProfesionalVisibilidadCambiadaData;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Solicitudes;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eventos del monolito y de otros servicios que consume profesionales:
 * - Identidad y verificación para los profesionales (se refrescan con la sincronización).
 * - Identidad para la copia de personas (ficha del paciente, lista y recetas). Si el monolito
 *   no responde, se tira la excepción para reintentar el evento.
 * - Turnos para la copia de turnos atendidos (con descarte de versiones viejas).
 */
@Configuration
public class Manejadores {

    private static final Logger log = LoggerFactory.getLogger(Manejadores.class);

    private final SincronizacionMonolito sincronizacion;
    private final Profesionales profesionales;

    public Manejadores(SincronizacionMonolito sincronizacion, Profesionales profesionales) {
        this.sincronizacion = sincronizacion;
        this.profesionales = profesionales;
    }

    // ------------------------------------------------------------------ profesionales

    @Bean
    ManejadorEvento<UsuarioRegistradoData> profesionalRegistrado() {
        return ManejadorEvento.de("profesional-registrado", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> {
                    if (SincronizacionMonolito.ROL_PROFESIONAL.equals(e.data().rol())) {
                        refrescar(e.data().usuarioId());
                    }
                });
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> profesionalActualizado() {
        return ManejadorEvento.de("profesional-identidad", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> {
                    // También si dejó de ser profesional (cambio de rol) o si ya estaba copiado.
                    if (SincronizacionMonolito.ROL_PROFESIONAL.equals(e.data().rol())
                            || profesionales.porId(e.data().usuarioId()).isPresent()) {
                        refrescar(e.data().usuarioId());
                    }
                });
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> profesionalEliminado() {
        return ManejadorEvento.de("profesional-eliminado", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> profesionales.eliminar(e.data().usuarioId()));
    }

    /** La verificación del administrador (decisión 4: queda en el monolito). */
    @Bean
    ManejadorEvento<ProfesionalVisibilidadCambiadaData> verificacionCambiada() {
        return ManejadorEvento.de("profesional-verificacion", Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
                ProfesionalVisibilidadCambiadaData.class, e -> refrescar(e.data().profesionalId()));
    }

    private void refrescar(Long id) {
        try {
            sincronizacion.refrescar(id);
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar al profesional {} desde el monolito; lo corrige la sincronización: {}", id, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ Mercado Pago y suscripción (Fase 9)

    // Con la fuente de identidad, Mercado Pago y la suscripción llegan de pagos: antes, del monolito por /estado, así
    // que con la fuente del monolito estos eventos se descartan (la vuelta atrás no cambia nada).

    @Bean
    ManejadorEvento<CuentaMpVinculadaData> profesionalMpVinculada(ClienteInterno interno) {
        return ManejadorEvento.de("profesional-mp-vinculada", Topicos.PAGOS_CUENTA_MP_VINCULADA_V1, CuentaMpVinculadaData.class,
                e -> {
                    if (interno.fuenteIdentidad()) {
                        profesionales.guardarMercadoPago(idValido(e.data().profesionalId()), true, versionValida(e.aggregateVersion()));
                    }
                });
    }

    @Bean
    ManejadorEvento<CuentaMpDesvinculadaData> profesionalMpDesvinculada(ClienteInterno interno) {
        return ManejadorEvento.de("profesional-mp-desvinculada", Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1,
                CuentaMpDesvinculadaData.class, e -> {
                    if (interno.fuenteIdentidad()) {
                        profesionales.guardarMercadoPago(idValido(e.data().profesionalId()), false, versionValida(e.aggregateVersion()));
                    }
                });
    }

    @Bean
    ManejadorEvento<SuscripcionCambioEstadoV2Data> profesionalSuscripcion(ClienteInterno interno) {
        return ManejadorEvento.de("profesional-suscripcion", Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2,
                SuscripcionCambioEstadoV2Data.class, e -> {
                    if (!interno.fuenteIdentidad()) {
                        return;
                    }
                    SuscripcionCambioEstadoV2Data d = e.data();
                    if (d.estadoNuevo() == null || d.estadoNuevo().isBlank()) {
                        throw new EventoInvalidoException("Cambio de estado de suscripción sin estado nuevo");
                    }
                    profesionales.guardarSuscripcion(idValido(d.profesionalId()), d.estadoNuevo(), d.periodoHasta(),
                            d.puedeRecetar(), versionValida(e.aggregateVersion()));
                });
    }

    // Nunca se confía en lo que manda otro servicio: un id o una versión inválidos mandan el evento a la DLQ.
    private static Long idValido(Long id) {
        if (id == null || id <= 0) {
            throw new EventoInvalidoException("Evento con un id de profesional inválido: " + id);
        }
        return id;
    }

    private static long versionValida(int version) {
        if (version < 0) {
            throw new EventoInvalidoException("Evento con una versión inválida: " + version);
        }
        return version;
    }

    // ------------------------------------------------------------------ personas (copia)

    @Bean
    ManejadorEvento<UsuarioRegistradoData> personaRegistrada(Copias copias) {
        return ManejadorEvento.de("persona-registrada", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> refrescarPersona(copias, e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> personaActualizada(Copias copias) {
        return ManejadorEvento.de("persona-actualizada", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> refrescarPersona(copias, e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> personaEliminada(Personas personas) {
        return ManejadorEvento.de("persona-eliminada", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> personas.eliminar(e.data().usuarioId()));
    }

    private static void refrescarPersona(Copias copias, Long id) {
        try {
            copias.refrescarPersona(id);
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar a la persona {} desde el monolito: {}", id, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------ conceptos (documentos pagos)

    @Bean
    ManejadorEvento<ConceptoAprobadoData> conceptoAprobado(Solicitudes solicitudes) {
        return ManejadorEvento.de("concepto-aprobado", Topicos.PAGOS_CONCEPTO_APROBADO_V1,
                ConceptoAprobadoData.class, e -> {
                    ConceptoAprobadoData d = e.data();
                    var op = solicitudes.porId(d.solicitudId());
                    if (op.isEmpty()) {
                        log.warn("pagos.concepto.aprobado de una solicitud que no existe en profesionales: {}", d.solicitudId());
                        return;
                    }
                    var solicitud = op.get();
                    if ("APROBADO".equals(solicitud.estado())) {
                        log.info("Aprobación de concepto para solicitud ID {} ya estaba procesada; ignorando.", d.solicitudId());
                        return;
                    }
                    solicitudes.actualizarAprobado(d.solicitudId(), d.mpPaymentId());
                });
    }

    // ------------------------------------------------------------------ turnos (copia)

    @Bean
    ManejadorEvento<TurnoActualizadoData> turnoActualizado(TurnosAtendidos turnosAtendidos) {
        return ManejadorEvento.conVersion("turno-actualizado", Topicos.TURNOS_TURNO_ACTUALIZADO_V1,
                TurnoActualizadoData.class, e -> {
                    TurnoActualizadoData d = e.data();
                    turnosAtendidos.guardar(d.turnoId(), d.profesionalId(), d.pacienteId(), d.fecha(), d.estado(), e.aggregateVersion());
                });
    }
}
