package com.tranqui.chat.copias;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoExpiradoData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Handlers de las copias locales del chat. Los de usuarios comparten el consumidor "participantes"
 * y los de turnos "turnos", para descartar eventos viejos de la misma entidad (sección 6.4).
 */
@Configuration
public class ManejadoresCopias {

    private final Copias copias;

    public ManejadoresCopias(Copias copias) {
        this.copias = copias;
    }

    @Bean
    ManejadorEvento<UsuarioRegistradoData> participanteRegistrado() {
        return ManejadorEvento.conVersion("participantes", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> copias.guardarParticipante(e.data().usuarioId(), e.data().email(), e.data().nombre(),
                        e.data().apellido(), e.data().rol()));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> participanteActualizado() {
        return ManejadorEvento.conVersion("participantes", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> copias.guardarParticipante(e.data().usuarioId(), e.data().email(), e.data().nombre(),
                        e.data().apellido(), e.data().rol()));
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> participanteEliminado() {
        return ManejadorEvento.de("participantes", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> copias.eliminarParticipante(e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> participanteProfesional() {
        return ManejadorEvento.conVersion("participantes", Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class,
                e -> copias.guardarParticipante(e.data().profesionalId(), null, e.data().nombre(), e.data().apellido(), null));
    }

    @Bean
    ManejadorEvento<TurnoConfirmadoData> turnoConfirmado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_CONFIRMADO_V1, TurnoConfirmadoData.class, e -> {
            TurnoConfirmadoData d = e.data();
            // Los documentos (recetas, certificados) no tienen fecha real: no cuentan para la prioridad.
            if (!d.esDocumento() && d.fecha() != null) {
                copias.guardarTurnoConfirmado(d.turnoId(), d.pacienteId(), d.profesionalId(), d.fecha(), d.horaInicio());
            }
        });
    }

    @Bean
    ManejadorEvento<TurnoReprogramadoData> turnoReprogramado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_REPROGRAMADO_V1, TurnoReprogramadoData.class,
                e -> copias.moverTurno(e.data().turnoId(), e.data().fechaNueva(), e.data().horaInicioNueva()));
    }

    @Bean
    ManejadorEvento<TurnoCanceladoData> turnoCancelado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class,
                e -> copias.quitarTurno(e.data().turnoId()));
    }

    @Bean
    ManejadorEvento<TurnoExpiradoData> turnoExpirado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_EXPIRADO_V1, TurnoExpiradoData.class,
                e -> copias.quitarTurno(e.data().turnoId()));
    }

    @Scheduled(cron = "0 10 0 * * ?", zone = "America/Argentina/Buenos_Aires")
    public void limpiarTurnosPasados() {
        copias.borrarTurnosPasados();
    }
}
