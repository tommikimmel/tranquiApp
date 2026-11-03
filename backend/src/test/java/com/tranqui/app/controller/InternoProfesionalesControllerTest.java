package com.tranqui.app.controller;

import com.tranqui.app.eventos.OutboxRepository;
import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.CopiaProfesionales;
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
 * Fase 5: rutas /internal/monolito/profesionales que usa el servicio de profesionales (carga inicial,
 * sincronización de cada minuto y la parte de identidad del perfil).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoProfesionalesControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private DisponibilidadRepository disponibilidadRepository;
    @Autowired private TarifaMedicoRepository tarifaRepository;
    @Autowired private OutboxRepository outboxRepository;

    private ResponseEntity<String> llamar(HttpMethod metodo, String ruta, String token, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private static String tokenServicio() {
        return new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("profesionales");
    }

    private Usuario profesional() {
        Usuario u = usuarioRepository.save(Usuario.builder().nombre("Ana").apellido("Paz")
                .email("ana." + System.nanoTime() + "@mail.com").rol(Rol.PSIQUIATRA).profession("psiquiatra")
                .specialty("Psiquiatría").titulo("Médica").fechaNacimiento(LocalDate.of(1980, 5, 4)).matriculaTipo("MN").matriculaNumero(1234)
                .ofreceOnline(true).ofrecePresencial(false).duracionTurnoMinutos(50)
                .fotoUrl("data:image/png;base64,AAAA").verificadoAdmin(true).build());
        disponibilidadRepository.save(Disponibilidad.builder().medico(u).diaSemana(1)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(12, 0)).modalidad(Modalidad.ONLINE).build());
        tarifaRepository.save(TarifaMedico.builder().medico(u).servicioId("particular").label("Consulta particular")
                .precio(new BigDecimal("60000")).habilitado(true).build());
        return u;
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(llamar(HttpMethod.GET, "/internal/monolito/profesionales/estado", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cargaInicial_traeElPerfilLaFotoLaDisponibilidadYLasTarifas() {
        Usuario u = profesional();

        ResponseEntity<String> r = llamar(HttpMethod.GET, "/internal/monolito/profesionales/" + u.getId(), tokenServicio(), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"rol\":\"PSIQUIATRA\"").contains("\"fotoUrl\":\"data:image/png;base64,AAAA\"")
                .contains("\"especialidad\":\"Psiquiatría\"").contains("\"matriculaNumero\":1234")
                .contains("\"duracionTurnoMinutos\":50")
                .contains("\"horaInicio\":\"09:00\"").contains("\"modalidad\":\"ONLINE\"")
                .contains("\"servicioId\":\"particular\"").contains("\"verificadoAdmin\":true")
                .contains("\"fechaNacimiento\":\"1980-05-04\"")
                .doesNotContain("password");
    }

    @Test
    void estado_vaSinLaFotoPeroConSuHash() {
        Usuario u = profesional();

        ResponseEntity<String> r = llamar(HttpMethod.GET, "/internal/monolito/profesionales/estado", tokenServicio(), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"id\":" + u.getId())
                .contains("\"fotoHash\":\"" + CopiaProfesionales.hashFoto("data:image/png;base64,AAAA") + "\"")
                .doesNotContain("base64,AAAA").doesNotContain("\"servicioId\"");
    }

    @Test
    void identidad_guardaLosDatosYAvisaElCambio() {
        Usuario u = profesional();
        long antes = outboxRepository.count();

        String cuerpo = "{\"nombre\":\"Ana María\",\"apellido\":\"Paz\",\"telefono\":\"+54 11 5555\",\"sexo\":\"F\","
                + "\"fechaNacimiento\":\"1980-05-04\",\"cuil\":27123456780,\"tipoDocumento\":\"DNI\","
                + "\"numeroDocumento\":12345678,\"fotoUrl\":\"data:image/png;base64,BBBB\"}";
        ResponseEntity<String> r = llamar(HttpMethod.PUT, "/internal/monolito/profesionales/" + u.getId() + "/identidad",
                tokenServicio(), cuerpo);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Usuario guardado = usuarioRepository.findById(u.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Ana María");
        assertThat(guardado.getFechaNacimiento()).isEqualTo(LocalDate.of(1980, 5, 4));
        assertThat(guardado.getCuil()).isEqualTo(27123456780L);
        assertThat(guardado.getFotoUrl()).isEqualTo("data:image/png;base64,BBBB");
        assertThat(guardado.getSpecialty()).isEqualTo("Psiquiatría");
        assertThat(outboxRepository.count()).isGreaterThan(antes);
        assertThat(outboxRepository.findAll()).anyMatch(o -> Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1.equals(o.getTopic())
                && String.valueOf(u.getId()).equals(o.getAggregateId()));
    }
}
