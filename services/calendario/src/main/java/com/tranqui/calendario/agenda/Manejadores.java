package com.tranqui.calendario.agenda;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

/**
 * Eventos que consume el calendario. Los de usuarios comparten el consumidor "participantes" y los de
 * turnos "turnos", para descartar eventos viejos de la misma entidad (sección 6.4).
 */
@Configuration
public class Manejadores {

    private final Participantes participantes;
    private final AgendaTurnos agenda;

    public Manejadores(Participantes participantes, AgendaTurnos agenda) {
        this.participantes = participantes;
        this.agenda = agenda;
    }

    // ------------------------------------------------------------------ turnos

    @Bean
    ManejadorEvento<TurnoConfirmadoData> turnoConfirmado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_CONFIRMADO_V1, TurnoConfirmadoData.class, e -> {
            TurnoConfirmadoData d = e.data();
            // Los documentos pedidos no ocupan agenda: no van a Google.
            if (d.esDocumento() || d.horaInicio() == null) {
                return;
            }
            agenda.confirmado(d.turnoId(), d.profesionalId(), d.pacienteId(), d.fecha(), LocalTime.parse(d.horaInicio()),
                    hora(d.horaFin()), d.modalidad());
        });
    }

    @Bean
    ManejadorEvento<TurnoReprogramadoData> turnoReprogramado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_REPROGRAMADO_V1, TurnoReprogramadoData.class, e -> {
            TurnoReprogramadoData d = e.data();
            if (d.fechaNueva() != null && d.horaInicioNueva() != null && !d.horaInicioNueva().isBlank()) {
                agenda.reprogramado(d.turnoId(), d.fechaNueva(), LocalTime.parse(d.horaInicioNueva()), hora(d.horaFinNueva()));
            }
        });
    }

    @Bean
    ManejadorEvento<TurnoCanceladoData> turnoCancelado() {
        return ManejadorEvento.conVersion("turnos", Topicos.TURNOS_TURNO_CANCELADO_V1, TurnoCanceladoData.class,
                e -> agenda.cancelado(e.data().turnoId()));
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

    /** Cuenta eliminada (Ley 25.326): si era un profesional con Google, se corta el canal y se borran sus datos. */
    @Bean
    ManejadorEvento<UsuarioEliminadoData> participanteEliminado(Canales canales, CuentasGoogle cuentas, EventosExternos externos) {
        return ManejadorEvento.de("participantes", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class, e -> {
            Long id = e.data().usuarioId();
            if (cuentas.buscar(id).isPresent()) {
                canales.detener(id);
                cuentas.borrar(id);
                externos.borrarDeProfesional(id);
            }
            participantes.eliminar(id);
        });
    }

    private static LocalTime hora(String hora) {
        return hora == null || hora.isBlank() ? null : LocalTime.parse(hora);
    }
}
