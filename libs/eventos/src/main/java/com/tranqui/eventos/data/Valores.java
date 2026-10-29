package com.tranqui.eventos.data;

/**
 * Los valores conocidos de los campos de texto con un conjunto cerrado (los enums del monolito y de los servicios),
 * para validar el data de los eventos (Fase 10). Si se agrega un valor nuevo, se agrega acá antes de publicarlo.
 */
public final class Valores {

    public static final String ROLES = "ADMIN|PSIQUIATRA|PACIENTE|VISITADOR";
    public static final String ESTADOS_TURNO = "PENDIENTE_PAGO|PENDIENTE_VALIDACION|CONFIRMADO|CANCELADO|EXPIRADO";
    public static final String ASISTENCIAS = "ESPERANDO|LLEGO|AUSENTE|COMPLETADA";
    public static final String ESTADOS_SUSCRIPCION = "REGISTERED|PENDING_VERIFICATION|VERIFIED|SUBSCRIPTION_PENDING|ACTIVE"
            + "|PAST_DUE|SUSPENDED|CANCELLED|REJECTED";

    private Valores() {
    }
}
