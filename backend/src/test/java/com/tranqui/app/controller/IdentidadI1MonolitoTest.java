package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RegisterRequestDto;
import com.tranqui.app.model.dto.UsuarioAdminDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ResendEmailService;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.PasswordTemporalGeneradaData;
import com.tranqui.eventos.data.SolicitudDatosData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "google.simulado=false",
        "notificaciones.envio-directo=false",
        "site.access.password="
})
class IdentidadI1MonolitoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ObjectMapper objectMapper;

    @SpyBean private OutboxPublisher outboxPublisher;
    @SpyBean private ResendEmailService resendEmailService;

    @Test
    void loginGoogleFalso_conGoogleSimuladoFalse_esRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"mock-admin@tranqui.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioAdminDto_noContieneNingunaClaveProhibidaEnJson() throws Exception {
        Usuario usuario = Usuario.builder()
                .id(123L)
                .nombre("Admin")
                .apellido("Test")
                .email("admin.dto@tranqui.com")
                .rol(Rol.ADMIN)
                .password("hash-super-secreto")
                .codigoVerificacion("123456")
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusHours(1))
                .resetPasswordCode("654321")
                .resetPasswordExpiresAt(LocalDateTime.now().plusHours(1))
                .mpAccessTokenEncrypted("mp-token-123")
                .mpRefreshTokenEncrypted("mp-refresh-123")
                .mpUserId("mp-user-1")
                .googleAccessTokenEncrypted("google-token-123")
                .googleRefreshTokenEncrypted("google-refresh-123")
                .credencialToken("cred-token-123")
                .qbi2ClientId("qbi2-client-123")
                .build();

        UsuarioAdminDto dto = UsuarioAdminDto.desde(usuario);
        String json = objectMapper.writeValueAsString(dto);
        JsonNode root = objectMapper.readTree(json);

        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            assertThat(field)
                    .doesNotContain("password")
                    .doesNotStartWith("codigo_verificacion")
                    .doesNotStartWith("reset_password")
                    .doesNotStartWith("mp_")
                    .doesNotStartWith("google_")
                    .isNotEqualTo("credencial_token")
                    .isNotEqualTo("qbi2_client_id");
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPassword_publicaEventoOutbox_ySinEnvioDirectoNoLlamaResend() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Usuario Pass")
                .email("reset." + System.nanoTime() + "@mail.com")
                .password("old-hash")
                .rol(Rol.PACIENTE)
                .build());

        mockMvc.perform(post("/api/admin/users/" + usuario.getId() + "/reset-password"))
                .andExpect(status().isOk());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publicar(
                eq(Topicos.IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1),
                eq(String.valueOf(usuario.getId())),
                anyInt(),
                captor.capture()
        );

        assertThat(captor.getValue()).isInstanceOf(PasswordTemporalGeneradaData.class);
        PasswordTemporalGeneradaData data = (PasswordTemporalGeneradaData) captor.getValue();
        assertThat(data.usuarioId()).isEqualTo(usuario.getId());
        assertThat(data.email()).isEqualTo(usuario.getEmail());
        assertThat(data.passwordTemporal()).isNotBlank();
    }

    @Test
    @WithMockUser(username = "paciente.soporte@mail.com", roles = "PACIENTE")
    void solicitudCopiaDatos_publicaEventoOutbox_ySinEnvioDirectoNoLlamaResend() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Paciente Soporte")
                .email("paciente.soporte@mail.com")
                .password("hash-123")
                .rol(Rol.PACIENTE)
                .build());

        mockMvc.perform(post("/api/soporte/solicitud-datos"))
                .andExpect(status().isOk());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publicar(
                eq(Topicos.IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1),
                eq(String.valueOf(usuario.getId())),
                anyInt(),
                captor.capture()
        );

        assertThat(captor.getValue()).isInstanceOf(SolicitudDatosData.class);
        SolicitudDatosData data = (SolicitudDatosData) captor.getValue();
        assertThat(data.usuarioId()).isEqualTo(usuario.getId());
        assertThat(data.email()).isEqualTo(usuario.getEmail());
        assertThat(data.rol()).isEqualTo("PACIENTE");
    }

    @Test
    void registro_soloPermitePacienteYPsiquiatra() throws Exception {
        // 1. ADMIN rechazado
        String emailAdmin = "intento.admin." + System.nanoTime() + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123",
                                  "rol": "ADMIN",
                                  "nombre": "Falso Admin",
                                  "aceptaTerminos": true
                                }
                                """.formatted(emailAdmin)))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmail(emailAdmin)).isEmpty();

        // 2. VISITADOR rechazado
        String emailVisitador = "intento.visitador." + System.nanoTime() + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123",
                                  "rol": "VISITADOR",
                                  "nombre": "Falso Visitador",
                                  "aceptaTerminos": true
                                }
                                """.formatted(emailVisitador)))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmail(emailVisitador)).isEmpty();

        // 3. rol null rechazado
        String emailNull = "intento.null." + System.nanoTime() + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123",
                                  "nombre": "Sin Rol",
                                  "aceptaTerminos": true
                                }
                                """.formatted(emailNull)))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByEmail(emailNull)).isEmpty();

        // 4. PACIENTE aceptado
        String emailPaciente = "paciente.ok." + System.nanoTime() + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123",
                                  "rol": "PACIENTE",
                                  "nombre": "Paciente Real",
                                  "aceptaTerminos": true
                                }
                                """.formatted(emailPaciente)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findByEmail(emailPaciente)).isPresent();

        // 5. PSIQUIATRA aceptado
        String emailPsi = "psi.ok." + System.nanoTime() + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123",
                                  "rol": "PSIQUIATRA",
                                  "nombre": "Doctor Real",
                                  "aceptaTerminos": true
                                }
                                """.formatted(emailPsi)))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findByEmail(emailPsi)).isPresent();
    }
}
