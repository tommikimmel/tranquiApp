package com.tranqui.app.controller;

import com.tranqui.app.model.dto.DashboardStatsDto;
import com.tranqui.app.model.dto.DisponibilidadDto;
import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.service.DisponibilidadService;
import com.tranqui.app.service.MedicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MedicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MedicoService medicoService;

    @MockBean
    private DisponibilidadService disponibilidadService;

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void testObtenerMedicos() throws Exception {
        when(medicoService.obtenerMedicosActivos()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerPerfil() throws Exception {
        MedicoDto dto = MedicoDto.builder().email("medico@test.com").name("Medico Test").build();
        when(medicoService.obtenerPerfil("medico@test.com")).thenReturn(dto);

        mockMvc.perform(get("/api/medicos/perfil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("medico@test.com"));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testActualizarPerfil() throws Exception {
        MedicoDto dto = MedicoDto.builder().email("medico@test.com").name("Medico Test").build();
        when(medicoService.actualizarPerfil(eq("medico@test.com"), any(MedicoDto.class))).thenReturn(dto);

        mockMvc.perform(put("/api/medicos/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Medico Test\",\"email\":\"medico@test.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerStats() throws Exception {
        DashboardStatsDto stats = DashboardStatsDto.builder().sessionsToday(5).build();
        when(medicoService.obtenerStats("medico@test.com")).thenReturn(stats);

        mockMvc.perform(get("/api/medicos/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsToday").value(5));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerDisponibilidad() throws Exception {
        when(disponibilidadService.obtenerDisponibilidades("medico@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos/disponibilidad"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testActualizarDisponibilidad() throws Exception {
        when(disponibilidadService.guardarDisponibilidades(eq("medico@test.com"), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/medicos/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());
    }
}
