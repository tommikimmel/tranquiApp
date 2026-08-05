package com.tranqui.app.service;

import com.tranqui.app.model.dto.Qbi2AnularDtos;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;

/**
 * Client for QBI2 Recipe (Innovamed), the external electronic-prescription
 * API that replaces TranquiApp's internal simulated Receta flow. Two
 * implementations exist: {@link Qbi2RecipeClientMock} (used when
 * {@code qbi2.recipe.enabled=false}) and {@link Qbi2RecipeClientHttp} (real
 * HTTP calls, activated via {@code qbi2.recipe.enabled=true}, currently the
 * default in {@code .env}). Verified against the real HML environment on
 * 2026-08-05: POST /apirecipe/Receta returns 200 with a valid
 * s3Link/verificador/idReceta/nroCUIR for a particular (no financiador) case.
 */
public interface Qbi2RecipeClient {

    Qbi2CatalogoDtos.DiagnosticoResponse buscarDiagnosticos(String texto);

    Qbi2CatalogoDtos.MedicamentoResponse buscarMedicamentos(String texto, int numeroPagina);

    Qbi2CatalogoDtos.FinanciadorResponse buscarFinanciadores();

    Qbi2RecetaDtos.RecetaResponse generarReceta(Qbi2RecetaDtos.RecetaRequest request);

    Qbi2AnularDtos.FarmalinkResult anularReceta(String hash);
}
