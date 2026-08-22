package com.tranqui.app.config;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.JwtService;
import com.tranqui.app.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityRbacTest.TestRbacController.class)
@org.springframework.transaction.annotation.Transactional
class SecurityRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // SubscriptionAccessFilter runs after JwtAuthenticationFilter and returns 403
    // SUBSCRIPTION_REQUIRED for any PSIQUIATRA without an ACTIVE subscription persisted in the
    // DB. These RBAC tests care about role-based access to endpoints, not the subscriptions
    // paywall, so it's stubbed open here — see SubscriptionAccessFilter/SubscriptionService.
    @MockBean
    private SubscriptionService subscriptionService;

    @BeforeEach
    void allowSubscriptionAccessByDefault() {
        when(subscriptionService.isAccessAllowed(anyLong())).thenReturn(true);
    }

    @RestController
    static class TestRbacController {
        @GetMapping("/api/admin/medicos")
        @PreAuthorize("hasRole('ADMIN')")
        public String getMedicos() {
            return "medicos";
        }

        @GetMapping("/api/medico/agenda")
        @PreAuthorize("hasRole('PSIQUIATRA')")
        public String getAgenda() {
            return "agenda";
        }
    }

    private Usuario paciente;
    private Usuario psiquiatra;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Juan Paciente")
                .email("paciente@gmail.com")
                .rol(Rol.PACIENTE)
                .build();

        psiquiatra = Usuario.builder()
                .nombre("Dr. Carlos Psiquiatra")
                .email("psiquiatra@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(psiquiatra);
    }



    @Test
    void whenAccessAdminEndpointAsPaciente_thenForbidden() throws Exception {
        String token = jwtService.generateToken(paciente);

        mockMvc.perform(get("/api/admin/medicos")
                .cookie(new Cookie("SESSION-TOKEN", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAccessMedicoEndpointAsPsiquiatra_thenOk() throws Exception {
        String token = jwtService.generateToken(psiquiatra);

        mockMvc.perform(get("/api/medico/agenda")
                .cookie(new Cookie("SESSION-TOKEN", token)))
                .andExpect(status().isOk());
    }

    @Test
    void whenAccessAdminEndpointWithoutToken_thenForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/medicos"))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAccessPsiquiatraEndpointAsPaciente_thenForbidden() throws Exception {
        String token = jwtService.generateToken(paciente);

        mockMvc.perform(get("/api/medico/agenda")
                        .cookie(new Cookie("SESSION-TOKEN", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAccessMedicoEndpointWithExpiredToken_thenForbidden() throws Exception {
        // Force an already-expired token by making JwtService issue it with a negative TTL,
        // then restore the real expiration so it doesn't leak into other tests.
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String expiredToken = jwtService.generateToken(psiquiatra);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 604800000L);

        mockMvc.perform(get("/api/medico/agenda")
                        .cookie(new Cookie("SESSION-TOKEN", expiredToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAccessMedicoEndpointWithTamperedToken_thenForbidden() throws Exception {
        String token = jwtService.generateToken(psiquiatra);
        // Flip the last couple of characters of the signature so parseClaimsJws rejects it.
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/medico/agenda")
                        .cookie(new Cookie("SESSION-TOKEN", tampered)))
                .andExpect(status().isForbidden());
    }
}
