package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private SeguimientoDiarioRepository seguimientoDiarioRepository;
    @Mock private InformeClinicoRepository informeClinicoRepository;
    @Mock private NotificacionService notificacionService;
    @Mock private MensajeRepository mensajeRepository;

    @InjectMocks
    private ClinicalService clinicalService;

    private Usuario medico(String email) {
        return Usuario.builder().id(1L).nombre("Dra. Paula").email(email).rol(Rol.PSIQUIATRA).build();
    }

    private Usuario paciente(Long id, String nombre) {
        return Usuario.builder().id(id).nombre(nombre).email(nombre.toLowerCase() + "@mail.com").rol(Rol.PACIENTE).build();
    }

    // ── obtenerPacientesAtendidos ────────────────────────────────────

    @Test
    void obtenerPacientesAtendidos_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("noexiste@mail.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.obtenerPacientesAtendidos("noexiste@mail.com"));
    }

    @Test
    void obtenerPacientesAtendidos_shouldMergePatientsFromTurnosAndChatsAndComputePriority() {
        Usuario medico = medico("dra@mail.com");
        Usuario pacienteConTurnoUrgente = paciente(10L, "Ana");
        Usuario pacienteSinTurno = paciente(20L, "Beto");

        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(turnoRepository.findDistinctPacientesByMedicoId(1L))
                .thenReturn(List.of(pacienteConTurnoUrgente));
        // Beto only has chat history, no appointment at all
        when(mensajeRepository.findPacientesConMensajesConMedico(1L))
                .thenReturn(List.of(pacienteConTurnoUrgente, pacienteSinTurno));

        LocalDate hoy = LocalDate.now();
        Turno turnoConfirmadoProximo = Turno.builder()
                .id(100L).paciente(pacienteConTurnoUrgente).medico(medico)
                .fecha(hoy.plusDays(1)).estado(EstadoTurno.CONFIRMADO).build();
        when(turnoRepository.findByMedicoIdAndEstadoNot(1L, EstadoTurno.CANCELADO))
                .thenReturn(List.of(turnoConfirmadoProximo));

        List<PacienteDto> result = clinicalService.obtenerPacientesAtendidos("dra@mail.com");

        assertEquals(2, result.size());
        PacienteDto ana = result.stream().filter(d -> d.getId().equals(10L)).findFirst().orElseThrow();
        assertEquals("PRIORIDAD_ALTA", ana.getPrioridadClinica());
        assertFalse(ana.isSinTurno());

        PacienteDto beto = result.stream().filter(d -> d.getId().equals(20L)).findFirst().orElseThrow();
        assertTrue(beto.isSinTurno());
        assertEquals("Sin turnos registrados", beto.getUltimaVisita());
        assertEquals("PRIORIDAD_BAJA", beto.getPrioridadClinica());
    }

    @Test
    void obtenerPacientesAtendidos_shouldReportLastVisitDateWhenPastAppointmentsExist() {
        Usuario medico = medico("dra@mail.com");
        Usuario pac = paciente(10L, "Ana");

        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(turnoRepository.findDistinctPacientesByMedicoId(1L)).thenReturn(List.of(pac));
        when(mensajeRepository.findPacientesConMensajesConMedico(1L)).thenReturn(List.of());

        LocalDate hoy = LocalDate.now();
        Turno pasado1 = Turno.builder().id(1L).paciente(pac).medico(medico).fecha(hoy.minusDays(10)).estado(EstadoTurno.CONFIRMADO).build();
        Turno pasado2 = Turno.builder().id(2L).paciente(pac).medico(medico).fecha(hoy.minusDays(2)).estado(EstadoTurno.CONFIRMADO).build();
        when(turnoRepository.findByMedicoIdAndEstadoNot(1L, EstadoTurno.CANCELADO))
                .thenReturn(List.of(pasado1, pasado2));

        List<PacienteDto> result = clinicalService.obtenerPacientesAtendidos("dra@mail.com");

        assertEquals(1, result.size());
        assertEquals(hoy.minusDays(2).toString(), result.get(0).getUltimaVisita());
        assertFalse(result.get(0).isSinTurno());
    }

    @Test
    void obtenerPacientesAtendidos_shouldIncludeCredentialInfoWhenPresent() {
        Usuario medico = medico("dra@mail.com");
        Usuario pac = paciente(10L, "Ana");
        pac.setCredencialCodEntidad(123);
        pac.setCredencialPan("PAN1");

        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(turnoRepository.findDistinctPacientesByMedicoId(1L)).thenReturn(List.of(pac));
        when(mensajeRepository.findPacientesConMensajesConMedico(1L)).thenReturn(List.of());
        when(turnoRepository.findByMedicoIdAndEstadoNot(1L, EstadoTurno.CANCELADO)).thenReturn(List.of());

        List<PacienteDto> result = clinicalService.obtenerPacientesAtendidos("dra@mail.com");

        assertNotNull(result.get(0).getCredencial());
        assertEquals(123, result.get(0).getCredencial().getCodEntidad());
    }

    // ── obtenerSeguimientos / obtenerInformes (medico) ───────────────

    @Test
    void obtenerSeguimientos_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.obtenerSeguimientos(1L, "x@mail.com"));
    }

    @Test
    void obtenerSeguimientos_shouldReturnRepositoryResult() {
        Usuario medico = medico("dra@mail.com");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        List<SeguimientoDiario> expected = List.of(SeguimientoDiario.builder().id(1L).build());
        when(seguimientoDiarioRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(5L, 1L)).thenReturn(expected);

        assertEquals(expected, clinicalService.obtenerSeguimientos(5L, "dra@mail.com"));
    }

    @Test
    void obtenerInformes_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.obtenerInformes(1L, "x@mail.com"));
    }

    @Test
    void obtenerInformes_shouldReturnRepositoryResult() {
        Usuario medico = medico("dra@mail.com");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        List<InformeClinico> expected = List.of(InformeClinico.builder().id(1L).build());
        when(informeClinicoRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(5L, 1L)).thenReturn(expected);

        assertEquals(expected, clinicalService.obtenerInformes(5L, "dra@mail.com"));
    }

    // ── guardarSeguimiento ───────────────────────────────────────────

    @Test
    void guardarSeguimiento_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarSeguimiento(1L, "x@mail.com", SeguimientoDiario.builder().build()));
    }

    @Test
    void guardarSeguimiento_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarSeguimiento(5L, "dra@mail.com", SeguimientoDiario.builder().build()));
    }

    @Test
    void guardarSeguimiento_shouldDefaultFechaToTodayAndNotify() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SeguimientoDiario entry = SeguimientoDiario.builder().estadoAnimo("Bueno").build();
        SeguimientoDiario saved = clinicalService.guardarSeguimiento(5L, "dra@mail.com", entry);

        assertEquals(LocalDate.now(), saved.getFecha());
        assertEquals(medico, saved.getMedico());
        assertEquals(paciente, saved.getPaciente());
        verify(notificacionService).crearNotificacion(eq(medico), anyString(), anyString(), eq("SEGUIMIENTO"));
    }

    @Test
    void guardarSeguimiento_shouldKeepExplicitFecha() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate fechaExplicita = LocalDate.of(2020, 1, 1);
        SeguimientoDiario entry = SeguimientoDiario.builder().fecha(fechaExplicita).build();
        SeguimientoDiario saved = clinicalService.guardarSeguimiento(5L, "dra@mail.com", entry);

        assertEquals(fechaExplicita, saved.getFecha());
    }

    // ── guardarInforme ───────────────────────────────────────────────

    @Test
    void guardarInforme_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarInforme("x@mail.com", 1L, "GENERAL", "plan", "contenido", "archivo.pdf"));
    }

    @Test
    void guardarInforme_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarInforme("dra@mail.com", 5L, "GENERAL", "plan", "contenido", "archivo.pdf"));
    }

    @Test
    void guardarInforme_shouldSaveAndNotifyBothMedicoAndPaciente() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(informeClinicoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InformeClinico saved = clinicalService.guardarInforme("dra@mail.com", 5L, "GENERAL", "plan", "contenido", "archivo.pdf");

        assertEquals("GENERAL", saved.getTipoInforme());
        assertEquals(medico, saved.getMedico());
        assertEquals(paciente, saved.getPaciente());
        assertEquals(LocalDate.now(), saved.getFecha());
        verify(notificacionService).crearNotificacion(eq(medico), anyString(), anyString(), eq("INFORME"));
        verify(notificacionService).crearNotificacion(eq(paciente), anyString(), anyString(), eq("INFORME"));
    }

    // ── obtenerSeguimientosPaciente / obtenerInformesPaciente ───────

    @Test
    void obtenerSeguimientosPaciente_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.obtenerSeguimientosPaciente("x@mail.com"));
    }

    @Test
    void obtenerSeguimientosPaciente_shouldReturnRepositoryResult() {
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        List<SeguimientoDiario> expected = List.of(SeguimientoDiario.builder().id(1L).build());
        when(seguimientoDiarioRepository.findByPacienteIdOrderByFechaDesc(5L)).thenReturn(expected);

        assertEquals(expected, clinicalService.obtenerSeguimientosPaciente("ana@mail.com"));
    }

    @Test
    void obtenerInformesPaciente_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.obtenerInformesPaciente("x@mail.com"));
    }

    @Test
    void obtenerInformesPaciente_shouldReturnRepositoryResult() {
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        List<InformeClinico> expected = List.of(InformeClinico.builder().id(1L).build());
        when(informeClinicoRepository.findByPacienteIdOrderByFechaDesc(5L)).thenReturn(expected);

        assertEquals(expected, clinicalService.obtenerInformesPaciente("ana@mail.com"));
    }

    // ── guardarSeguimientoPaciente ───────────────────────────────────

    @Test
    void guardarSeguimientoPaciente_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarSeguimientoPaciente("x@mail.com", SeguimientoDiario.builder().build()));
    }

    @Test
    void guardarSeguimientoPaciente_shouldFallBackToDefaultMedicoWhenMissing() {
        Usuario paciente = paciente(5L, "Ana");
        Usuario medicoDefault = medico("paula@tranqui.com");
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findByEmail("paula@tranqui.com")).thenReturn(Optional.of(medicoDefault));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SeguimientoDiario entry = SeguimientoDiario.builder().build();
        SeguimientoDiario saved = clinicalService.guardarSeguimientoPaciente("ana@mail.com", entry);

        assertEquals(medicoDefault, saved.getMedico());
        assertEquals(paciente, saved.getPaciente());
        assertEquals(LocalDate.now(), saved.getFecha());
        verify(notificacionService).crearNotificacion(eq(medicoDefault), anyString(), anyString(), eq("SEGUIMIENTO"));
    }

    @Test
    void guardarSeguimientoPaciente_shouldThrowWhenDefaultMedicoMissing() {
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findByEmail("paula@tranqui.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.guardarSeguimientoPaciente("ana@mail.com", SeguimientoDiario.builder().build()));
    }

    @Test
    void guardarSeguimientoPaciente_shouldKeepExplicitMedicoAndFecha() {
        Usuario paciente = paciente(5L, "Ana");
        Usuario medicoExplicito = medico("otra@mail.com");
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(paciente));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate fecha = LocalDate.of(2021, 6, 1);
        SeguimientoDiario entry = SeguimientoDiario.builder().medico(medicoExplicito).fecha(fecha).build();
        SeguimientoDiario saved = clinicalService.guardarSeguimientoPaciente("ana@mail.com", entry);

        assertEquals(medicoExplicito, saved.getMedico());
        assertEquals(fecha, saved.getFecha());
        verify(usuarioRepository, never()).findByEmail("paula@tranqui.com");
    }

    // ── actualizarPaciente ───────────────────────────────────────────

    @Test
    void actualizarPaciente_shouldThrowWhenMedicoNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.actualizarPaciente(1L, "x@mail.com", PacienteDto.builder().build()));
    }

    @Test
    void actualizarPaciente_shouldThrowWhenPacienteNotFound() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.actualizarPaciente(5L, "dra@mail.com", PacienteDto.builder().build()));
    }

    @Test
    void actualizarPaciente_shouldUpdateFieldsAndParseFechaNacimiento() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PacienteDto dto = PacienteDto.builder()
                .nombre("Ana Actualizada")
                .apellido("Gomez")
                .fechaNacimiento("1990-05-20")
                .obraSocial("Particular")
                .build();

        PacienteDto result = clinicalService.actualizarPaciente(5L, "dra@mail.com", dto);

        assertEquals("Ana Actualizada", result.getNombre());
        assertEquals(LocalDate.of(1990, 5, 20), paciente.getFechaNacimiento());
        assertEquals("Hoy", result.getUltimaVisita());
        assertFalse(result.isSinTurno());
    }

    @Test
    void actualizarPaciente_shouldIgnoreBlankFechaNacimiento() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PacienteDto dto = PacienteDto.builder().nombre("Ana").fechaNacimiento("   ").build();
        clinicalService.actualizarPaciente(5L, "dra@mail.com", dto);

        assertNull(paciente.getFechaNacimiento());
    }

    @Test
    void actualizarPaciente_shouldSetObraSocialFromCredencialPlanWhenPresent() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PacienteDto.CredencialInfoDto cred = PacienteDto.CredencialInfoDto.builder()
                .codEntidad(1).pan("PAN123456").plan("210").token("TOK").build();
        // actualizarPaciente no resuelve codEntidad -> nombre de obra social por sí solo: usa
        // dto.getObraSocial() (lo que el frontend ya resolvió contra el catálogo de financiadores)
        // como base y le concatena el plan de la credencial. Sin obraSocial en el DTO, cae al
        // literal genérico "Obra Social" (cubierto por el fallback de ese caso más abajo).
        PacienteDto dto = PacienteDto.builder().nombre("Ana").obraSocial("OSDE").credencial(cred).build();

        clinicalService.actualizarPaciente(5L, "dra@mail.com", dto);

        assertEquals("OSDE 210", paciente.getObraSocial());
        assertEquals(1, paciente.getCredencialCodEntidad());
    }

    @Test
    void actualizarPaciente_shouldFallBackToDtoObraSocialWhenCredencialPlanBlank() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PacienteDto.CredencialInfoDto cred = PacienteDto.CredencialInfoDto.builder()
                .codEntidad(1).pan("PAN123456").plan("  ").build();
        PacienteDto dto = PacienteDto.builder().nombre("Ana").obraSocial("Swiss Medical").credencial(cred).build();

        clinicalService.actualizarPaciente(5L, "dra@mail.com", dto);

        assertEquals("Swiss Medical", paciente.getObraSocial());
    }

    @Test
    void actualizarPaciente_shouldClearCredencialWhenNotProvided() {
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico("dra@mail.com")));
        Usuario paciente = paciente(5L, "Ana");
        paciente.setCredencialCodEntidad(9);
        paciente.setObraSocial("Algo");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PacienteDto dto = PacienteDto.builder().nombre("Ana").build();
        clinicalService.actualizarPaciente(5L, "dra@mail.com", dto);

        assertNull(paciente.getCredencialCodEntidad());
        assertNull(paciente.getObraSocial());
    }

    // ── eliminarInforme (soft-delete, nunca hard delete) ─────────────

    @Test
    void eliminarInforme_shouldThrowWhenNotFound() {
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.eliminarInforme(1L, "dra@mail.com", "motivo"));
    }

    @Test
    void eliminarInforme_shouldThrowWhenNotOwner() {
        InformeClinico informe = InformeClinico.builder().id(1L).medico(medico("otra@mail.com")).build();
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.of(informe));

        assertThrows(AccessDeniedException.class, () -> clinicalService.eliminarInforme(1L, "dra@mail.com", "motivo"));
        verify(informeClinicoRepository, never()).delete(any());
        verify(informeClinicoRepository, never()).save(any());
    }

    @Test
    void eliminarInforme_shouldSoftDeleteWithMotivoWhenOwner() {
        InformeClinico informe = InformeClinico.builder().id(1L).medico(medico("dra@mail.com")).build();
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.of(informe));
        when(informeClinicoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        clinicalService.eliminarInforme(1L, "dra@mail.com", "cargado por error");

        assertEquals("ANULADO", informe.getEstado());
        assertEquals("cargado por error", informe.getMotivo());
        verify(informeClinicoRepository, never()).delete(any());
        verify(informeClinicoRepository).save(informe);
    }

    // ── editarInforme (corrección por anexo, no edición en el lugar) ─

    @Test
    void editarInforme_shouldThrowWhenNotFound() {
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> clinicalService.editarInforme(1L, "dra@mail.com", "GENERAL", "plan", "contenido", "motivo"));
    }

    @Test
    void editarInforme_shouldThrowWhenNotOwner() {
        InformeClinico informe = InformeClinico.builder().id(1L).medico(medico("otra@mail.com")).build();
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.of(informe));

        assertThrows(AccessDeniedException.class,
                () -> clinicalService.editarInforme(1L, "dra@mail.com", "GENERAL", "plan", "contenido", "motivo"));
    }

    @Test
    void editarInforme_shouldThrowWhenOriginalAlreadyAnulado() {
        InformeClinico informe = InformeClinico.builder().id(1L).medico(medico("dra@mail.com")).estado("ANULADO").build();
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.of(informe));

        assertThrows(IllegalStateException.class,
                () -> clinicalService.editarInforme(1L, "dra@mail.com", "GENERAL", "plan", "contenido", "motivo"));
    }

    @Test
    void editarInforme_shouldCreateAnexoAndKeepOriginalIntact() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        InformeClinico original = InformeClinico.builder().id(1L).medico(medico).paciente(paciente).tipoInforme("VIEJO").build();
        when(informeClinicoRepository.findById(1L)).thenReturn(Optional.of(original));
        when(informeClinicoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InformeClinico anexo = clinicalService.editarInforme(1L, "dra@mail.com", "NUEVO", "plan-x", "contenido-x", "corrección de diagnóstico");

        // El original no pierde su contenido — solo cambia de estado.
        assertEquals("VIEJO", original.getTipoInforme());
        assertEquals("VIGENTE_CORREGIDO", original.getEstado());

        // El anexo es una fila nueva, enlazada al original, con el contenido corregido.
        assertEquals("NUEVO", anexo.getTipoInforme());
        assertEquals("plan-x", anexo.getPlanTrabajo());
        assertEquals("contenido-x", anexo.getContenido());
        assertEquals("ANEXO_CORRECCION", anexo.getEstado());
        assertEquals(1L, anexo.getInformeOriginalId());
        assertEquals("corrección de diagnóstico", anexo.getMotivo());
        assertNotNull(anexo.getHashIntegridad());
        verify(informeClinicoRepository, times(2)).save(any());
    }

    // ── eliminarSeguimiento (soft-delete, nunca hard delete) ─────────

    @Test
    void eliminarSeguimiento_shouldThrowWhenNotFound() {
        when(seguimientoDiarioRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> clinicalService.eliminarSeguimiento(1L, "dra@mail.com", "motivo"));
    }

    @Test
    void eliminarSeguimiento_shouldThrowWhenNotOwner() {
        SeguimientoDiario seguimiento = SeguimientoDiario.builder().id(1L).medico(medico("otra@mail.com")).build();
        when(seguimientoDiarioRepository.findById(1L)).thenReturn(Optional.of(seguimiento));

        assertThrows(AccessDeniedException.class, () -> clinicalService.eliminarSeguimiento(1L, "dra@mail.com", "motivo"));
        verify(seguimientoDiarioRepository, never()).delete(any());
        verify(seguimientoDiarioRepository, never()).save(any());
    }

    @Test
    void eliminarSeguimiento_shouldSoftDeleteWithMotivoWhenOwner() {
        SeguimientoDiario seguimiento = SeguimientoDiario.builder().id(1L).medico(medico("dra@mail.com")).build();
        when(seguimientoDiarioRepository.findById(1L)).thenReturn(Optional.of(seguimiento));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        clinicalService.eliminarSeguimiento(1L, "dra@mail.com", "duplicado");

        assertEquals("ANULADO", seguimiento.getEstado());
        assertEquals("duplicado", seguimiento.getMotivo());
        verify(seguimientoDiarioRepository, never()).delete(any());
        verify(seguimientoDiarioRepository).save(seguimiento);
    }

    // ── hash de integridad al crear ───────────────────────────────────

    @Test
    void guardarInforme_shouldSetEstadoVigenteAndComputeHash() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(informeClinicoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InformeClinico saved = clinicalService.guardarInforme("dra@mail.com", 5L, "GENERAL", "plan", "contenido", "archivo.pdf");

        assertEquals("VIGENTE", saved.getEstado());
        assertNotNull(saved.getHashIntegridad());
        assertEquals(64, saved.getHashIntegridad().length()); // SHA-256 en hex
    }

    @Test
    void guardarSeguimiento_shouldSetEstadoVigenteAndComputeHash() {
        Usuario medico = medico("dra@mail.com");
        Usuario paciente = paciente(5L, "Ana");
        when(usuarioRepository.findByEmail("dra@mail.com")).thenReturn(Optional.of(medico));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(paciente));
        when(seguimientoDiarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SeguimientoDiario entry = SeguimientoDiario.builder().estadoAnimo("Bueno").build();
        SeguimientoDiario saved = clinicalService.guardarSeguimiento(5L, "dra@mail.com", entry);

        assertEquals("VIGENTE", saved.getEstado());
        assertNotNull(saved.getHashIntegridad());
        assertEquals(64, saved.getHashIntegridad().length());
    }
}
