package com.tranqui.gateway.admin;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.health.model.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Servicio que consulta el estado de salud y registro de los microservicios en Consul
 * para el panel de administración (/api/admin/salud) según sección 4.4 y 11.2 del plan.
 */
@Service
public class ConsulSaludService {

    private static final Logger log = LoggerFactory.getLogger(ConsulSaludService.class);

    public static final List<String> SERVICIOS_ESPERADOS = List.of(
            "gateway",
            "backend",
            "identidad",
            "profesionales",
            "turnos",
            "pagos",
            // Suscripciones vive en el servicio de pagos (Fase 8, decisión 1): no es un servicio aparte en Consul
            "notificaciones",
            "chat",
            "calendario"
    );

    private final ObjectProvider<ConsulClient> consulClientProvider;

    public ConsulSaludService(ObjectProvider<ConsulClient> consulClientProvider) {
        this.consulClientProvider = consulClientProvider;
    }

    public Map<String, Object> obtenerEstadoServicios() {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        ConsulClient consulClient = consulClientProvider.getIfAvailable();

        List<Map<String, Object>> listaServicios = new ArrayList<>();
        boolean consulOk = false;

        if (consulClient != null) {
            try {
                Map<String, List<String>> catalogServices = consulClient.getCatalogServices(QueryParams.DEFAULT).getValue();
                consulOk = true;

                Set<String> serviciosRegistrados = catalogServices != null ? new HashSet<>(catalogServices.keySet()) : Collections.emptySet();
                serviciosRegistrados.remove("consul");

                Set<String> todos = new LinkedHashSet<>(SERVICIOS_ESPERADOS);
                todos.addAll(serviciosRegistrados);

                for (String servicio : todos) {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("nombre", servicio);

                    if (serviciosRegistrados.contains(servicio)) {
                        List<HealthService> healthServices = consulClient.getHealthServices(servicio, false, QueryParams.DEFAULT).getValue();
                        info.put("instancias", healthServices != null ? healthServices.size() : 0);

                        String version = "0.0.1-SNAPSHOT";
                        String estado = "UP";

                        List<Map<String, Object>> instanciasDetalle = new ArrayList<>();
                        if (healthServices != null && !healthServices.isEmpty()) {
                            for (HealthService hs : healthServices) {
                                Map<String, Object> inst = new LinkedHashMap<>();
                                inst.put("id", hs.getService().getId());
                                inst.put("host", hs.getService().getAddress());
                                inst.put("puerto", hs.getService().getPort());

                                if (hs.getService().getTags() != null) {
                                    for (String tag : hs.getService().getTags()) {
                                        if (tag.startsWith("version=")) {
                                            version = tag.substring(8);
                                        }
                                    }
                                }
                                if (hs.getService().getMeta() != null && hs.getService().getMeta().containsKey("version")) {
                                    version = hs.getService().getMeta().get("version");
                                }

                                boolean anyCritical = false;
                                boolean anyWarning = false;
                                if (hs.getChecks() != null) {
                                    for (Check check : hs.getChecks()) {
                                        if (Check.CheckStatus.CRITICAL.equals(check.getStatus())) {
                                            anyCritical = true;
                                        } else if (Check.CheckStatus.WARNING.equals(check.getStatus())) {
                                            anyWarning = true;
                                        }
                                    }
                                }

                                String instEstado = anyCritical ? "DOWN" : (anyWarning ? "WARNING" : "UP");
                                inst.put("estado", instEstado);
                                instanciasDetalle.add(inst);

                                if (anyCritical) {
                                    estado = "DEGRADADO";
                                }
                            }
                        } else {
                            estado = "DOWN";
                        }

                        info.put("version", version);
                        info.put("estado", estado);
                        info.put("detalles", instanciasDetalle);
                    } else {
                        info.put("instancias", 0);
                        info.put("version", "-");
                        info.put("estado", "NO_REGISTRADO");
                        info.put("detalles", Collections.emptyList());
                    }

                    listaServicios.add(info);
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener información de servicios desde Consul: {}", e.getMessage());
                consulOk = false;
            }
        }

        if (!consulOk) {
            for (String servicio : SERVICIOS_ESPERADOS) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("nombre", servicio);
                info.put("instancias", servicio.equals("gateway") ? 1 : 0);
                info.put("version", servicio.equals("gateway") ? "0.0.1-SNAPSHOT" : "-");
                info.put("estado", servicio.equals("gateway") ? "UP" : "DESCONOCIDO");
                info.put("detalles", Collections.emptyList());
                listaServicios.add(info);
            }
        }

        respuesta.put("consulConectado", consulOk);
        respuesta.put("servicios", listaServicios);
        respuesta.put("timestamp", Instant.now().toString());

        return respuesta;
    }
}
