package com.tranqui.identidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de identidad (Fase 9 del plan de microservicios): cuentas, sesiones,
 * usuarios, soporte y novedades.
 */
@SpringBootApplication
@EnableScheduling
public class IdentidadApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentidadApplication.class, args);
    }
}
