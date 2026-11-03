package com.tranqui.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de notificaciones (Fase 1 del plan de microservicios): manda los mails y crea las
 * notificaciones dentro de la app a partir de eventos. No recibe llamadas de otros servicios.
 */
@SpringBootApplication
@EnableScheduling
public class NotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificacionesApplication.class, args);
    }
}
