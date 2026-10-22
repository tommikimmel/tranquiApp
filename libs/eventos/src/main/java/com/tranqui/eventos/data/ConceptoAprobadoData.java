package com.tranqui.eventos.data;

/**
 * Datos del evento pagos.concepto.aprobado.v1 (agregado en la Fase 2).
 * Se aprobó el pago de una solicitud de documento ("concepto"). Consumido por notificaciones, que
 * avisa en vivo al profesional (sin guardar la notificación, como hacía el monolito). Hasta la
 * Fase 2 ese aviso salía directo del monolito por /ws-tranqui, que pasa al servicio de chat.
 *
 * <p>Campo opcional agregado en la Fase 4: el id del pago en Mercado Pago, que el monolito guarda en
 * la solicitud cuando la aprobación llega del servicio de pagos.
 */
public record ConceptoAprobadoData(
    Long solicitudId,
    Long profesionalId,
    Long pacienteId,
    String tipoConcepto,
    String mpPaymentId
) {
    public ConceptoAprobadoData(Long solicitudId, Long profesionalId, Long pacienteId, String tipoConcepto) {
        this(solicitudId, profesionalId, pacienteId, tipoConcepto, null);
    }
}
