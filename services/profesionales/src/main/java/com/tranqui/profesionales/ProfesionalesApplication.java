package com.tranqui.profesionales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de profesionales (Fase 5 del plan de microservicios): el perfil, el buscador, las tarifas y la
 * disponibilidad. Mismas rutas que tenía el monolito; la identidad, la verificación del administrador y la
 * suscripción siguen allá y se copian acá. La historia clínica y las recetas van en una fase propia.
 */
@SpringBootApplication
@EnableScheduling
public class ProfesionalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfesionalesApplication.class, args);
    }
}
