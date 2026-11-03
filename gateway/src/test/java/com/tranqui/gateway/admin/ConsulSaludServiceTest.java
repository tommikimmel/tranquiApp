package com.tranqui.gateway.admin;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.health.model.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Estado de los servicios para /api/admin/salud a partir del catálogo y los health checks de Consul. */
class ConsulSaludServiceTest {

    private final ConsulClient consul = mock(ConsulClient.class);

    @SuppressWarnings("unchecked")
    private ConsulSaludService service(ConsulClient cliente) {
        ObjectProvider<ConsulClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cliente);
        return new ConsulSaludService(provider);
    }

    private static HealthService instancia(String id, List<String> tags, Map<String, String> meta, Check.CheckStatus... estados) {
        HealthService.Service s = new HealthService.Service();
        s.setId(id);
        s.setAddress("10.0.0.5");
        s.setPort(8080);
        s.setTags(tags);
        s.setMeta(meta);
        HealthService hs = new HealthService();
        hs.setService(s);
        hs.setChecks(java.util.Arrays.stream(estados).map(e -> {
            Check c = new Check();
            c.setStatus(e);
            return c;
        }).toList());
        return hs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicio(Map<String, Object> respuesta, String nombre) {
        return ((List<Map<String, Object>>) respuesta.get("servicios")).stream()
                .filter(s -> nombre.equals(s.get("nombre"))).findFirst().orElseThrow();
    }

    @Test
    void conConsul_calculaEstadoYVersionPorServicio() {
        when(consul.getCatalogServices(QueryParams.DEFAULT)).thenReturn(new Response<>(Map.of(
                "consul", List.of(), "identidad", List.of(), "pagos", List.of(), "turnos", List.of(), "chat", List.of(), "extra", List.of()),
                1L, true, 1L));
        when(consul.getHealthServices(eq("identidad"), anyBoolean(), any(QueryParams.class))).thenReturn(new Response<>(List.of(
                instancia("identidad-1", List.of("version=1.2.0"), null, Check.CheckStatus.PASSING),
                instancia("identidad-2", null, Map.of("version", "1.3.0"), Check.CheckStatus.WARNING)), 1L, true, 1L));
        when(consul.getHealthServices(eq("pagos"), anyBoolean(), any(QueryParams.class))).thenReturn(new Response<>(List.of(
                instancia("pagos-1", List.of("otro"), Map.of(), Check.CheckStatus.CRITICAL)), 1L, true, 1L));
        when(consul.getHealthServices(eq("turnos"), anyBoolean(), any(QueryParams.class))).thenReturn(new Response<>(List.of(), 1L, true, 1L));
        when(consul.getHealthServices(eq("chat"), anyBoolean(), any(QueryParams.class))).thenReturn(new Response<>(null, 1L, true, 1L));
        when(consul.getHealthServices(eq("extra"), anyBoolean(), any(QueryParams.class))).thenReturn(new Response<>(List.of(
                instancia("extra-1", null, null)), 1L, true, 1L));

        Map<String, Object> r = service(consul).obtenerEstadoServicios();

        assertThat(r.get("consulConectado")).isEqualTo(true);
        Map<String, Object> identidad = servicio(r, "identidad");
        assertThat(identidad.get("instancias")).isEqualTo(2);
        assertThat(identidad.get("version")).isEqualTo("1.3.0");
        assertThat(identidad.get("estado")).isEqualTo("UP");
        assertThat((List<?>) identidad.get("detalles")).hasSize(2);
        assertThat(servicio(r, "pagos").get("estado")).isEqualTo("DEGRADADO");
        assertThat(servicio(r, "turnos").get("estado")).isEqualTo("DOWN");
        assertThat(servicio(r, "chat").get("instancias")).isEqualTo(0);
        assertThat(servicio(r, "extra").get("estado")).isEqualTo("UP");
        assertThat(servicio(r, "calendario").get("estado")).isEqualTo("NO_REGISTRADO");
        assertThat(((List<?>) r.get("servicios")).stream().noneMatch(s -> "consul".equals(((Map<?, ?>) s).get("nombre")))).isTrue();
    }

    @Test
    void catalogoVacio_todosNoRegistrados() {
        when(consul.getCatalogServices(QueryParams.DEFAULT)).thenReturn(new Response<>(null, 1L, true, 1L));
        Map<String, Object> r = service(consul).obtenerEstadoServicios();
        assertThat(r.get("consulConectado")).isEqualTo(true);
        assertThat(servicio(r, "gateway").get("estado")).isEqualTo("NO_REGISTRADO");
    }

    @Test
    void consulCaidoOAusente_respuestaPorDefecto() {
        when(consul.getCatalogServices(QueryParams.DEFAULT)).thenThrow(new IllegalStateException("sin conexión"));
        for (ConsulClient cliente : new ConsulClient[]{consul, null}) {
            Map<String, Object> r = service(cliente).obtenerEstadoServicios();
            assertThat(r.get("consulConectado")).isEqualTo(false);
            assertThat(servicio(r, "gateway").get("estado")).isEqualTo("UP");
            assertThat(servicio(r, "pagos").get("estado")).isEqualTo("DESCONOCIDO");
            assertThat(r).containsKey("timestamp");
        }
    }
}
