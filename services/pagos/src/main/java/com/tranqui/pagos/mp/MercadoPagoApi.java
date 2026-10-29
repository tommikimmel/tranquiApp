package com.tranqui.pagos.mp;

import java.math.BigDecimal;

/**
 * Lo que el servicio le pide a Mercado Pago. Detrás de una interfaz para probar la lógica sin Mercado Pago;
 * la implementación real ({@link MercadoPagoApiSdk}) hace las mismas llamadas que el monolito. Siempre con
 * el access token del profesional por pedido, nunca con el global del SDK.
 */
public interface MercadoPagoApi {

    /** Un pago de Mercado Pago: su id, estado ({@code approved}, {@code rejected}...), referencia y monto. */
    record PagoMp(Long id, String estado, String referencia, BigDecimal monto) {}

    /** Respuesta del OAuth (código o renovación). */
    record TokensOAuth(String accessToken, String refreshToken, long expiraEnSegundos, String userId) {}

    /** Crea la preferencia de Checkout Pro y devuelve su link ({@code init_point}). */
    String crearPreferencia(String accessToken, String titulo, BigDecimal monto, String referencia) throws Exception;

    PagoMp obtenerPago(String accessToken, long paymentId) throws Exception;

    /**
     * El pago más reciente de una referencia, aprobado si hay alguno (un paciente puede reintentar después
     * de un rechazo). {@code null} si no hay ninguno.
     */
    PagoMp buscarPorReferencia(String accessToken, String referencia) throws Exception;

    void reembolsar(String accessToken, long paymentId) throws Exception;

    TokensOAuth pedirTokens(String formulario) throws Exception;
}
