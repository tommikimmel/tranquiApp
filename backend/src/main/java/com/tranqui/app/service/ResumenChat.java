package com.tranqui.app.service;

import java.util.List;
import java.util.Map;

/**
 * Lo que la lista de pacientes del profesional necesita del chat: con qué pacientes tiene
 * conversación y cuántos mensajes sin leer le mandó cada uno. Mismo formato que la ruta interna
 * {@code /internal/chat/profesionales/{id}/resumen} del servicio de chat (Fase 2).
 */
public record ResumenChat(List<Long> pacientesConChat, Map<Long, Integer> noLeidosPorPaciente) {

    public static ResumenChat vacio() {
        return new ResumenChat(List.of(), Map.of());
    }
}
