package com.tranqui.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de pagos (Fase 4 del plan de microservicios): Mercado Pago de los turnos y los documentos
 * pagos. Checkout a partir de las reservas, webhooks, verificación al volver del checkout, reembolsos y
 * la vinculación de la cuenta de cada profesional. Mismas rutas que tenía el monolito; las suscripciones
 * (cuenta de la plataforma) siguen allá hasta la Fase 7.
 */
@SpringBootApplication
public class PagosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosApplication.class, args);
    }
}
