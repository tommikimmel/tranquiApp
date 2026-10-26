package com.tranqui.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// VictoriaMetrics lee /actuator/prometheus del puerto de management sin sesion. Con el gate del
// sitio activo (site.access.password) tiene que seguir respondiendo, y el puerto publico no debe
// exponer el actuator.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// El application.yml de test reemplaza al principal, asi que se replica aca la parte de management.
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "management.prometheus.metrics.export.enabled=true"
})
class ActuatorPrometheusTest {

    @LocalManagementPort
    private int puertoManagement;

    @LocalServerPort
    private int puertoPublico;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void prometheus_enPuertoDeManagement_respondeSinSesionNiGate() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "http://localhost:" + puertoManagement + "/actuator/prometheus", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void actuator_noSeExponeEnElPuertoPublico() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "http://localhost:" + puertoPublico + "/actuator/prometheus", String.class);

        assertThat(respuesta.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
}
