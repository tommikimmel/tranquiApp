package com.tranqui.profesionales.interno;

import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.ValidadorEventos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

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
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Llamadas de profesionales al monolito, siempre por el gateway con un JWT de servicio (sección 4.2):
 * la carga inicial, la sincronización de identidad, verificación y suscripción, la parte de identidad de
 * {@code PUT /api/medicos/perfil}, las copias de pacientes y turnos, y la carga inicial de la historia clínica.
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "profesionales";

    /** {@code InternoProfesionalesController.ProfesionalDto} del monolito. */
    public record ProfesionalMonolito(@NotNull @Positive Long id, int version, @Email String email, String rol, String profession,
                                      String nombre, String apellido, String telefono, String sexo,
                                      LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                                      String fotoUrl, String fotoHash,
                                      Boolean verificadoAdmin, boolean mpVinculada,
                                      String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, boolean puedeRecetar,
                                      @Valid PerfilProfesionalData perfil,
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

    /** {@code CuentasMp.CuentaMpResumenDto} del servicio de pagos. */
    public record CuentaMp(@NotNull @Positive Long profesionalId, boolean vinculada, long version) {}

    /** {@code Dtos.EstadoSuscripcionDto} del servicio de pagos (el mismo dato que suscripciones.suscripcion.cambio-estado.v2). */
    public record EstadoSuscripcion(@NotNull @Positive Long professionalId, String estado, String planCode, boolean acceso,
                                    boolean puedeRecetar, LocalDateTime periodoHasta, LocalDateTime graciaHasta,
                                    Long version) {}

    /** {@code InternoProfesionalesController.IdentidadDto} del monolito. */
    public record Identidad(String nombre, String apellido, String telefono, String sexo, LocalDate fechaNacimiento,
                            Long cuil, String tipoDocumento, Integer numeroDocumento, String fotoUrl) {}

    /** {@code InternoTurnosController.PacienteDto} del monolito. */
    public record PacienteMonolito(
            @NotNull @Positive Long id, int version, String rol, @Email String email, String nombre, String apellido,
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
    public record TurnoMonolito(int version, @NotNull @Valid TurnoActualizadoData turno, String tokenReserva, String tokenConfirmacionAsistencia) {}

    /** Lo que manda el servicio de turnos ({@code /internal/turnos/turnos}): sin los tokens, que quedan en turnos. */
    public record TurnoServicio(int version, @NotNull @Valid TurnoActualizadoData turno) {}

    /** {@code InternoClinicaController.SeguimientoDiarioDto} del monolito. */
    public record SeguimientoMonolito(
            @NotNull @Positive Long id,
            @Positive Long pacienteId,
            @Positive Long medicoId,
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
            @NotNull @Positive Long id,
            @Positive Long pacienteId,
            @Positive Long medicoId,
            LocalDate fecha,
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            LocalDateTime fechaCreacion,
            String estado,
            @Positive Long informeOriginalId,
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
            @NotNull @Positive Long id,
            @Positive Long medicoId,
            @Positive Long pacienteId,
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
            @NotNull @Positive Long id,
            @Positive Long pacienteId,
            @Positive Long medicoId,
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
    private final RestClient httpRapido;
    private final ServiceJwt serviceJwt;

    private static final Consumer<List<HttpMessageConverter<?>>> ISO =
            c -> c.add(0, new MappingJackson2HttpMessageConverter(EventObjectMapperFactory.create()));

    // /internal/monolito o /internal/identidad (ver rutaIdentidad).
    private final String baseIdentidad;

    public ClienteInterno(@Value("${tranqui.gateway.url}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret}") String claveServicio,
                          @Value("${tranqui.fuente-identidad:monolito}") String fuenteIdentidad) {
        this.baseIdentidad = rutaIdentidad(fuenteIdentidad);
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(30_000);
        // Fechas en ISO como texto: sin esto un LocalDate del cuerpo sale como [1990,5,1] y el monolito lo rechaza.
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).messageConverters(ISO).build();

        SimpleClientHttpRequestFactory fabricaRapida = new SimpleClientHttpRequestFactory();
        fabricaRapida.setConnectTimeout(2_000);
        fabricaRapida.setReadTimeout(5_000);
        this.httpRapido = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabricaRapida).messageConverters(ISO).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    /**
     * De dónde se leen los datos de usuario (Fase 9): {@code monolito} (por defecto, hasta el corte) o
     * {@code identidad}. Volver atrás es sacar la variable {@code TRANQUI_FUENTE_IDENTIDAD}.
     */
    static String rutaIdentidad(String fuente) {
        String f = fuente == null ? "" : fuente.trim().toLowerCase();
        return switch (f) {
            case "", "monolito" -> "/internal/monolito";
            case "identidad" -> "/internal/identidad";
            default -> throw new IllegalStateException(
                    "TRANQUI_FUENTE_IDENTIDAD tiene que ser 'monolito' o 'identidad', no '" + fuente + "'.");
        };
    }

    /** Si los datos de usuario se leen del servicio de identidad (y lo que no es suyo, de su dueño). */
    public boolean fuenteIdentidad() {
        return "/internal/identidad".equals(baseIdentidad);
    }

    private <T> T get(String uri, ParameterizedTypeReference<T> tipo, Object... variables) {
        return ValidadorEventos.respuesta(http.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo), uri);
    }

    /** Carga inicial: los profesionales completos (con foto, perfil, disponibilidad y tarifas), paginados. */
    public List<ProfesionalMonolito> profesionales(int pagina, int tamanio) {
        List<ProfesionalMonolito> r = get("/internal/monolito/profesionales?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Un usuario completo (puede haber dejado de ser profesional: ver {@code rol}). */
    public ProfesionalMonolito profesional(Long id) {
        return get(baseIdentidad + "/profesionales/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    /** Identidad sin la foto, verificación, Mercado Pago y suscripción de todos los profesionales. */
    public List<ProfesionalMonolito> estado() {
        List<ProfesionalMonolito> r = get(baseIdentidad + "/profesionales/estado", new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }

    /** La parte de identidad del perfil. Si el monolito no responde, tira la excepción: no se guarda nada. */
    public ProfesionalMonolito guardarIdentidad(Long id, Identidad identidad) {
        return ValidadorEventos.respuesta(http.put().uri(baseIdentidad + "/profesionales/{id}/identidad", id)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(identidad)
                .retrieve().body(ProfesionalMonolito.class), baseIdentidad + "/profesionales/{id}/identidad");
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
        return ValidadorEventos.respuesta(http.put().uri(baseIdentidad + "/pacientes/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(body)
                .retrieve().body(PacienteMonolito.class), baseIdentidad + "/pacientes/{id}");
    }

    public PacienteMonolito paciente(Long id) {
        return get(baseIdentidad + "/pacientes/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    /** Todos los turnos, paginados por id (para carga inicial y resincronización periódica). */
    public List<TurnoMonolito> turnos(int pagina, int tamanio) {
        List<TurnoMonolito> r = get("/internal/monolito/turnos?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /**
     * Fase 9, con la fuente de identidad: los turnos salen de su dueño ({@code /internal/turnos/turnos}, la misma ruta
     * que usaba el monolito para su copia), sin paginar.
     */
    public List<TurnoServicio> turnosDelServicio() {
        List<TurnoServicio> r = get("/internal/turnos/turnos", new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }

    /** Fase 9, con la fuente de identidad: Mercado Pago vinculado de cada profesional, de pagos (su dueño). */
    public List<CuentaMp> cuentasMp(int pagina, int tamanio) {
        List<CuentaMp> r = get("/internal/pagos/cuentas-mp?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {},
                pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Fase 9, con la fuente de identidad: el estado de la suscripción de cada profesional, de pagos (su dueño). */
    public List<EstadoSuscripcion> estadoSuscripciones(int pagina, int tamanio) {
        List<EstadoSuscripcion> r = get("/internal/suscripciones/estado?pagina={p}&tamanio={t}",
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

    /** Payload para POST /internal/notificaciones/recetas (decisión 4). */
    public record NotificacionReceta(@Email String email, String pacienteNombre, String medicoNombre,
                                     String matricula, String pdfUrl, String nroRecetario) {}

    /**
     * Mail de la receta emitida al paciente (decisión 4). Timeouts cortos.
     * Si notificaciones está caído o responde error, tira excepción (el llamador la atrapa).
     */
    public void enviarMailReceta(NotificacionReceta pedido) {
        httpRapido.post().uri("/internal/notificaciones/recetas")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(pedido)
                .retrieve().toBodilessEntity();
    }
}
