package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.repository.TurnoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fase 7: quién puede cargar en la historia clínica de un paciente, editar su ficha o recetarle. Es la misma
 * regla con la que se arma la lista de Pacientes ({@link ClinicalService#obtenerPacientesAtendidos}): un turno
 * confirmado o pendiente de validación con ese profesional, o un chat. Antes bastaba con conocer el id.
 */
@Component
public class AccesoPacientes {

    private static final List<EstadoTurno> ESTADOS_ATENDIDO = List.of(EstadoTurno.CONFIRMADO, EstadoTurno.PENDIENTE_VALIDACION);

    private final TurnoRepository turnoRepository;
    private final ResumenChatCliente resumenChatCliente;

    public AccesoPacientes(TurnoRepository turnoRepository, ResumenChatCliente resumenChatCliente) {
        this.turnoRepository = turnoRepository;
        this.resumenChatCliente = resumenChatCliente;
    }

    /** @throws AccessDeniedException (403) si el profesional no atiende al paciente. */
    public void exigirAtendido(Long profesionalId, Long pacienteId) {
        if (!atendido(profesionalId, pacienteId)) {
            throw new AccessDeniedException("El paciente no está entre los que atendés.");
        }
    }

    public boolean atendido(Long profesionalId, Long pacienteId) {
        if (turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn(profesionalId, pacienteId, ESTADOS_ATENDIDO)) {
            return true;
        }
        // El chat se consulta solo si no hay turno: es una llamada al servicio de chat (vacía si está caído).
        return resumenChatCliente.resumen(profesionalId).pacientesConChat().contains(pacienteId);
    }
}
