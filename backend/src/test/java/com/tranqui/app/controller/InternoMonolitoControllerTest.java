package com.tranqui.app.controller;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.jwt.ServiceJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rutas /internal/monolito/** que consulta el servicio de notificaciones (Fase 1): solo con JWT de
 * servicio, también con el gate de contraseña del sitio prendido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoMonolitoControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TurnoRepository turnoRepository;

    private ResponseEntity<String> get(String ruta, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String tokenServicio() {
        return new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("notificaciones");
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(get("/internal/monolito/contactos", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/contactos", "no-es-un-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String otraClave = new ServiceJwt("otra-clave-de-servicio-de-32-bytes!!").emitirToken("notificaciones");
        assertThat(get("/internal/monolito/contactos", otraClave).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contactos_conTokenDeServicio_devuelveLaCopiaMinima() {
        Usuario u = usuarioRepository.save(Usuario.builder().nombre("Lía").apellido("Ríos")
                .email("lia." + System.nanoTime() + "@mail.com").rol(Rol.PACIENTE).build());

        ResponseEntity<String> respuesta = get("/internal/monolito/contactos?pagina=0&tamanio=1000", tokenServicio());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"id\":" + u.getId()).contains(u.getEmail())
                .contains("\"rol\":\"PACIENTE\"").contains("\"emailHabilitado\":true")
                .doesNotContain("password");
    }

    @Autowired private com.tranqui.app.repository.NotificacionRepository notificacionRepository;

    @Test
    void notificacionesVigentes_soloLasDeLasUltimas24Horas() {
        Usuario u = usuarioRepository.save(Usuario.builder().nombre("Lía")
                .email("vig." + System.nanoTime() + "@mail.com").rol(Rol.PACIENTE).build());
        String marca = "vigente-" + System.nanoTime();
        notificacionRepository.save(com.tranqui.app.model.Notificacion.builder().usuario(u).titulo(marca)
                .mensaje("m").tipo("TURNO_CONFIRMADO").leido(true).fechaCreacion(java.time.LocalDateTime.now().minusHours(2)).build());
        notificacionRepository.save(com.tranqui.app.model.Notificacion.builder().usuario(u).titulo("vieja-" + marca)
                .mensaje("m").tipo("TURNO_CONFIRMADO").fechaCreacion(java.time.LocalDateTime.now().minusHours(30)).build());

        ResponseEntity<String> respuesta = get("/internal/monolito/notificaciones-vigentes", tokenServicio());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"titulo\":\"" + marca + "\"").contains("\"usuarioId\":" + u.getId())
                .contains("\"leido\":true").doesNotContain("vieja-" + marca);
    }

    @Autowired private com.tranqui.app.repository.MensajeRepository mensajeRepository;

    @Test
    void mensajesYResumenDeChat_paraLaFase2() {
        Usuario paciente = usuarioRepository.save(Usuario.builder().nombre("Pía")
                .email("chat.pac." + System.nanoTime() + "@mail.com").rol(Rol.PACIENTE).build());
        Usuario medico = usuarioRepository.save(Usuario.builder().nombre("Dra")
                .email("chat.med." + System.nanoTime() + "@mail.com").rol(Rol.PSIQUIATRA).build());
        com.tranqui.app.model.Mensaje m = mensajeRepository.save(com.tranqui.app.model.Mensaje.builder()
                .remitente(paciente).destinatario(medico).contenido("hola doc").build());

        ResponseEntity<String> mensajes = get("/internal/monolito/mensajes?desdeId=" + (m.getId() - 1) + "&tamanio=5",
                tokenServicio());
        assertThat(mensajes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mensajes.getBody()).contains("\"id\":" + m.getId()).contains("\"remitenteId\":" + paciente.getId())
                .contains("\"contenido\":\"hola doc\"").contains("\"leido\":false");

        ResponseEntity<String> resumen = get("/internal/chat/profesionales/" + medico.getId() + "/resumen", tokenServicio());
        assertThat(resumen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resumen.getBody()).contains("\"pacientesConChat\":[" + paciente.getId() + "]")
                .contains("\"" + paciente.getId() + "\":1");
        assertThat(get("/internal/chat/profesionales/" + medico.getId() + "/resumen", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void turnosConfirmados_soloLosDeHoyEnAdelante() {
        Usuario paciente = usuarioRepository.save(Usuario.builder().nombre("Pía")
                .email("tc.pac." + System.nanoTime() + "@mail.com").rol(Rol.PACIENTE).build());
        Usuario medico = usuarioRepository.save(Usuario.builder().nombre("Dra")
                .email("tc.med." + System.nanoTime() + "@mail.com").rol(Rol.PSIQUIATRA).build());
        Turno futuro = turnoRepository.save(Turno.builder().paciente(paciente).medico(medico)
                .fecha(LocalDate.now().plusDays(2)).horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(11, 45))
                .estado(EstadoTurno.CONFIRMADO).tipo(com.tranqui.app.model.TipoTurno.PARTICULAR).build());
        Turno pasado = turnoRepository.save(Turno.builder().paciente(paciente).medico(medico)
                .fecha(LocalDate.now().minusDays(2)).horaInicio(LocalTime.of(11, 0)).horaFin(LocalTime.of(11, 45))
                .estado(EstadoTurno.CONFIRMADO).tipo(com.tranqui.app.model.TipoTurno.PARTICULAR).build());

        ResponseEntity<String> respuesta = get("/internal/monolito/turnos-confirmados", tokenServicio());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"turnoId\":" + futuro.getId()).contains("\"horaInicio\":\"11:00\"")
                .doesNotContain("\"turnoId\":" + pasado.getId() + ",");
    }

    @Test
    void enlacesDeAsistencia_soloSiElTurnoTieneToken() {
        Usuario paciente = usuarioRepository.save(Usuario.builder().nombre("Lía")
                .email("pac." + System.nanoTime() + "@mail.com").rol(Rol.PACIENTE).build());
        Usuario medico = usuarioRepository.save(Usuario.builder().nombre("Dra")
                .email("med." + System.nanoTime() + "@mail.com").rol(Rol.PSIQUIATRA).build());
        Turno turno = Turno.builder().paciente(paciente).medico(medico).fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45)).estado(EstadoTurno.CONFIRMADO)
                .tipo(com.tranqui.app.model.TipoTurno.PARTICULAR).build();
        turno = turnoRepository.save(turno);

        assertThat(get("/internal/monolito/turnos/" + turno.getId() + "/enlaces-asistencia", tokenServicio())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        turno.setTokenConfirmacionAsistencia("tok-123");
        turnoRepository.save(turno);
        ResponseEntity<String> respuesta = get("/internal/monolito/turnos/" + turno.getId() + "/enlaces-asistencia",
                tokenServicio());

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("https://tranqui.test/api/turnos/" + turno.getId() + "/confirmar-asistencia?token=tok-123")
                .contains("https://tranqui.test/api/turnos/" + turno.getId() + "/no-asistira?token=tok-123");
    }
}
