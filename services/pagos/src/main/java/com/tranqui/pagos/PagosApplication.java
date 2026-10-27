package com.tranqui.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio de pagos (Fase 4 del plan de microservicios): Mercado Pago de los turnos y los documentos
 * pagos. Checkout a partir de las reservas, webhooks, verificación al volver del checkout, reembolsos y
 * la vinculación de la cuenta de cada profesional.
 * Desde la Fase 8 (S2a), maneja además los planes, suscripciones, facturación ARCA y copia de suscriptores.
 */
@SpringBootApplication
@EnableScheduling
public class PagosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosApplication.class, args);
    }
}
