package com.tranqui.profesionales.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.Solicitudes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Llamadas de profesionales al monolito, siempre por el gateway con un JWT de servicio (sección 4.2):
 * la carga inicial, la sincronización de identidad, verificación y suscripción, la parte de identidad de
 * {@code PUT /api/medicos/perfil}, las copias de pacientes y turnos, y la carga inicial de la historia clínica.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "profesionales";

    /** {@code InternoProfesionalesController.ProfesionalDto} del monolito. */
    public record ProfesionalMonolito(Long id, int version, String email, String rol, String profession,
                                      String nombre, String apellido, String telefono, String sexo,
                                      LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                                      String fotoUrl, String fotoHash,
                                      Boolean verificadoAdmin, boolean mpVinculada,
                                      String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, boolean puedeRecetar,
                                      PerfilProfesionalData perfil,
                                      List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                      List<TarifasProfesionalData.Tarifa> tarifas) {

        public Profesionales.DatosMonolito datos() {
            return new Profesionales.DatosMonolito(id, version, email, rol, profession, nombre, apellido, telefono, sexo,
                    fechaNacimiento, cuil, tipoDocumento, numeroDocumento, fotoUrl, fotoHash, verificadoAdmin, mpVinculada,
                    suscripcionEstado, suscripcionPeriodoHasta, puedeRecetar);
        }

        public ProfesionalMonolito withDisponibilidad(List<DisponibilidadProfesionalData.Franja> franjas) {
            return new ProfesionalMonolito(id, version, email, rol, profession, nombre, apellido, telefono, sexo, fechaNacimiento,
                    cuil, tipoDocumento, numeroDocumento, fotoUrl, fotoHash, verificadoAdmin, mpVinculada, suscripcionEstado,
                    suscripcionPeriodoHasta, puedeRecetar, perfil, franjas, tarifas);
        }

        public ProfesionalMonolito withTarifas(List<TarifasProfesionalData.Tarifa> lista) {
            return new ProfesionalMonolito(id, version, email, rol, profession, nombre, apellido, telefono, sexo, fechaNacimiento,
                    cuil, tipoDocumento, numeroDocumento, fotoUrl, fotoHash, verificadoAdmin, mpVinculada, suscripcionEstado,
                    suscripcionPeriodoHasta, puedeRecetar, perfil, disponibilidad, lista);
        }
    }

    /** {@code InternoProfesionalesController.IdentidadDto} del monolito. */
    public record Identidad(String nombre, String apellido, String telefono, String sexo, LocalDate fechaNacimiento,
                            Long cuil, String tipoDocumento, Integer numeroDocumento, String fotoUrl) {}

    /** {@code InternoTurnosController.PacienteDto} del monolito. */
    public record PacienteMonolito(
            Long id, int version, String rol, String email, String nombre, String apellido,
            String telefono, String dni, String direccion, String obraSocial, String numAfiliado,
            String sexo, LocalDate fechaNacimiento, Long cuil, String tipoDocumento,
            Integer numeroDocumento, String datosOfuscado, Integer credencialCodEntidad,
            String credencialPan, String credencialPlan, String credencialToken,
            boolean emailHabilitado,
            String domicilioCalle, String domicilioNumero, String domicilioPiso,
            String domicilioDpto, String domicilioCodigoPostal, String domicilioLocalidad,
            String domicilioProvincia, String domicilioPais
    ) {
        public Personas.Persona persona() {
            return new Personas.Persona(
                    id, version, rol, email, nombre, apellido, telefono, dni, direccion,
                    obraSocial, numAfiliado, sexo, fechaNacimiento, cuil, tipoDocumento,
                    numeroDocumento, datosOfuscado, credencialCodEntidad, credencialPan,
                    credencialPlan, credencialToken, emailHabilitado,
                    domicilioCalle, domicilioNumero, domicilioPiso, domicilioDpto,
                    domicilioCodigoPostal, domicilioLocalidad, domicilioProvincia, domicilioPais
            );
        }
    }

    /** {@code InternoTurnosController.TurnoDto} del monolito (carga inicial y resincronización). */
    public record TurnoMonolito(int version, TurnoActualizadoData turno, String tokenReserva, String tokenConfirmacionAsistencia) {}

    /** {@code InternoClinicaController.SeguimientoDiarioDto} del monolito. */
    public record SeguimientoMonolito(
            Long id,
            Long pacienteId,
            Long medicoId,
            LocalDate fecha,
            String estadoAnimo,
            String sintomas,
            String notas,
            LocalDateTime fechaCreacion,
            String estado,
            String motivo,
            String hashIntegridad
    ) {
        public Seguimientos.Seguimiento seguimiento() {
            return new Seguimientos.Seguimiento(
                    id, pacienteId, medicoId, fecha, estadoAnimo, sintomas, notas,
                    fechaCreacion, estado, motivo, hashIntegridad
            );
        }
    }

    /** {@code InternoClinicaController.InformeClinicoDto} del monolito. */
    public record InformeMonolito(
            Long id,
            Long pacienteId,
            Long medicoId,
            LocalDate fecha,
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            LocalDateTime fechaCreacion,
            String estado,
            Long informeOriginalId,
            String motivo,
            String hashIntegridad
    ) {
        public Informes.Informe informe() {
            return new Informes.Informe(
                    id, pacienteId, medicoId, fecha, tipoInforme, planTrabajo, contenido,
                    nombreArchivo, fechaCreacion, estado, informeOriginalId, motivo, hashIntegridad
            );
        }
    }

    /** {@code InternoClinicaController.RecetaDto} del monolito. */
    public record RecetaMonolito(
            Long id,
            Long medicoId,
            Long pacienteId,
            String medicamentos,
            String laboratorios,
            String diagnostico,
            String indicaciones,
            String pdfUrl,
            String qbi2IdReceta,
            String qbi2Verificador,
            String qbi2NroCuir,
            String qbi2FechaVencimiento,
            String qbi2IdTransaccion,
            LocalDateTime fechaEmision
    ) {
        public Recetas.Receta receta() {
            return new Recetas.Receta(
                    id, medicoId, pacienteId, medicamentos, laboratorios, diagnostico,
                    indicaciones, pdfUrl, qbi2IdReceta, qbi2Verificador, qbi2NroCuir,
                    qbi2FechaVencimiento, qbi2IdTransaccion, fechaEmision
            );
        }
    }

    /** {@code InternoClinicaController.SolicitudDocumentoDto} del monolito. */
    public record SolicitudMonolito(
            Long id,
            Long pacienteId,
            Long medicoId,
            String tipoConcepto,
            BigDecimal precio,
            String estado,
            String transactionId,
            boolean emitido,
            String urlDescarga,
            LocalDateTime fechaEmision,
            LocalDateTime fechaCreacion
    ) {
        public Solicitudes.Solicitud solicitud() {
            return new Solicitudes.Solicitud(
                    id, pacienteId, medicoId, tipoConcepto, precio, estado,
                    transactionId, emitido, urlDescarga, fechaEmision, fechaCreacion
            );
        }
    }

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    public ClienteInterno(@Value("${tranqui.gateway.url}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret}") String claveServicio) {
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    private <T> T get(String uri, ParameterizedTypeReference<T> tipo, Object... variables) {
        return http.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
    }

    /** Carga inicial: los profesionales completos (con foto, perfil, disponibilidad y tarifas), paginados. */
    public List<ProfesionalMonolito> profesionales(int pagina, int tamanio) {
        List<ProfesionalMonolito> r = get("/internal/monolito/profesionales?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Un usuario completo (puede haber dejado de ser profesional: ver {@code rol}). */
    public ProfesionalMonolito profesional(Long id) {
        return get("/internal/monolito/profesionales/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    /** Identidad sin la foto, verificación, Mercado Pago y suscripción de todos los profesionales. */
    public List<ProfesionalMonolito> estado() {
        List<ProfesionalMonolito> r = get("/internal/monolito/profesionales/estado", new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }

    /** La parte de identidad del perfil. Si el monolito no responde, tira la excepción: no se guarda nada. */
    public ProfesionalMonolito guardarIdentidad(Long id, Identidad identidad) {
        return http.put().uri("/internal/monolito/profesionales/{id}/identidad", id)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(identidad)
                .retrieve().body(ProfesionalMonolito.class);
    }

    /** Todos los usuarios (pacientes y profesionales), paginados por id. */
    public List<PacienteMonolito> pacientes(int pagina, int tamanio) {
        List<PacienteMonolito> r = get("/internal/monolito/pacientes?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Un usuario por id (para refrescar la copia tras eventos de identidad). */
    /** Actualiza la ficha de un paciente en el monolito (dueño de identidad). */
    public PacienteMonolito actualizarPaciente(Long id, Object body) {
        return http.put().uri("/internal/monolito/pacientes/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(body)
                .retrieve().body(PacienteMonolito.class);
    }

    public PacienteMonolito paciente(Long id) {
        return get("/internal/monolito/pacientes/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    /** Todos los turnos, paginados por id (para carga inicial y resincronización periódica). */
    public List<TurnoMonolito> turnos(int pagina, int tamanio) {
        List<TurnoMonolito> r = get("/internal/monolito/turnos?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Seguimientos diarios del monolito para la carga inicial clínica. */
    public List<SeguimientoMonolito> seguimientos(int pagina, int tamanio) {
        List<SeguimientoMonolito> r = get("/internal/monolito/clinica/seguimientos?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Informes clínicos del monolito para la carga inicial clínica. */
    public List<InformeMonolito> informes(int pagina, int tamanio) {
        List<InformeMonolito> r = get("/internal/monolito/clinica/informes?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Recetas del monolito para la carga inicial clínica. */
    public List<RecetaMonolito> recetas(int pagina, int tamanio) {
        List<RecetaMonolito> r = get("/internal/monolito/clinica/recetas?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Solicitudes de documentos del monolito para la carga inicial clínica. */
    public List<SolicitudMonolito> solicitudes(int pagina, int tamanio) {
        List<SolicitudMonolito> r = get("/internal/monolito/clinica/solicitudes?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }
}
