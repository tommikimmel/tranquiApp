package com.tranqui.app.controller;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ResendEmailService.enviarQueja is called for real here (not mocked) — safe because no
// RESEND_API_KEY is configured in the test profile, so it logs and returns instead of hitting
// the real Resend API (see ResendEmailServiceTest).
@SpringBootTest
@AutoConfigureMockMvc
class SoporteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario paciente;
    private Usuario medico;

    @BeforeEach
    void setUp() {
        paciente = usuarioRepository.save(Usuario.builder()
                .nombre("Pedro")
                .apellido("Gómez")
                .email("pedro.soporte." + System.nanoTime() + "@test.com")
                .rol(Rol.PACIENTE)
                .build());
        medico = usuarioRepository.save(Usuario.builder()
                .nombre("Marta")
                .apellido("Rossi")
                .email("marta.soporte." + System.nanoTime() + "@test.com")
                .rol(Rol.PSIQUIATRA)
                .build());
    }

    @AfterEach
    void tearDown() {
        usuarioRepository.delete(paciente);
        usuarioRepository.delete(medico);
    }

    @Test
    void enviarQueja_comoPaciente_devuelve200() throws Exception {
        mockMvc.perform(post("/api/soporte/queja")
                        .with(user(paciente.getEmail()).roles("PACIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Problema con un pago\",\"mensaje\":\"El pago no se acreditó.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void enviarQueja_comoMedico_devuelve200() throws Exception {
        mockMvc.perform(post("/api/soporte/queja")
                        .with(user(medico.getEmail()).roles("PSIQUIATRA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"\",\"mensaje\":\"No puedo emitir una receta.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void enviarQueja_mensajeVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/api/soporte/queja")
                        .with(user(paciente.getEmail()).roles("PACIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asunto\":\"Algo\",\"mensaje\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enviarQueja_sinAutenticar_devuelve4xx() throws Exception {
        mockMvc.perform(post("/api/soporte/queja")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().is4xxClientError());
    }
}
