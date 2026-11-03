package com.tranqui.turnos.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.RecetaEmitidaData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.turnos.Cobros;
import com.tranqui.turnos.turnos.Gestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los eventos que consume turnos: pagos (confirmar el turno, rechazo, reembolso, link del checkout, cuentas
 * vinculadas), calendario (el Meet), profesionales (agenda, disponibilidad y tarifas), identidad (los
 * usuarios) y la receta emitida (monolito).
 */
@Configuration
public class Manejadores {

    private static final Logger log = LoggerFactory.getLogger(Manejadores.class);

    // ------------------------------------------------------------------ pagos y calendario

    @Bean
    ManejadorEvento<PagoAprobadoData> turnoPagado(Cobros cobros) {
        return ManejadorEvento.de("turno-pagado", Topicos.PAGOS_PAGO_APROBADO_V1, PagoAprobadoData.class, e -> cobros.pagoAprobado(e.data()));
    }

    @Bean
    ManejadorEvento<PagoRechazadoData> turnoPagoRechazado(Cobros cobros) {
        return ManejadorEvento.de("turno-pago-rechazado", Topicos.PAGOS_PAGO_RECHAZADO_V1, PagoRechazadoData.class,
                e -> cobros.pagoRechazado(e.data()));
    }

    @Bean
    ManejadorEvento<ReembolsoRealizadoData> turnoReembolsado(Cobros cobros) {
        return ManejadorEvento.de("turno-reembolsado", Topicos.PAGOS_REEMBOLSO_REALIZADO_V1, ReembolsoRealizadoData.class,
                e -> cobros.reembolsado(e.data()));
    }

    @Bean
    ManejadorEvento<CheckoutCreadoData> checkoutEnTurno(Cobros cobros) {
        return ManejadorEvento.de("checkout-en-turno", Topicos.PAGOS_CHECKOUT_CREADO_V1, CheckoutCreadoData.class,
                e -> cobros.checkout(e.data()));
    }

    @Bean
    ManejadorEvento<MeetCreadoData> meetEnTurno(Cobros cobros) {
        return ManejadorEvento.de("meet-en-turno", Topicos.CALENDARIO_MEET_CREADO_V1, MeetCreadoData.class, e -> cobros.meet(e.data()));
    }

    @Bean
    ManejadorEvento<CuentaMpVinculadaData> cuentaMpVinculada(Personas personas) {
        return ManejadorEvento.de("cuenta-mp-vinculada", Topicos.PAGOS_CUENTA_MP_VINCULADA_V1, CuentaMpVinculadaData.class,
                e -> personas.mercadoPago(e.data().profesionalId(), true));
    }

    @Bean
    ManejadorEvento<CuentaMpDesvinculadaData> cuentaMpDesvinculada(Personas personas) {
        return ManejadorEvento.de("cuenta-mp-desvinculada", Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1, CuentaMpDesvinculadaData.class,
                e -> personas.mercadoPago(e.data().profesionalId(), false));
    }

    // ------------------------------------------------------------------ profesionales

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> agendaProfesional(Profesionales profesionales, Personas personas) {
        return ManejadorEvento.conVersion("agenda-profesional", Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class, e -> {
                    personas.nombre(e.data().profesionalId(), e.data().nombre(), e.data().apellido());
                    // Sin perfil es un evento del monolito (vuelta atrás de la Fase 5): lo corrige la resincronización.
                    if (e.data().perfil() != null) {
                        profesionales.guardarPerfil(e.data().profesionalId(), e.data().perfil());
                    }
                });
    }

    @Bean
    ManejadorEvento<DisponibilidadProfesionalData> disponibilidadProfesional(Profesionales profesionales) {
        return ManejadorEvento.conVersion("disponibilidad-profesional", Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2,
                DisponibilidadProfesionalData.class, e -> profesionales.reemplazarDisponibilidad(e.data().profesionalId(), e.data().franjas()));
    }

    @Bean
    ManejadorEvento<TarifasProfesionalData> tarifasProfesional(Profesionales profesionales) {
        return ManejadorEvento.conVersion("tarifas-profesional", Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2,
                TarifasProfesionalData.class, e -> profesionales.reemplazarTarifas(e.data().profesionalId(), e.data().tarifas()));
    }

    @Bean
    ManejadorEvento<RecetaEmitidaData> recetaEmitida(Gestion gestion) {
        return ManejadorEvento.de("receta-emitida", Topicos.PROFESIONALES_RECETA_EMITIDA_V1, RecetaEmitidaData.class,
                e -> gestion.recetasEmitidas(e.data().profesionalId(), e.data().pacienteId()));
    }

    // ------------------------------------------------------------------ identidad

    @Bean
    ManejadorEvento<UsuarioRegistradoData> usuarioRegistrado(Copias copias) {
        return ManejadorEvento.de("usuario-registrado", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> refrescar(copias, e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> usuarioActualizado(Copias copias) {
        return ManejadorEvento.de("usuario-actualizado", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> refrescar(copias, e.data().usuarioId()));
    }

    /** El evento de identidad no trae el documento ni la obra social: se pide el usuario al monolito. */
    private static void refrescar(Copias copias, Long id) {
        try {
            copias.refrescarPersona(id);
        } catch (RuntimeException ex) {
            // Se reintenta el evento: sin la copia, la agenda mostraría datos viejos del paciente.
            log.warn("No se pudo refrescar al usuario {} desde el monolito: {}", id, ex.getMessage());
            throw ex;
        }
    }
}
