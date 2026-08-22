package com.tranqui.app.controller;

import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TurnoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TurnoService turnoService;

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testObtenerTurnosDisponibles() throws Exception {
        when(turnoService.obtenerHorariosDisponibles(eq(1L), any(LocalDate.class), eq(Modalidad.ONLINE))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos/1/turnos-disponibles")
                        .param("fecha", "2026-07-02")
                        .param("modalidad", "ONLINE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testReservarTurno() throws Exception {
        TurnoResponseDto res = TurnoResponseDto.builder().turnoId(1L).estado("PENDIENTE_PAGO").build();
        when(turnoService.reservarTurno(any(ReservaTurnoDto.class))).thenReturn(res);

        mockMvc.perform(post("/api/turnos/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicoId\":1,\"emailPaciente\":\"pac@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnoId").value(1));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerTurnosDeHoy() throws Exception {
        when(turnoService.obtenerTurnosDeHoy("medico@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos/turnos/hoy"))
                .andExpect(status().isOk());
    }

    // --- cancelar --------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testCancelarTurno_success() throws Exception {
        doNothing().when(turnoService).cancelarTurno(eq(1L), eq("paciente@test.com"));

        mockMvc.perform(post("/api/turnos/1/cancelar"))
                .andExpect(status().isOk());

        verify(turnoService, times(1)).cancelarTurno(eq(1L), eq("paciente@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testCancelarTurno_wrongRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/turnos/1/cancelar"))
                .andExpect(status().isForbidden());
    }

    // --- abandonar-pago (public, no role restriction) ---------------------------------------

    @Test
    void testAbandonarReservaPendiente_success() throws Exception {
        doNothing().when(turnoService).abandonarReservaPendiente(1L);

        mockMvc.perform(post("/api/turnos/1/abandonar-pago"))
                .andExpect(status().isOk());

        verify(turnoService, times(1)).abandonarReservaPendiente(1L);
    }

    // --- asistencia --------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testActualizarAsistencia_success() throws Exception {
        doNothing().when(turnoService).actualizarAsistencia(1L, "LLEGO");

        mockMvc.perform(put("/api/turnos/1/asistencia").param("asistencia", "LLEGO"))
                .andExpect(status().isOk());

        verify(turnoService, times(1)).actualizarAsistencia(1L, "LLEGO");
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testActualizarAsistencia_wrongRole_forbidden() throws Exception {
        mockMvc.perform(put("/api/turnos/1/asistencia").param("asistencia", "LLEGO"))
                .andExpect(status().isForbidden());
    }

    // --- documento-enviado -------------------------------------------------------------------

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testMarcarDocumentoEnviado_success() throws Exception {
        doNothing().when(turnoService).marcarDocumentoEnviado(eq(1L), eq("medico@test.com"), any(), any());

        mockMvc.perform(post("/api/turnos/1/documento-enviado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archivoData\":\"data:application/pdf;base64,aGVsbG8=\",\"archivoNombre\":\"x.pdf\"}"))
                .andExpect(status().isOk());

        verify(turnoService, times(1)).marcarDocumentoEnviado(eq(1L), eq("medico@test.com"), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testMarcarDocumentoEnviado_wrongRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/turnos/1/documento-enviado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // --- reprogramar -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testReprogramarTurno_success() throws Exception {
        doNothing().when(turnoService).reprogramarTurno(1L, "2026-09-01", "10:00");

        mockMvc.perform(put("/api/turnos/1/reprogramar")
                        .param("fecha", "2026-09-01")
                        .param("hora", "10:00"))
                .andExpect(status().isOk());

        verify(turnoService, times(1)).reprogramarTurno(1L, "2026-09-01", "10:00");
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testReprogramarTurno_wrongRole_forbidden() throws Exception {
        mockMvc.perform(put("/api/turnos/1/reprogramar")
                        .param("fecha", "2026-09-01")
                        .param("hora", "10:00"))
                .andExpect(status().isForbidden());
    }
}
