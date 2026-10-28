package com.tranqui.profesionales.clinica;

import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.interno.ClienteChat;
import org.springframework.stereotype.Component;

/**
 * Reglas de acceso a la historia clínica, ficha del paciente y recetas (Fase 7, decisión 2):
 * exige que el paciente sea atendido por el profesional (turno CONFIRMADO o PENDIENTE_VALIDACION,
 * o si no hay turno, un chat existente). Reutilizable para H2c (recetas).
 */
@Component
public class AccesoPacientes {

    private final TurnosAtendidos turnosAtendidos;
    private final ClienteChat clienteChat;

    public AccesoPacientes(TurnosAtendidos turnosAtendidos, ClienteChat clienteChat) {
        this.turnosAtendidos = turnosAtendidos;
        this.clienteChat = clienteChat;
    }

    /**
     * @throws ProhibidoException (403) con "El paciente no está entre los que atendés." si no lo atiende.
     */
    public void exigirAtendido(Long profesionalId, Long pacienteId) {
        if (!atendido(profesionalId, pacienteId)) {
            throw new ProhibidoException("El paciente no está entre los que atendés.");
        }
    }

    public boolean atendido(Long profesionalId, Long pacienteId) {
        if (turnosAtendidos.existeConfirmadoOPendiente(profesionalId, pacienteId)) {
            return true;
        }
        // El chat se consulta solo si no hay turno: llamada al servicio de chat por el gateway (vacía si está caído).
        return clienteChat.resumen(profesionalId).pacientesConChat().contains(pacienteId);
    }
}
