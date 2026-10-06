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

    // Used by TurnoService#enviarRecordatoriosConfirmacionAsistencia — ocupaAgenda = true excludes
    // document-only turnos (recetas, certificados, informes) automatically, since those are always
    // booked with ocupaAgenda = false (see reservarTurno). That's what keeps this feature scoped to
    // real turnos only, without needing a separate esReceta check.
    List<Turno> findByEstadoAndFechaAndOcupaAgendaAndConfirmacionAsistenciaEmailEnviado(
            EstadoTurno estado, LocalDate fecha, boolean ocupaAgenda, boolean confirmacionAsistenciaEmailEnviado);

    // JOIN FETCH t.paciente: every caller of this method (ClinicalService, MedicoService,
    // TurnoService.obtenerTodosTurnos) reads t.getPaciente() while mapping the result, which
    // without the fetch join fired one lazy-load SELECT per turno in the returned list.
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t JOIN FETCH t.paciente WHERE t.medico.id = :medicoId AND t.estado != :estado")
    List<Turno> findByMedicoIdAndEstadoNot(@org.springframework.data.repository.query.Param("medicoId") Long medicoId, @org.springframework.data.repository.query.Param("estado") EstadoTurno estado);

    List<Turno> findByMedicoIdAndEstado(Long medicoId, EstadoTurno estado);

    // Date-bounded counterparts used by MedicoService#obtenerStats — the dashboard only ever
    // needs the current + previous comparison windows (a few weeks/months at most), not a
    // médico's entire turno history, which used to be loaded in full on every dashboard refresh.
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t JOIN FETCH t.paciente WHERE t.medico.id = :medicoId AND t.estado != :estado AND t.fecha BETWEEN :desde AND :hasta")
    List<Turno> findByMedicoIdAndEstadoNotAndFechaBetween(
            @org.springframework.data.repository.query.Param("medicoId") Long medicoId,
            @org.springframework.data.repository.query.Param("estado") EstadoTurno estado,
            @org.springframework.data.repository.query.Param("desde") LocalDate desde,
            @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t WHERE t.medico.id = :medicoId AND t.estado = :estado AND t.fecha BETWEEN :desde AND :hasta")
    List<Turno> findByMedicoIdAndEstadoAndFechaBetween(
            @org.springframework.data.repository.query.Param("medicoId") Long medicoId,
            @org.springframework.data.repository.query.Param("estado") EstadoTurno estado,
            @org.springframework.data.repository.query.Param("desde") LocalDate desde,
            @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);

    // Lightweight aggregate (2 scalar columns, grouped) for "is this patient new in the selected
    // period" — needs each patient's true first-ever appointment date across all history, so it
    // can't be bounded by the same current/previous window as the queries above, but doesn't need
    // to load full Turno entities to answer that either.
    interface PrimeraFechaPorPaciente {
        Long getPacienteId();
        LocalDate getPrimeraFecha();
    }

    @org.springframework.data.jpa.repository.Query("SELECT t.paciente.id AS pacienteId, MIN(t.fecha) AS primeraFecha FROM Turno t WHERE t.medico.id = :medicoId AND t.estado != :estado GROUP BY t.paciente.id")
    List<PrimeraFechaPorPaciente> findPrimeraFechaPorPaciente(@org.springframework.data.repository.query.Param("medicoId") Long medicoId, @org.springframework.data.repository.query.Param("estado") EstadoTurno estado);

    boolean existsByPacienteEmailAndEstadoNot(String email, EstadoTurno estado);

    // Batched counterpart of existsByPacienteEmailAndEstadoNot for listing endpoints that need
    // the "first consultation" flag for many patients at once instead of one exists-query per row.
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t.paciente.email FROM Turno t WHERE t.paciente.email IN :emails AND t.estado != 'CANCELADO'")
    List<String> findPacienteEmailsConTurnoNoCancelado(@org.springframework.data.repository.query.Param("emails") List<String> emails);

    // Excludes both CANCELADO and EXPIRADO: a turno whose 5-minute payment hold timed out
    // (LiberarTurnosScheduler) must not keep blocking this patient from booking anything else —
    // including a document-only purchase — indefinitely. EXPIRADO used to be left out of this
    // exclusion, which turned any turno a cleanup pass marked EXPIRADO into a permanent block.
    // ocupaAgenda = true: a pending/confirmed document request (receta fuera de turno,
    // certificado, informe) doesn't hold a real slot and has its own independent lifecycle
    // (documentoEnviado), so it must never block booking a real turno — or another document —
    // the way an actual scheduled appointment does.
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) > 0 FROM Turno t WHERE t.paciente.email = :email AND t.fecha >= :fecha AND t.estado NOT IN ('CANCELADO', 'EXPIRADO') AND t.ocupaAgenda = true")
    boolean existsActiveTurnoByPacienteEmail(@org.springframework.data.repository.query.Param("email") String email, @org.springframework.data.repository.query.Param("fecha") LocalDate fecha);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t.paciente FROM Turno t WHERE t.medico.id = :medicoId AND t.estado IN ('CONFIRMADO', 'PENDIENTE_VALIDACION')")
    List<Usuario> findDistinctPacientesByMedicoId(@org.springframework.data.repository.query.Param("medicoId") Long medicoId);

    // JOIN FETCH t.medico: TurnoService.obtenerTurnosPaciente ("Mis turnos", patient-facing)
    // reads t.getMedico() fields for every row while mapping the DTO.
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t JOIN FETCH t.medico WHERE t.paciente.id = :pacienteId AND t.estado != :estado")
    List<Turno> findByPacienteIdAndEstadoNot(@org.springframework.data.repository.query.Param("pacienteId") Long pacienteId, @org.springframework.data.repository.query.Param("estado") EstadoTurno estado);

    boolean existsByMedicoIdAndGoogleEventId(Long medicoId, String googleEventId);

    // All turnos that still have a live Google Calendar event (i.e. TranquiApp created a Meet
    // event for them) — used to clean up the médico's real Google Calendar when they disconnect
    // the integration, since those events would otherwise be orphaned there forever.
    List<Turno> findByMedicoIdAndGoogleEventIdIsNotNull(Long medicoId);

    // Used by TurnoService#marcarRecetasEnviadasParaPaciente to auto-clear a médico's pending
    // "receta fuera de turno" purchases for a patient once an actual receta gets generated for
    // them — see that method for why this matches by médico+paciente rather than a turnoId.
    List<Turno> findByMedicoIdAndPacienteIdAndServicioIdAndEstadoAndDocumentoEnviado(
            Long medicoId, Long pacienteId, String servicioId, EstadoTurno estado, boolean documentoEnviado);

    // Used by RecetaFlagBackfillRunner — turnos booked (via the built-in "receta-fuera" servicio
    // or the legacy TipoTurno.RECETA) before Turno.esReceta existed, so it's still sitting at its
    // DB default (false).
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t WHERE t.esReceta = false AND (t.servicioId = 'receta-fuera' OR t.tipo = 'RECETA')")
    List<Turno> findLegacyRecetasSinFlag();

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Turno t WHERE t.medico.id = :medicoId AND t.fecha >= :fecha AND t.estado = :estado AND t.ocupaAgenda = true")
    List<Turno> findTurnosFuturosConfirmadosParaGoogle(
            @org.springframework.data.repository.query.Param("medicoId") Long medicoId,
            @org.springframework.data.repository.query.Param("fecha") LocalDate fecha,
            @org.springframework.data.repository.query.Param("estado") EstadoTurno estado);
}
