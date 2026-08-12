package com.tranqui.app.controller;

import com.tranqui.app.config.SiteAccessFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SiteAccessFilter sits in the actual servlet filter chain (SecurityConfig#addFilterBefore), not
// just as a controller dependency — @MockBean-ing it would replace the real filter everywhere,
// including in the chain that runs before every request, and a mock's inherited doFilter() does
// nothing by default (never calls chain.doFilter()), which would hang every request in this test
// class. Enabling the real gate for just this test class via @TestPropertySource instead exercises
// the actual filter + controller together, which is both safer and more meaningful.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "site.access.password=test-pass-123")
class SiteAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SiteAccessFilter siteAccessFilter;

    @Test
    void estado_sinCookie_noAutorizado() throws Exception {
        mockMvc.perform(get("/api/site-access/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(false));
    }

    @Test
    void estado_conCookieValida_autorizado() throws Exception {
        mockMvc.perform(get("/api/site-access/estado")
                        .cookie(new jakarta.servlet.http.Cookie(SiteAccessFilter.COOKIE_NAME, siteAccessFilter.expectedCookieValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(true));
    }

    @Test
    void estado_conCookieIncorrecta_noAutorizado() throws Exception {
        mockMvc.perform(get("/api/site-access/estado")
                        .cookie(new jakarta.servlet.http.Cookie(SiteAccessFilter.COOKIE_NAME, "valor-incorrecto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(false));
    }

    @Test
    void verificar_passwordIncorrecta_devuelve401() throws Exception {
        mockMvc.perform(post("/api/site-access/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void verificar_passwordCorrecta_devuelveAutorizadoYCookie() throws Exception {
        mockMvc.perform(post("/api/site-access/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(true))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void otrasRutasApi_sinCookie_bloqueadasPorElGate() throws Exception {
        mockMvc.perform(get("/api/medicos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthCheck_bypassaElGateSiempre() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
