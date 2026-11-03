package com.tranqui.turnos.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.turnos.datos.Personas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Llamadas de turnos a otros servicios, siempre por el gateway con un JWT de servicio (sección 4.2): al
 * monolito (pacientes, "buscar o crear paciente", carga inicial y profesionales), al calendario (horarios
 * ocupados) y a notificaciones (el documento adjunto).
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "turnos";

    /** {@code InternoTurnosController.PacienteDto} del monolito. */
    public record PacienteMonolito(Long id, int version, String rol, String email, String nombre, String apellido,
                                   String telefono, String dni, String direccion, String obraSocial, String numAfiliado,
                                   String sexo, LocalDate fechaNacimiento, Long cuil, String tipoDocumento,
                                   Integer numeroDocumento, String datosOfuscado, Integer credencialCodEntidad,
                                   String credencialPan, String credencialPlan, String credencialToken, boolean emailHabilitado) {

        public Personas.Persona persona(boolean mpVinculada) {
            return new Personas.Persona(id, version, rol, email, nombre, apellido, telefono, dni, direccion, obraSocial,
                    numAfiliado, sexo, fechaNacimiento, cuil, tipoDocumento, numeroDocumento, datosOfuscado,
                    credencialCodEntidad, credencialPan, credencialPlan, credencialToken, emailHabilitado, mpVinculada);
        }
    }

    public record BuscarOCrearPaciente(String email, String nombre, String telefono, String obraSocial, String numAfiliado) {}

    /** {@code InternoTurnosController.TurnoDto} del monolito (carga inicial). */
    public record TurnoMonolito(int version, TurnoActualizadoData turno, String tokenReserva, String tokenConfirmacionAsistencia) {}

    /** Lo que turnos usa de {@code InternoProfesionalesController.ProfesionalDto} del monolito. */
    public record ProfesionalMonolito(Long id, String rol, boolean mpVinculada, PerfilProfesionalData perfil,
                                      List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                      List<TarifasProfesionalData.Tarifa> tarifas) {}

    /** {@code InternoController.ProfesionalServicio} del servicio de profesionales. */
    public record ProfesionalServicio(Long profesionalId, int version, PerfilProfesionalData perfil,
                                      List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                      List<TarifasProfesionalData.Tarifa> tarifas) {}

    /** {@code CuentasMp.CuentaMpResumenDto} del servicio de pagos. */
    public record CuentaMp(Long profesionalId, boolean vinculada, long version) {}

    /** Un intervalo ocupado en el Google Calendar del profesional; {@code todoElDia} bloquea el día. */
    public record Intervalo(LocalTime inicio, LocalTime fin, boolean todoElDia) {}

    public record DocumentoAdjunto(String email, String pacienteNombre, String medicoNombre, String tipoDocumento,
                                   String archivoDataUri, String archivoNombre) {}

    private final RestClient http;
    private final RestClient rapido;
    private final ServiceJwt serviceJwt;

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
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        // Lo que se pide mientras el paciente espera la agenda: si el calendario tarda, se ofrece sin ese filtro.
        SimpleClientHttpRequestFactory corta = new SimpleClientHttpRequestFactory();
        corta.setConnectTimeout(2_000);
        corta.setReadTimeout(3_000);
        this.rapido = RestClient.builder().baseUrl(gatewayUrl).requestFactory(corta).build();
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

    private <T> T get(RestClient cliente, String uri, ParameterizedTypeReference<T> tipo, Object... variables) {
        return cliente.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
    }

    private <T> T post(String uri, Object cuerpo, Class<T> tipo) {
        return http.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(cuerpo)
                .retrieve().body(tipo);
    }

    public List<PacienteMonolito> pacientes(int pagina, int tamanio) {
        List<PacienteMonolito> r = get(http, "/internal/monolito/pacientes?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {},
                pagina, tamanio);
        return r != null ? r : List.of();
    }

    public PacienteMonolito paciente(Long id) {
        return get(http, baseIdentidad + "/pacientes/{id}", new ParameterizedTypeReference<>() {}, id);
    }

    /** "Buscar o crear paciente" de la reserva. Si el monolito no responde, tira la excepción. */
    public PacienteMonolito buscarOCrearPaciente(BuscarOCrearPaciente pedido) {
        return post(baseIdentidad + "/pacientes", pedido, PacienteMonolito.class);
    }

    public List<TurnoMonolito> turnos(int pagina, int tamanio) {
        List<TurnoMonolito> r = get(http, "/internal/monolito/turnos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {},
                pagina, tamanio);
        return r != null ? r : List.of();
    }

    /** Los profesionales con su agenda, sin la foto (la copia del monolito está al día con o sin profesionales). */
    public List<ProfesionalMonolito> profesionales(int pagina, int tamanio) {
        List<ProfesionalMonolito> r = get(http, "/internal/monolito/profesionales?pagina={p}&tamanio={t}&foto=false",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
        return r != null ? r : List.of();
    }

    /**
     * Fase 9, con la fuente de identidad: la agenda, la disponibilidad y las tarifas de los profesionales salen de su
     * dueño ({@code /internal/profesionales/profesionales}, la misma que usaba el monolito para su copia).
     */
    public List<ProfesionalServicio> profesionalesDelServicio() {
        List<ProfesionalServicio> r = get(http, "/internal/profesionales/profesionales", new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }

    /** Fase 9, con la fuente de identidad: si cada profesional tiene Mercado Pago vinculado, de pagos (su dueño). */
    public List<CuentaMp> cuentasMp(int pagina, int tamanio) {
        List<CuentaMp> r = get(http, "/internal/pagos/cuentas-mp?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {},
                pagina, tamanio);
        return r != null ? r : List.of();
    }

    public List<Intervalo> ocupados(Long profesionalId, LocalDate fecha) {
        List<Intervalo> r = get(rapido, "/internal/calendario/profesionales/{id}/ocupado?fecha={fecha}", new ParameterizedTypeReference<>() {},
                profesionalId, fecha.toString());
        return r != null ? r : List.of();
    }

    /** El mail con el documento adjunto (decisión 3). Tira la excepción si notificaciones no lo pudo mandar. */
    public void enviarDocumento(DocumentoAdjunto documento) {
        post("/internal/notificaciones/documentos", documento, Void.class);
    }
}
