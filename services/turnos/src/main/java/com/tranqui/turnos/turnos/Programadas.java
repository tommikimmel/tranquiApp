package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.CargaInicial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Las tareas de turnos que corrían en el monolito: el vencimiento del bloqueo de 5 minutos
 * ({@code LiberarTurnosScheduler}, cada minuto) y el recordatorio de asistencia
 * ({@code NotificationScheduler}, a las 9 de la Argentina). No corren hasta que termina la carga inicial.
 */
@Component
public class Programadas {

    private static final Logger log = LoggerFactory.getLogger(Programadas.class);

    private final Turnos turnos;
    private final Personas personas;
    private final Gestion gestion;
    private final Publicador publicador;
    private final CargaInicial cargaInicial;
    private final TransactionTemplate tx;

    public Programadas(Turnos turnos, Personas personas, Gestion gestion, Publicador publicador, CargaInicial cargaInicial,
                       TransactionTemplate tx) {
        this.turnos = turnos;
        this.personas = personas;
        this.gestion = gestion;
        this.publicador = publicador;
        this.cargaInicial = cargaInicial;
        this.tx = tx;
    }

    /** Como el monolito: la reserva sin pagar queda cancelada y se avisa {@code turno.expirado}. */
    @Scheduled(fixedRate = 60_000)
    public void vencerReservas() {
        if (!cargaInicial.listo()) {
            return;
        }
        for (Turno pendiente : turnos.pendientesVencidos(LocalDateTime.now().minusMinutes(5))) {
            tx.executeWithoutResult(estado -> turnos.porIdParaActualizar(pendiente.id)
                    .filter(t -> Turno.PENDIENTE_PAGO.equals(t.estado))
                    .ifPresent(t -> {
                        t.estado = Turno.CANCELADO;
                        turnos.guardar(t);
                        publicador.expirado(t);
                        publicador.actualizado(t);
                    }));
        }
    }

    /**
     * Turnos de agenda de mañana o pasado que todavía no recibieron el recordatorio: un token de un solo uso
     * por turno y {@code turno.recordatorio.programado} (el mail con los dos botones lo arma notificaciones,
     * que pide los enlaces con el token a la ruta interna). Saltea a los pacientes que no aceptan mails.
     */
    @Scheduled(cron = "0 0 9 * * ?", zone = "America/Argentina/Cordoba")
    public void recordatorios() {
        if (!cargaInicial.listo()) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        for (Turno candidato : turnos.paraRecordatorio(hoy.plusDays(1), hoy.plusDays(2))) {
            boolean aceptaMails = personas.porId(candidato.pacienteId).map(Personas.Persona::emailHabilitado).orElse(false);
            if (!aceptaMails) {
                continue;
            }
            try {
                tx.executeWithoutResult(estado -> turnos.porIdParaActualizar(candidato.id).ifPresent(t -> {
                    t.tokenConfirmacionAsistencia = UUID.randomUUID().toString();
                    t.confirmacionAsistenciaEmailEnviado = true;
                    turnos.guardar(t);
                    String modalidad = "ONLINE".equals(t.modalidad) ? "Online (videollamada)"
                            : "PRESENCIAL".equals(t.modalidad) ? "Presencial" : null;
                    String direccion = "PRESENCIAL".equals(t.modalidad) ? gestion.direccion(t) : null;
                    publicador.recordatorio(t, modalidad, Etiquetas.tipo(t), direccion);
                    publicador.actualizado(t);
                }));
            } catch (RuntimeException e) {
                log.error("No se pudo programar el recordatorio del turno {}", candidato.id, e);
            }
        }
    }
}
