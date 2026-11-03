package com.tranqui.calendario.agenda;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.TurnosAgendados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Las tareas periódicas que tenía el monolito para Google Calendar (GoogleCalendarPollingScheduler y
 * GoogleCalendarWatchScheduler), más la limpieza de los turnos pasados.
 */
@Component
public class Programador {

    private static final Logger log = LoggerFactory.getLogger(Programador.class);

    private final CuentasGoogle cuentas;
    private final TurnosAgendados turnos;
    private final Sincronizacion sincronizacion;
    private final Canales canales;
    private final boolean googleHabilitado;

    public Programador(CuentasGoogle cuentas, TurnosAgendados turnos, Sincronizacion sincronizacion, Canales canales,
                       @Value("${google.calendar.enabled:false}") boolean googleHabilitado) {
        this.cuentas = cuentas;
        this.turnos = turnos;
        this.sincronizacion = sincronizacion;
        this.canales = canales;
        this.googleHabilitado = googleHabilitado;
    }

    /** Cada 5 minutos: sincroniza cada cuenta conectada y reintenta los turnos sin evento. */
    @Scheduled(fixedRate = 300_000, initialDelay = 30_000)
    public void sincronizarCuentas() {
        if (!googleHabilitado) {
            return;
        }
        for (CuentasGoogle.Cuenta cuenta : cuentas.todas()) {
            try {
                sincronizacion.incremental(cuenta.profesionalId());
            } catch (Exception e) {
                log.error("Fallo al sincronizar al profesional {}", cuenta.profesionalId(), e);
            }
        }
    }

    /** Los canales de Google no se renuevan en el lugar: los que vencen en 48 h se paran y se vuelven a crear. */
    @Scheduled(cron = "0 0 4 * * ?", zone = "America/Argentina/Cordoba")
    public void renovarCanales() {
        if (!canales.habilitados()) {
            return;
        }
        for (CuentasGoogle.Cuenta cuenta : cuentas.canalesPorVencer(LocalDateTime.now().plusHours(48))) {
            try {
                canales.renovar(cuenta.profesionalId());
            } catch (Exception e) {
                log.error("Fallo al renovar el canal de Google del profesional {}; se reintenta mañana", cuenta.profesionalId(), e);
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * ?", zone = "America/Argentina/Cordoba")
    public void borrarTurnosPasados() {
        int borrados = turnos.borrarPasados();
        if (borrados > 0) {
            log.info("Turnos pasados borrados de la copia del calendario: {}", borrados);
        }
    }
}
