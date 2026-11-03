package com.tranqui.profesionales.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.profesionales.datos.Profesionales;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Llamadas de profesionales al monolito, siempre por el gateway con un JWT de servicio (sección 4.2):
 * la carga inicial, la sincronización de identidad, verificación y suscripción, y la parte de identidad de
 * {@code PUT /api/medicos/perfil}.
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
}
