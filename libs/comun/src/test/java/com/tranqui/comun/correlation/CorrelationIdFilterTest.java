package com.tranqui.comun.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_sinEncabezado_generaNuevoUuidYLoPoneEnMdcYRespuesta() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        String generatedId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generatedId).isNotNull().isNotBlank();
        // Validar que sea un UUID válido
        assertThat(UUID.fromString(generatedId)).isNotNull();
        assertThat(mdcDuringExecution.get()).isEqualTo(generatedId);
        // Al terminar, el MDC debe quedar limpio
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_conEncabezadoExistente_preservaElCorrelationId() throws ServletException, IOException {
        String existingId = "custom-corr-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringExecution = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringExecution.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(existingId);
        assertThat(mdcDuringExecution.get()).isEqualTo(existingId);
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_cuandoCadenaLanzaExcepcion_limpiaMdcIgual() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Simulated error");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
        }

        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void correlationIdHolder_devuelveValorCorrecto() {
        assertThat(CorrelationIdHolder.getCorrelationId()).isNull();

        String sampleId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, sampleId);
        assertThat(CorrelationIdHolder.getCorrelationId()).isEqualTo(sampleId);

        MDC.clear();
        String generated = CorrelationIdHolder.getOrGenerateCorrelationId();
        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated)).isNotNull();
    }
}
