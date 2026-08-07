package com.tranqui.app.controller;

import com.tranqui.app.model.InformeClinico;
import com.tranqui.app.service.ClinicalService;
import com.tranqui.app.service.NotificacionService;
import com.tranqui.app.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicalService clinicalService;

    @MockBean
    private NotificacionService notificacionService;

    @MockBean
    private TurnoService turnoService;

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testEditarInforme() throws Exception {
        InformeClinico mockInforme = InformeClinico.builder()
                .id(1L)
                .tipoInforme("Evaluativo")
                .planTrabajo("Nuevo plan")
                .contenido("Nuevo contenido")
                .build();

        when(clinicalService.editarInforme(eq(1L), eq("medico@test.com"), eq("Evaluativo"), eq("Nuevo plan"), eq("Nuevo contenido"), isNull()))
                .thenReturn(mockInforme);

        mockMvc.perform(put("/api/pacientes/2/informes/1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"tipoInforme\":\"Evaluativo\",\"planTrabajo\":\"Nuevo plan\",\"contenido\":\"Nuevo contenido\"}"))
                .andExpect(status().isOk());
    }
}
