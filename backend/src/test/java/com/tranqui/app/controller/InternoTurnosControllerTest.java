package com.tranqui.app.controller;

import com.tranqui.app.eventos.OutboxRepository;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 6: rutas internas del monolito para el servicio de turnos (carga inicial, pacientes y "buscar o
 * crear paciente"), y las que el monolito atiende con la URL de turnos y de notificaciones (vuelta atrás).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoTurnosControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private OutboxRepository outboxRepository;

    private ResponseEntity<String> llamar(HttpMethod metodo, String ruta, String cuerpo, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        if (conToken) {
            headers.setBearerAuth(new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("turnos"));
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private Turno turno() {
        Usuario medico = usuarioRepository.save(Usuario.builder().nombre("Dra").email("dra.t." + System.nanoTime() + "@mail.com")
                .rol(Rol.PSIQUIATRA).build());
        Usuario paciente = usuarioRepository.save(Usuario.builder().nombre("Pac").email("pac.t." + System.nanoTime() + "@mail.com")
                .rol(Rol.PACIENTE).dni("30111222").obraSocial("OSDE").build());
        return turnoRepository.save(Turno.builder().medico(medico).paciente(paciente).fecha(LocalDate.now().plusDays(4))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45)).tipo(TipoTurno.PARTICULAR).modalidad(Modalidad.ONLINE)
                .estado(EstadoTurno.CONFIRMADO).precio(new BigDecimal("60000")).tokenReserva("reserva-123")
                .tokenConfirmacionAsistencia("asistencia-456").build());
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(llamar(HttpMethod.GET, "/internal/monolito/turnos", null, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(llamar(HttpMethod.POST, "/internal/monolito/pacientes", "{}", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cargaInicial_traeLosTurnosConLosTokensYLosPacientes() {
        Turno t = turno();

        ResponseEntity<String> turnos = llamar(HttpMethod.GET, "/internal/monolito/turnos?pagina=0&tamanio=1000", null, true);
        assertThat(turnos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(turnos.getBody()).contains("\"turnoId\":" + t.getId()).contains("\"tokenReserva\":\"reserva-123\"")
                .contains("\"tokenConfirmacionAsistencia\":\"asistencia-456\"").contains("\"estado\":\"CONFIRMADO\"");

        ResponseEntity<String> paciente = llamar(HttpMethod.GET, "/internal/monolito/pacientes/" + t.getPaciente().getId(), null, true);
        assertThat(paciente.getBody()).contains("\"dni\":\"30111222\"").contains("\"obraSocial\":\"OSDE\"")
                .contains("\"rol\":\"PACIENTE\"").doesNotContain("password");
    }

    @Test
    void buscarOCrear_creaAlPacienteNormalizadoYAvisa_yAlQueExisteLeActualizaLaObraSocial() {
        String email = "Nuevo." + System.nanoTime() + "@Mail.com ";
        long antes = outboxRepository.count();

        ResponseEntity<String> creado = llamar(HttpMethod.POST, "/internal/monolito/pacientes",
                "{\"email\":\"" + email + "\",\"nombre\":\"Juana\",\"telefono\":\"11 5555 1234\"}", true);

        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario u = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.PACIENTE);
        assertThat(u.getTelefono()).isEqualTo("+54 11 5555 1234");
        assertThat(outboxRepository.count()).isGreaterThan(antes);
        assertThat(outboxRepository.findAll()).anyMatch(o -> Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1.equals(o.getTopic())
                && String.valueOf(u.getId()).equals(o.getAggregateId()));

        ResponseEntity<String> existente = llamar(HttpMethod.POST, "/internal/monolito/pacientes",
                "{\"email\":\"" + email.trim() + "\",\"nombre\":\"Otra\",\"obraSocial\":\"Swiss\",\"numAfiliado\":\"123\"}", true);
        assertThat(existente.getBody()).contains("\"id\":" + u.getId()).contains("\"obraSocial\":\"Swiss\"")
                .contains("\"nombre\":\"Juana\"");
    }

    @Test
    void enlacesDeAsistencia_tambienConLaUrlDelServicioDeTurnos() {
        Turno t = turno();

        ResponseEntity<String> r = llamar(HttpMethod.GET, "/internal/turnos/turnos/" + t.getId() + "/enlaces-asistencia", null, true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("https://tranqui.test/api/turnos/" + t.getId() + "/confirmar-asistencia?token=asistencia-456");
    }

    @Test
    void documentoAdjunto_conLaUrlDelServicioDeNotificaciones() {
        ResponseEntity<String> r = llamar(HttpMethod.POST, "/internal/notificaciones/documentos",
                "{\"email\":\"pac@mail.com\",\"pacienteNombre\":\"Ana\",\"medicoNombre\":\"Dra. Paz\",\"tipoDocumento\":\"Certificado\","
                        + "\"archivoDataUri\":\"data:application/pdf;base64,JVBERi0=\",\"archivoNombre\":\"certificado.pdf\"}", true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"enviado\":true");
    }
}
