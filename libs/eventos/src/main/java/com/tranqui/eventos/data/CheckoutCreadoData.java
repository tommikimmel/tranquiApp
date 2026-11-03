package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento pagos.checkout.creado.v1 (Fase 4).
 * El servicio de pagos creó la preferencia de Mercado Pago a partir de turnos.turno.reservado o de
 * profesionales.concepto.solicitado. Consumido por turnos (hoy el monolito), que guarda el link en el
 * turno para "Mis Turnos". La referencia es la misma {@code external_reference} de Mercado Pago: el id
 * del turno, o {@code doc-<id>} para un documento de la API de conceptos.
 */
public record CheckoutCreadoData(
    @NotBlank String referencia,
    @Positive Long turnoId,
    @Positive Long solicitudId,
    String checkoutUrl
) {}
