package com.tranqui.turnos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de turnos (Fase 6 del plan de microservicios): reservas, agenda, cancelación, reprogramación,
 * asistencia y documentos. Mismas rutas que tenía el monolito; los pacientes (identidad) siguen allá y la
 * agenda de cada profesional llega del servicio de profesionales.
 */
@SpringBootApplication
@EnableScheduling
public class TurnosApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnosApplication.class, args);
    }
}
