package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.model.dto.RecetaResponseDto;
import com.tranqui.app.service.Qbi2RecipeClient;
import com.tranqui.app.service.RecetaElectronicaException;
import com.tranqui.app.service.RecetaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RecetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecetaService recetaService;

    @MockBean
    private Qbi2RecipeClient qbi2RecipeClient;

    private RecetaDto.MedicamentoDto medicamentoDto() {
        RecetaDto.MedicamentoDto m = new RecetaDto.MedicamentoDto();
        m.setName("Ibuprofeno");
        m.setDosage("400mg");
        m.setFrequency("Cada 8hs");
        m.setDuration("3 dias");
        return m;
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void enviarReceta_comoMedico_devuelve200() throws Exception {
        RecetaDto dto = RecetaDto.builder()
                .pacienteId(1L)
                .diagnosis("Dolor")
                .medications(List.of(medicamentoDto()))
                .build();
        RecetaResponseDto response = RecetaResponseDto.builder().id(10L).build();
        when(recetaService.emitirReceta(anyString(), any(RecetaDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/recetas/enviar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto))
)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void enviarReceta_comoPaciente_devuelve403() throws Exception {
        RecetaDto dto = RecetaDto.builder().pacienteId(1L).medications(List.of(medicamentoDto())).build();

        mockMvc.perform(post("/api/recetas/enviar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto))
)
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void enviarReceta_datosIncompletos_devuelve422ConMensaje() throws Exception {
        RecetaDto dto = RecetaDto.builder().pacienteId(1L).medications(List.of(medicamentoDto())).build();
        when(recetaService.emitirReceta(anyString(), any(RecetaDto.class)))
                .thenThrow(new RecetaElectronicaException("falta completar el DNI del paciente", null));

        mockMvc.perform(post("/api/recetas/enviar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto))
)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DNI del paciente")));
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void obtenerMisRecetas_comoPaciente_devuelve200() throws Exception {
        when(recetaService.obtenerMisRecetas("paciente@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/recetas/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void buscarDiagnosticos_comoMedico_devuelve200() throws Exception {
        Qbi2CatalogoDtos.DiagnosticoResponse resp = Qbi2CatalogoDtos.DiagnosticoResponse.builder()
                .diagnosticos(Collections.emptyList())
                .build();
        when(qbi2RecipeClient.buscarDiagnosticos(anyString())).thenReturn(resp);

        mockMvc.perform(get("/api/recetas/diagnosticos").param("texto", "ansiedad"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void buscarDiagnosticos_comoPaciente_devuelve403() throws Exception {
        mockMvc.perform(get("/api/recetas/diagnosticos").param("texto", "ansiedad"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void buscarMedicamentos_comoMedico_devuelve200() throws Exception {
        Qbi2CatalogoDtos.MedicamentoResponse resp = Qbi2CatalogoDtos.MedicamentoResponse.builder()
                .medicamentos(Collections.emptyList())
                .build();
        when(qbi2RecipeClient.buscarMedicamentos(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(resp);

        mockMvc.perform(get("/api/recetas/medicamentos").param("texto", "ibuprofeno").param("pagina", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void buscarFinanciadores_sinAutenticar_devuelve200() throws Exception {
        Qbi2CatalogoDtos.FinanciadorResponse resp = Qbi2CatalogoDtos.FinanciadorResponse.builder()
                .financiadores(Collections.emptyList())
                .build();
        when(qbi2RecipeClient.buscarFinanciadores()).thenReturn(resp);

        mockMvc.perform(get("/api/recetas/financiadores"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "paciente@test.com", roles = "PACIENTE")
    void obtenerRecetaPorId_comoPaciente_devuelve200() throws Exception {
        RecetaResponseDto response = RecetaResponseDto.builder().id(5L).build();
        when(recetaService.obtenerRecetaPorId(5L, "paciente@test.com")).thenReturn(response);

        mockMvc.perform(get("/api/recetas/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void obtenerMisRecetas_sinAutenticar_devuelve401o403() throws Exception {
        mockMvc.perform(get("/api/recetas/me"))
                .andExpect(status().is4xxClientError());
    }
}
