package com.tranqui.app.service;

import com.tranqui.app.model.dto.Qbi2AnularDtos;
import com.tranqui.app.model.dto.Qbi2CatalogoDtos;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;

/**
 * Client for QBI2 Recipe (Innovamed), the external electronic-prescription
 * API that will eventually replace TranquiApp's internal simulated Receta
 * flow. Two implementations exist: {@link Qbi2RecipeClientMock} (default —
 * no real credentials exist yet) and {@link Qbi2RecipeClientHttp} (real HTTP
 * calls, activated via {@code qbi2.recipe.enabled=true}). IMPORTANT: QBI2's
 * authentication mechanism (how a Bearer token is obtained) is not
 * documented anywhere in their public Confluence space or Swagger spec as of
 * this writing — resolve that with Innovamed (soporte.it@innovamed.com.ar)
 * before flipping {@code qbi2.recipe.enabled} on; until then,
 * Qbi2RecipeClientHttp is unverified/untested code.
 */
public interface Qbi2RecipeClient {

    Qbi2CatalogoDtos.DiagnosticoResponse buscarDiagnosticos(String texto);

    Qbi2CatalogoDtos.MedicamentoResponse buscarMedicamentos(String texto, int numeroPagina);

    Qbi2CatalogoDtos.FinanciadorResponse buscarFinanciadores();

    Qbi2RecetaDtos.RecetaResponse generarReceta(Qbi2RecetaDtos.RecetaRequest request);

    Qbi2AnularDtos.FarmalinkResult anularReceta(String hash);
}
