package com.tranqui.app.repository;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByMedicoIdAndFecha(Long medicoId, LocalDate fecha);
    
    List<Turno> findByMedicoIdAndFechaAndEstadoNot(Long medicoId, LocalDate fecha, EstadoTurno estado);

    List<Turno> findByEstadoAndFechaCreacionBefore(EstadoTurno estado, java.time.LocalDateTime limit);

    List<Turno> findByEstadoAndFechaAndRecordatorioEnviado(EstadoTurno estado, LocalDate fecha, Boolean recordatorioEnviado);

    List<Turno> findByMedicoIdAndEstadoNot(Long medicoId, EstadoTurno estado);

    boolean existsByPacienteEmailAndEstadoNot(String email, EstadoTurno estado);

    // Batched counterpart of existsByPacienteEmailAndEstadoNot for listing endpoints that need
    // the "first consultation" flag for many patients at once instead of one exists-query per row.
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t.paciente.email FROM Turno t WHERE t.paciente.email IN :emails AND t.estado != 'CANCELADO'")
    List<String> findPacienteEmailsConTurnoNoCancelado(@org.springframework.data.repository.query.Param("emails") List<String> emails);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) > 0 FROM Turno t WHERE t.paciente.email = :email AND t.fecha >= :fecha AND t.estado != 'CANCELADO'")
    boolean existsActiveTurnoByPacienteEmail(@org.springframework.data.repository.query.Param("email") String email, @org.springframework.data.repository.query.Param("fecha") LocalDate fecha);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t.paciente FROM Turno t WHERE t.medico.id = :medicoId AND t.estado IN ('CONFIRMADO', 'PENDIENTE_VALIDACION')")
    List<Usuario> findDistinctPacientesByMedicoId(@org.springframework.data.repository.query.Param("medicoId") Long medicoId);

    List<Turno> findByPacienteIdAndEstadoNot(Long pacienteId, EstadoTurno estado);

    boolean existsByMedicoIdAndGoogleEventId(Long medicoId, String googleEventId);
}
