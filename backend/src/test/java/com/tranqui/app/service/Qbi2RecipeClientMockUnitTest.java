package com.tranqui.app.service;

import com.tranqui.app.model.dto.Qbi2AnularDtos;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Qbi2RecipeClientMockUnitTest {

    private final Qbi2RecipeClientMock client = new Qbi2RecipeClientMock();

    @Test
    void testMockMethods() {
        Qbi2CatalogoDtos.DiagnosticoResponse diag = client.buscarDiagnosticos("ansiedad");
        assertNotNull(diag);
        assertFalse(diag.getDiagnosticos().isEmpty());

        Qbi2CatalogoDtos.MedicamentoResponse med = client.buscarMedicamentos("clona", 1);
        assertNotNull(med);
        assertFalse(med.getMedicamentos().isEmpty());

        Qbi2CatalogoDtos.FinanciadorResponse fin = client.buscarFinanciadores();
        assertNotNull(fin);
        assertFalse(fin.getFinanciadores().isEmpty());

        Qbi2RecetaDtos.RecetaRequest req = Qbi2RecetaDtos.RecetaRequest.builder().build();
        Qbi2RecetaDtos.RecetaResponse receta = client.generarReceta(req);
        assertNotNull(receta);
        assertFalse(receta.getRecetas().isEmpty());

        Qbi2AnularDtos.FarmalinkResult anular = client.anularReceta("hash-123");
        assertNotNull(anular);
        assertEquals(0, anular.getCodigoRespuesta());
    }

    @Test
    void testHttpClientMethods() {
        Qbi2RecipeClientHttp httpClient = new Qbi2RecipeClientHttp();
        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "environment", "prod");
        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "baseUrlProd", "https://prod.qbi2.com");
        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "baseUrlHml", "https://hml.qbi2.com");
        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "token", "12345678901234567890");
        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "clienteAppId", "123");

        assertEquals("https://prod.qbi2.com", org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "baseUrl"));

        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "environment", "hml");
        assertEquals("https://hml.qbi2.com", org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "baseUrl"));

        String masked = org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "maskedToken");
        assertTrue(masked.contains("…"));

        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "token", "short");
        assertEquals("***", org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "maskedToken"));

        org.springframework.test.util.ReflectionTestUtils.setField(httpClient, "token", "");
        assertEquals("(sin token)", org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "maskedToken"));

        String encoded = org.springframework.test.util.ReflectionTestUtils.invokeMethod(httpClient, "urlEncode", "hola mundo");
        assertEquals("hola+mundo", encoded);

        assertThrows(RuntimeException.class, () -> httpClient.buscarDiagnosticos("ansiedad"));
        assertThrows(RuntimeException.class, () -> httpClient.buscarMedicamentos("clona", 1));
        assertThrows(RuntimeException.class, () -> httpClient.buscarFinanciadores());
        assertThrows(RuntimeException.class, () -> httpClient.generarReceta(Qbi2RecetaDtos.RecetaRequest.builder().build()));
        assertThrows(RuntimeException.class, () -> httpClient.anularReceta("hash-123"));
    }
}
