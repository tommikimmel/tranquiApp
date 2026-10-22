package com.tranqui.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de chat (Fase 2 del plan de microservicios): mensajería en tiempo real entre pacientes,
 * profesionales y visitadores médicos, con su historial. Mismas rutas que tenía el monolito.
 */
@SpringBootApplication
@EnableScheduling
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
