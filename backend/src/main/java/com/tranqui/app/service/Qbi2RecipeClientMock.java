package com.tranqui.app.service;

import com.tranqui.app.model.dto.Qbi2AnularDtos;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Default {@link Qbi2RecipeClient} implementation, active whenever
 * qbi2.recipe.enabled is false (the default, since we have no real QBI2
 * credentials yet). Returns small, obviously-fake canned responses so the
 * rest of the app can be developed/tested against this integration without
 * ever touching the real QBI2 API.
 */
@Service
@ConditionalOnProperty(prefix = "qbi2.recipe", name = "enabled", havingValue = "false", matchIfMissing = true)
public class Qbi2RecipeClientMock implements Qbi2RecipeClient {

    private static final Logger log = LoggerFactory.getLogger(Qbi2RecipeClientMock.class);

    @Override
    public Qbi2CatalogoDtos.DiagnosticoResponse buscarDiagnosticos(String texto) {
        log.warn("Qbi2RecipeClientMock.buscarDiagnosticos('{}') — QBI2 real no está configurado, devolviendo datos simulados", texto);
        Qbi2CatalogoDtos.DiagnosticoItem item = Qbi2CatalogoDtos.DiagnosticoItem.builder()
                .iddiagnostico(1)
                .coddiagnostico("F410")
                .descdiagnostico("[MOCK] Trastorno de ansiedad generalizada")
                .build();
        return Qbi2CatalogoDtos.DiagnosticoResponse.builder()
                .diagnosticos(List.of(item))
                .build();
    }

    @Override
    public Qbi2CatalogoDtos.MedicamentoResponse buscarMedicamentos(String texto, int numeroPagina) {
        log.warn("Qbi2RecipeClientMock.buscarMedicamentos('{}', {}) — QBI2 real no está configurado, devolviendo datos simulados", texto, numeroPagina);
        Qbi2CatalogoDtos.MedicamentoItem item = Qbi2CatalogoDtos.MedicamentoItem.builder()
                .presentacion("[MOCK] Comprimidos x 30")
                .nombreProducto("[MOCK] Producto de prueba")
                .nombreDroga("[MOCK] Droga de prueba")
                .regNo("MOCK-000000")
                .tieneCobertura(false)
                .requiereAprobacion(false)
                .descuento(0f)
                .psicofarmaco(false)
                .estupefaciente(false)
                .ventaControlada(false)
                .hiv(false)
                .requiereDuplicado(false)
                .build();
        Qbi2CatalogoDtos.PageInfo pageInfo = Qbi2CatalogoDtos.PageInfo.builder()
                .numeroPagina(numeroPagina)
                .cantidadPaginas(1)
                .cantidadMaxResultadosXPagina(20)
                .tieneMasResultados(false)
                .build();
        return Qbi2CatalogoDtos.MedicamentoResponse.builder()
                .medicamentos(List.of(item))
                .pageInfo(pageInfo)
                .build();
    }

    @Override
    public Qbi2CatalogoDtos.FinanciadorResponse buscarFinanciadores() {
        log.warn("Qbi2RecipeClientMock.buscarFinanciadores() — QBI2 real no está configurado, devolviendo datos simulados");
        Qbi2CatalogoDtos.FinanciadorItem item = Qbi2CatalogoDtos.FinanciadorItem.builder()
                .idfinanciador(1)
                .nrofinanciador("MOCK-1")
                .nombreComercial("[MOCK] Financiador de prueba")
                .planes(Collections.emptyList())
                .build();
        return Qbi2CatalogoDtos.FinanciadorResponse.builder()
                .financiadores(List.of(item))
                .build();
    }

    @Override
    public Qbi2RecetaDtos.RecetaResponse generarReceta(Qbi2RecetaDtos.RecetaRequest request) {
        log.warn("Qbi2RecipeClientMock.generarReceta() — QBI2 real no está configurado, simulando generación de receta");
        Qbi2RecetaDtos.RecetaResult result = Qbi2RecetaDtos.RecetaResult.builder()
                .id("MOCK-" + System.currentTimeMillis())
                .idReceta("MOCK-" + System.currentTimeMillis())
                .fecha(null)
                .nroCUIR(Collections.emptyList())
                .s3Link(null)
                .verificador("MOCK-VERIFICADOR")
                .linkECommerce(null)
                .fechavencimiento(null)
                .status("MOCK")
                .build();
        return Qbi2RecetaDtos.RecetaResponse.builder()
                .recetas(List.of(result))
                .errores(Collections.emptyList())
                .idTransaccion("MOCK-" + System.currentTimeMillis())
                .build();
    }

    @Override
    public Qbi2AnularDtos.FarmalinkResult anularReceta(String hash) {
        log.warn("Qbi2RecipeClientMock.anularReceta('{}') — QBI2 real no está configurado, simulando anulación", hash);
        return Qbi2AnularDtos.FarmalinkResult.builder()
                .codigoRespuesta(0)
                .mensaje("MOCK: anulación simulada, no se llamó a QBI2 real")
                .build();
    }
}
