package com.tranqui.pagos.cobros;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ConceptoSolicitadoData;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoExpiradoData;
import com.tranqui.eventos.data.TurnoReservadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.Participantes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eventos que consume pagos. Los de turnos crean el checkout, lo vencen o reembolsan; los de usuarios
 * mantienen la copia de participantes (comparten el consumidor "participantes" para descartar eventos
 * viejos de la misma persona, sección 6.4).
 */
@Configuration
public class Manejadores {

    private final Participantes participantes;

    public Manejadores(Participantes participantes) {
        this.participantes = participantes;
    }

    // ------------------------------------------------------------------ cobros

    @Bean
    ManejadorEvento<TurnoReservadoData> checkoutDeTurno(Checkout checkout) {
        return ManejadorEvento.de("checkout-turno", Topicos.TURNOS_TURNO_RESERVADO_V1, TurnoReservadoData.class,
                e -> checkout.paraTurno(e.data()));
    }

    @Bean
    ManejadorEvento<ConceptoSolicitadoData> checkoutDeDocumento(Checkout checkout) {
        return ManejadorEvento.de("checkout-documento", Topicos.PROFESIONALES_CONCEPTO_SOLICITADO_V1,
                ConceptoSolicitadoData.class, e -> checkout.paraDocumento(e.data()));
    }

    @Bean
    ManejadorEvento<TurnoExpiradoData> cobroVencido(Checkout checkout) {
        return ManejadorEvento.de("cobro-vencido", Topicos.TURNOS_TURNO_EXPIRADO_V1, TurnoExpiradoData.class,
                e -> checkout.vencer(e.data().turnoId()));
    }

    @Bean
    ManejadorEvento<TurnoCanceladoData> reembolso(ReembolsosTurnos reembolsos) {
        return ManejadorEvento.de("reembolso", Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class,
                e -> reembolsos.alCancelar(e.data()));
    }

    // ------------------------------------------------------------------ usuarios

    @Bean
    ManejadorEvento<UsuarioRegistradoData> participanteRegistrado() {
        return ManejadorEvento.conVersion("participantes", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> participantes.guardar(e.data().usuarioId(), e.data().email(), e.data().nombre(), e.data().apellido(),
                        e.data().rol()));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> participanteActualizado() {
        return ManejadorEvento.conVersion("participantes", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> participantes.guardar(e.data().usuarioId(), e.data().email(), e.data().nombre(), e.data().apellido(),
                        e.data().rol()));
    }

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> participanteProfesional() {
        return ManejadorEvento.conVersion("participantes", Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class,
                e -> participantes.guardar(e.data().profesionalId(), null, e.data().nombre(), e.data().apellido(), null));
    }

    /**
     * Cuenta eliminada (Ley 25.326): se borran los tokens de Mercado Pago. Los pagos quedan: son registros
     * de cobros que hay que poder conciliar.
     */
    @Bean
    ManejadorEvento<UsuarioEliminadoData> participanteEliminado(CuentasMp cuentas) {
        return ManejadorEvento.de("participantes", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class, e -> {
            cuentas.borrar(e.data().usuarioId());
            participantes.eliminar(e.data().usuarioId());
        });
    }
}
