package com.tranqui.app.model;

// PENDIENTE = recién creado, sin respuesta de un admin todavía. ACTIVO = un admin ya respondió,
// conversación en curso. RESUELTO = cerrado por un admin. Un mensaje nuevo del creador sobre un
// ticket RESUELTO lo vuelve a PENDIENTE automáticamente (ver TicketService) — necesita que un
// admin lo vuelva a mirar.
public enum TicketEstado {
    PENDIENTE,
    ACTIVO,
    RESUELTO
}
