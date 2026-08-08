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
import static org.mockito.ArgumentMatchers.eq;
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
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testIsFirstConsultation() throws Exception {
        when(turnoService.esPrimeraConsulta("pac@test.com")).thenReturn(true);

        mockMvc.perform(get("/api/turnos/check-first-consultation")
                        .param("email", "pac@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerTurnosDeHoy() throws Exception {
        when(turnoService.obtenerTurnosDeHoy("medico@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos/turnos/hoy"))
                .andExpect(status().isOk());
    }
}
