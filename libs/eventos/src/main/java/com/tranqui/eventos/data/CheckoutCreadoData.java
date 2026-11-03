package com.tranqui.eventos.data;

/**
 * Datos del evento pagos.checkout.creado.v1 (Fase 4).
 * El servicio de pagos creó la preferencia de Mercado Pago a partir de turnos.turno.reservado o de
 * profesionales.concepto.solicitado. Consumido por turnos (hoy el monolito), que guarda el link en el
 * turno para "Mis Turnos". La referencia es la misma {@code external_reference} de Mercado Pago: el id
 * del turno, o {@code doc-<id>} para un documento de la API de conceptos.
 */
public record CheckoutCreadoData(
    String referencia,
    Long turnoId,
    Long solicitudId,
    String checkoutUrl
) {}
