package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos del evento pagos.alerta.admin.v1 (Fase 11): algo de la reconciliación de suscripciones que necesita que un
 * administrador actúe (facturas con error, facturación cerca del tope del monotributo). Consumido por notificaciones,
 * que le manda el mail de alerta a cada administrador, como hacía el monolito.
 */
public record AlertaAdminData(
    @NotBlank @Size(max = 200) String titulo,
    @NotBlank @Size(max = 2000) String mensaje
) {}
