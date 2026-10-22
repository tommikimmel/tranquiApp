package com.tranqui.calendario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de calendario (Fase 3 del plan de microservicios): Google Calendar y Meet de los
 * profesionales. OAuth, eventos de los turnos confirmados, sincronización de sus eventos propios y
 * los horarios ocupados que lee la disponibilidad. Mismas rutas que tenía el monolito.
 */
@SpringBootApplication
@EnableScheduling
public class CalendarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalendarioApplication.class, args);
    }
}
