package com.tranqui.app.config;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.Cookie;

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
}
