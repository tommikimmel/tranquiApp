package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    private Usuario paciente;
    private Usuario medico;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Paciente Pepito")
                .email("pepito@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        medico = Usuario.builder()
                .nombre("Dr. Carlos")
                .email("carlos@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);
    }

    @AfterEach
    void tearDown() {
        mensajeRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "carlos@gmail.com")
    void whenGetCanales_shouldReturnList() throws Exception {
        // Create at least one message so that a channel is active
        Mensaje mensaje = Mensaje.builder()
                .remitente(paciente)
                .destinatario(medico)
                .contenido("Hola doctor")
                .fechaEnvio(LocalDateTime.now())
                .build();
        mensajeRepository.save(mensaje);

        mockMvc.perform(get("/api/chat/canales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(paciente.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Paciente Pepito"))
                .andExpect(jsonPath("$[0].prioridadClinica").value("PRIORIDAD_BAJA"));
    }

    @Test
    @WithMockUser(username = "pepito@gmail.com")
    void whenGetHistorial_shouldReturnPageableMessages() throws Exception {
        Mensaje msg1 = Mensaje.builder()
                .remitente(paciente)
                .destinatario(medico)
                .contenido("Pregunta 1")
                .fechaEnvio(LocalDateTime.now().minusMinutes(5))
                .build();
        Mensaje msg2 = Mensaje.builder()
                .remitente(medico)
                .destinatario(paciente)
                .contenido("Respuesta 1")
                .fechaEnvio(LocalDateTime.now().minusMinutes(2))
                .build();

        mensajeRepository.save(msg1);
        mensajeRepository.save(msg2);

        mockMvc.perform(get("/api/chat/historial/" + medico.getId())
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].contenido").value("Respuesta 1"))
                .andExpect(jsonPath("$.content[1].contenido").value("Pregunta 1"));
    }
}
