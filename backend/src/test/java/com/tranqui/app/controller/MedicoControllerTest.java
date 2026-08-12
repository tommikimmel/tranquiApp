package com.tranqui.app.controller;

import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.DashboardStatsDto;
import com.tranqui.app.model.dto.DisponibilidadDto;
import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.DisponibilidadService;
import com.tranqui.app.service.MedicoService;
import com.tranqui.app.service.MercadoPagoOAuthService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private MercadoPagoOAuthService mercadoPagoOAuthService;

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
        when(medicoService.obtenerStats("medico@test.com", "MENSUAL")).thenReturn(stats);

        mockMvc.perform(get("/api/medicos/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsToday").value(5));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerDisponibilidad() throws Exception {
        when(disponibilidadService.obtenerDisponibilidades(eq("medico@test.com"), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/medicos/disponibilidad").param("modalidad", "ONLINE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testActualizarDisponibilidad() throws Exception {
        when(disponibilidadService.guardarDisponibilidades(eq("medico@test.com"), eq(Modalidad.ONLINE), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/medicos/disponibilidad")
                        .param("modalidad", "ONLINE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerEstadoMercadoPago_connected() throws Exception {
        Usuario medico = Usuario.builder().id(1L).email("medico@test.com").rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("enc").mpUserId("999").build();
        when(usuarioRepository.findByEmail("medico@test.com")).thenReturn(Optional.of(medico));

        mockMvc.perform(get("/api/medicos/mercadopago/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.mpUserId").value("999"));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerEstadoMercadoPago_notConnected() throws Exception {
        Usuario medico = Usuario.builder().id(1L).email("medico@test.com").rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findByEmail("medico@test.com")).thenReturn(Optional.of(medico));

        mockMvc.perform(get("/api/medicos/mercadopago/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.mpUserId").value(""));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testObtenerUrlConexionMercadoPago() throws Exception {
        Usuario medico = Usuario.builder().id(1L).email("medico@test.com").rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findByEmail("medico@test.com")).thenReturn(Optional.of(medico));
        when(mercadoPagoOAuthService.buildAuthorizationUrl(medico)).thenReturn("https://auth.mercadopago.com.ar/authorization?x=1");

        mockMvc.perform(get("/api/medicos/mercadopago/connect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://auth.mercadopago.com.ar/authorization?x=1"));
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testSimularConexionMercadoPago_devMode() throws Exception {
        Usuario medico = Usuario.builder().id(1L).email("medico@test.com").rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findByEmail("medico@test.com")).thenReturn(Optional.of(medico));

        // mercadopago.enabled defaults to false in the test profile, so this must succeed.
        mockMvc.perform(post("/api/medicos/mercadopago/connect-simulado"))
                .andExpect(status().isOk());

        verify(mercadoPagoOAuthService).simularConexionDev(medico);
    }

    @Test
    @WithMockUser(username = "medico@test.com", roles = "PSIQUIATRA")
    void testDesvincularMercadoPago() throws Exception {
        Usuario medico = Usuario.builder().id(1L).email("medico@test.com").rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findByEmail("medico@test.com")).thenReturn(Optional.of(medico));

        mockMvc.perform(post("/api/medicos/mercadopago/disconnect"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCallbackMercadoPago_success() throws Exception {
        when(mercadoPagoOAuthService.verificarState("valid-state")).thenReturn(7L);

        mockMvc.perform(get("/api/medicos/mercadopago/callback").param("code", "abc").param("state", "valid-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/?mp=success")));
    }

    @Test
    void testCallbackMercadoPago_errorParam() throws Exception {
        mockMvc.perform(get("/api/medicos/mercadopago/callback").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("mp=error&reason=access_denied")));
    }

    @Test
    void testCallbackMercadoPago_invalidState() throws Exception {
        when(mercadoPagoOAuthService.verificarState("bad-state")).thenReturn(null);

        mockMvc.perform(get("/api/medicos/mercadopago/callback").param("code", "abc").param("state", "bad-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("mp=error&reason=invalid_state")));
    }

    @Test
    void testCallbackMercadoPago_exchangeFails() throws Exception {
        when(mercadoPagoOAuthService.verificarState("valid-state")).thenReturn(7L);
        when(mercadoPagoOAuthService.procesarCallback(7L, "abc")).thenThrow(new RuntimeException("mp down"));

        mockMvc.perform(get("/api/medicos/mercadopago/callback").param("code", "abc").param("state", "valid-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("mp=error&reason=exchange_failed")));
    }
}
