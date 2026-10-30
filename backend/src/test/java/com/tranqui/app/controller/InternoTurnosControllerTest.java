package com.tranqui.app.controller;

import com.tranqui.eventos.contratos.Contratos;
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
                .rol(Rol.PACIENTE).dni("30111222").obraSocial("OSDE").fechaNacimiento(LocalDate.of(1990, 3, 7)).domicilioCalle("Av. Santa Fe").domicilioLocalidad("CABA").build());
        return turnoRepository.save(Turno.builder().medico(medico).paciente(paciente).fecha(LocalDate.now().plusDays(4))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45)).tipo(TipoTurno.PARTICULAR).modalidad(Modalidad.ONLINE)
                .estado(EstadoTurno.CONFIRMADO).precio(new BigDecimal("60000")).tokenReserva("reserva-123")
                .tokenConfirmacionAsistencia("asistencia-456").build());
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(llamar(HttpMethod.GET, "/internal/monolito/turnos", null, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(llamar(HttpMethod.POST, "/internal/monolito/pacientes", "{}", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(llamar(HttpMethod.PUT, "/internal/monolito/pacientes/1", "{}", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cargaInicial_traeLosTurnosConLosTokensYLosPacientes() {
        Turno t = turno();

        ResponseEntity<String> turnos = llamar(HttpMethod.GET, "/internal/monolito/turnos?pagina=0&tamanio=1000", null, true);
        Contratos.mismaForma("internas/monolito/turnos.json", turnos.getBody());
        assertThat(turnos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(turnos.getBody()).contains("\"turnoId\":" + t.getId()).contains("\"tokenReserva\":\"reserva-123\"")
                .contains("\"tokenConfirmacionAsistencia\":\"asistencia-456\"").contains("\"estado\":\"CONFIRMADO\"")
                .contains("\"fecha\":\"" + t.getFecha() + "\"");

        ResponseEntity<String> paciente = llamar(HttpMethod.GET, "/internal/monolito/pacientes/" + t.getPaciente().getId(), null, true);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", paciente.getBody());
        assertThat(paciente.getBody()).contains("\"dni\":\"30111222\"").contains("\"obraSocial\":\"OSDE\"")
                .contains("\"rol\":\"PACIENTE\"").contains("\"fechaNacimiento\":\"1990-03-07\"")
                .doesNotContain("password");

        ResponseEntity<String> pacientes = llamar(HttpMethod.GET, "/internal/monolito/pacientes?pagina=0&tamanio=1000", null, true);
        assertThat(pacientes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Contratos.mismaForma("internas/monolito/pacientes.json", pacientes.getBody());
    }

    @Test
    void buscarOCrear_creaAlPacienteNormalizadoYAvisa_yAlQueExisteLeActualizaLaObraSocial() {
        String email = "Nuevo." + System.nanoTime() + "@Mail.com ";
        long antes = outboxRepository.count();

        ResponseEntity<String> creado = llamar(HttpMethod.POST, "/internal/monolito/pacientes",
                "{\"email\":\"" + email + "\",\"nombre\":\"Juana\",\"telefono\":\"11 5555 1234\"}", true);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", creado.getBody());

        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario u = usuarioRepository.findByEmail(email.trim().toLowerCase()).orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.PACIENTE);
        assertThat(u.getTelefono()).isEqualTo("+54 11 5555 1234");
        assertThat(outboxRepository.count()).isGreaterThan(antes);
        assertThat(outboxRepository.findAll()).anyMatch(o -> Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1.equals(o.getTopic())
                && String.valueOf(u.getId()).equals(o.getAggregateId()));

        ResponseEntity<String> existente = llamar(HttpMethod.POST, "/internal/monolito/pacientes",
                "{\"email\":\"" + email.trim() + "\",\"nombre\":\"Otra\",\"obraSocial\":\"Swiss\",\"numAfiliado\":\"123\"}", true);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", existente.getBody());
        assertThat(existente.getBody()).contains("\"id\":" + u.getId()).contains("\"obraSocial\":\"Swiss\"")
                .contains("\"nombre\":\"Juana\"");
    }

    @Test
    void enlacesDeAsistencia_tambienConLaUrlDelServicioDeTurnos() {
        Turno t = turno();

        ResponseEntity<String> r = llamar(HttpMethod.GET, "/internal/turnos/turnos/" + t.getId() + "/enlaces-asistencia", null, true);
        Contratos.mismaForma("internas/turnos/enlaces-asistencia.json", r.getBody());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("https://tranqui.test/api/turnos/" + t.getId() + "/confirmar-asistencia?token=asistencia-456");
    }

    @Test
    void documentoAdjunto_conLaUrlDelServicioDeNotificaciones() {
        ResponseEntity<String> r = llamar(HttpMethod.POST, "/internal/notificaciones/documentos",
                Contratos.fixture("internas/notificaciones/documentos.request.json").toString(), true);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"enviado\":true");
    }

    @Test
    void recetaEmitida_conLaUrlDelServicioDeNotificaciones() {
        ResponseEntity<String> r = llamar(HttpMethod.POST, "/internal/notificaciones/recetas",
                Contratos.fixture("internas/notificaciones/recetas.request.json").toString(), true);
        Contratos.mismaForma("internas/notificaciones/resultado.json", r.getBody());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"enviado\":true");

        ResponseEntity<String> sinEmail = llamar(HttpMethod.POST, "/internal/notificaciones/recetas",
                "{\"email\":\"\",\"pacienteNombre\":\"Ana\"}", true);
        assertThat(sinEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actualizarPaciente_guardaYPublicaIdentidadUsuarioActualizado() {
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("Carlos").email("carlos." + System.nanoTime() + "@mail.com")
                .rol(Rol.PACIENTE).build());
        long antes = outboxRepository.count();

        String body = "{\"nombre\":\"Carlos Modificado\",\"apellido\":\"Perez\",\"telefono\":\"11 4444 5555\","
                + "\"dni\":\"28111222\",\"tipoDocumento\":\"DNI\",\"numeroDocumento\":28111222,"
                + "\"fechaNacimiento\":\"1985-04-12\","
                + "\"domicilio\":{\"calle\":\"Corrientes\",\"numero\":\"500\",\"localidad\":\"CABA\",\"provincia\":\"Buenos Aires\"}}";

        ResponseEntity<String> r = llamar(HttpMethod.PUT, "/internal/monolito/pacientes/" + p.getId(), body, true);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", r.getBody());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"nombre\":\"Carlos Modificado\"")
                .contains("\"apellido\":\"Perez\"")
                .contains("\"telefono\":\"+54 11 4444 5555\"")
                .contains("\"domicilioCalle\":\"Corrientes\"")
                .contains("\"domicilioLocalidad\":\"CABA\"");

        Usuario actualizado = usuarioRepository.findById(p.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Carlos Modificado");
        assertThat(actualizado.getTelefono()).isEqualTo("+54 11 4444 5555");
        assertThat(actualizado.getDomicilioCalle()).isEqualTo("Corrientes");

        assertThat(outboxRepository.count()).isGreaterThan(antes);
        assertThat(outboxRepository.findAll()).anyMatch(o -> Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1.equals(o.getTopic())
                && String.valueOf(p.getId()).equals(o.getAggregateId()));
    }

    @Test
    void actualizarPaciente_datosInvalidos_responde400() {
        Usuario p = usuarioRepository.save(Usuario.builder().nombre("Elena").email("elena." + System.nanoTime() + "@mail.com")
                .rol(Rol.PACIENTE).build());

        String body = "{\"nombre\":\"   \"}";
        ResponseEntity<String> r = llamar(HttpMethod.PUT, "/internal/monolito/pacientes/" + p.getId(), body, true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pedidosDelContrato_seAceptanTalCualLosMandanTurnosYProfesionales() throws Exception {
        ResponseEntity<String> creado = llamar(HttpMethod.POST, "/internal/monolito/pacientes",
                Contratos.fixture("internas/fuente-identidad/pacientes-buscar-o-crear.request.json").toString(), true);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = Contratos.MAPPER.readTree(creado.getBody()).get("id").asLong();

        ResponseEntity<String> actualizado = llamar(HttpMethod.PUT, "/internal/monolito/pacientes/" + id,
                Contratos.fixture("internas/fuente-identidad/paciente-actualizar.request.json").toString(), true);
        assertThat(actualizado.getStatusCode()).as(actualizado.getBody()).isEqualTo(HttpStatus.OK);
        Contratos.mismaForma("internas/fuente-identidad/paciente.json", actualizado.getBody());
    }
}
