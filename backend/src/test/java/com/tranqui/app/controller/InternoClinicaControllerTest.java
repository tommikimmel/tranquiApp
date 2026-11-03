package com.tranqui.app.controller;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.InformeClinico;
import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.SeguimientoDiario;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.TipoConcepto;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.InformeClinicoRepository;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.SeguimientoDiarioRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 7: rutas internas del monolito para la carga inicial de la historia clínica, recetas y solicitudes.
 * Solo con JWT de servicio, paginadas por id y con fechas en formato ISO.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoClinicaControllerTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SeguimientoDiarioRepository seguimientoDiarioRepository;
    @Autowired private InformeClinicoRepository informeClinicoRepository;
    @Autowired private RecetaRepository recetaRepository;
    @Autowired private SolicitudDocumentoRepository solicitudDocumentoRepository;

    private ResponseEntity<String> get(String ruta, boolean conToken) {
        HttpHeaders headers = new HttpHeaders();
        if (conToken) {
            headers.setBearerAuth(new ServiceJwt("clave-servicio-de-test-de-32-bytes-ok").emitirToken("profesionales"));
        }
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Usuario crearUsuario(String nombre, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@mail.com")
                .rol(rol)
                .build());
    }

    @Test
    void sinTokenDeServicio_401() {
        assertThat(get("/internal/monolito/clinica/seguimientos", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/clinica/informes", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/clinica/recetas", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/internal/monolito/clinica/solicitudes", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void seguimientos_paginacionYFechasIso() {
        Usuario medico = crearUsuario("DraSeg", Rol.PSIQUIATRA);
        Usuario paciente = crearUsuario("PacSeg", Rol.PACIENTE);

        SeguimientoDiario s = seguimientoDiarioRepository.save(SeguimientoDiario.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.of(2026, 10, 25))
                .estadoAnimo("Bueno")
                .sintomas("Ansiedad leve")
                .notas("Evolución favorable")
                .estado("VIGENTE")
                .hashIntegridad("hash-seg-123")
                .build());

        ResponseEntity<String> r = get("/internal/monolito/clinica/seguimientos?pagina=0&tamanio=1000", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"id\":" + s.getId())
                .contains("\"pacienteId\":" + paciente.getId())
                .contains("\"medicoId\":" + medico.getId())
                .contains("\"fecha\":\"2026-10-25\"")
                .doesNotContain("25/10/2026")
                .contains("\"estadoAnimo\":\"Bueno\"")
                .contains("\"estado\":\"VIGENTE\"")
                .contains("\"hashIntegridad\":\"hash-seg-123\"");

        // Paginación
        ResponseEntity<String> paginado = get("/internal/monolito/clinica/seguimientos?pagina=9999&tamanio=10", true);
        assertThat(paginado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paginado.getBody()).isEqualTo("[]");
    }

    @Test
    void informes_paginacionYFechasIso() {
        Usuario medico = crearUsuario("DraInf", Rol.PSIQUIATRA);
        Usuario paciente = crearUsuario("PacInf", Rol.PACIENTE);

        InformeClinico i = informeClinicoRepository.save(InformeClinico.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.of(2026, 10, 26))
                .tipoInforme("Evaluativo")
                .planTrabajo("Plan inicial")
                .contenido("Contenido confidencial")
                .nombreArchivo("informe.pdf")
                .estado("VIGENTE")
                .informeOriginalId(null)
                .motivo(null)
                .hashIntegridad("hash-inf-456")
                .build());

        ResponseEntity<String> r = get("/internal/monolito/clinica/informes?pagina=0&tamanio=1000", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"id\":" + i.getId())
                .contains("\"pacienteId\":" + paciente.getId())
                .contains("\"medicoId\":" + medico.getId())
                .contains("\"fecha\":\"2026-10-26\"")
                .doesNotContain("26/10/2026")
                .contains("\"tipoInforme\":\"Evaluativo\"")
                .contains("\"planTrabajo\":\"Plan inicial\"")
                .contains("\"contenido\":\"Contenido confidencial\"")
                .contains("\"nombreArchivo\":\"informe.pdf\"")
                .contains("\"estado\":\"VIGENTE\"")
                .contains("\"hashIntegridad\":\"hash-inf-456\"");
    }

    @Test
    void recetas_paginacion() {
        Usuario medico = crearUsuario("DraRec", Rol.PSIQUIATRA);
        Usuario paciente = crearUsuario("PacRec", Rol.PACIENTE);

        Receta rec = recetaRepository.save(Receta.builder()
                .medico(medico)
                .paciente(paciente)
                .medicamentos("[{\"nombre\":\"Clonazepam\"}]")
                .laboratorios("[\"Bago\"]")
                .diagnostico("F41.1")
                .indicaciones("1 por noche")
                .pdfUrl("https://s3.tranqui/receta-1.pdf")
                .qbi2IdReceta("REC-987")
                .qbi2Verificador("https://verum.ar/abc")
                .qbi2NroCuir("CUIR-111")
                .qbi2FechaVencimiento("2026-11-25")
                .qbi2IdTransaccion("TX-555")
                .build());

        ResponseEntity<String> r = get("/internal/monolito/clinica/recetas?pagina=0&tamanio=1000", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"id\":" + rec.getId())
                .contains("\"medicoId\":" + medico.getId())
                .contains("\"pacienteId\":" + paciente.getId())
                .contains("\"qbi2IdReceta\":\"REC-987\"")
                .contains("\"diagnostico\":\"F41.1\"")
                .contains("\"pdfUrl\":\"https://s3.tranqui/receta-1.pdf\"");
    }

    @Test
    void solicitudes_paginacion() {
        Usuario medico = crearUsuario("DraSol", Rol.PSIQUIATRA);
        Usuario paciente = crearUsuario("PacSol", Rol.PACIENTE);

        SolicitudDocumento sol = solicitudDocumentoRepository.save(SolicitudDocumento.builder()
                .medico(medico)
                .paciente(paciente)
                .tipoConcepto(TipoConcepto.RECETA)
                .precio(new BigDecimal("15000.00"))
                .estado(EstadoPago.APROBADO)
                .transactionId("MP-TX-999")
                .emitido(true)
                .urlDescarga("https://descarga.com/doc.pdf")
                .fechaEmision(LocalDateTime.of(2026, 10, 26, 12, 0))
                .build());

        ResponseEntity<String> r = get("/internal/monolito/clinica/solicitudes?pagina=0&tamanio=1000", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .contains("\"id\":" + sol.getId())
                .contains("\"pacienteId\":" + paciente.getId())
                .contains("\"medicoId\":" + medico.getId())
                .contains("\"tipoConcepto\":\"RECETA\"")
                .contains("\"estado\":\"APROBADO\"")
                .contains("\"transactionId\":\"MP-TX-999\"")
                .contains("\"emitido\":true");
    }
}
