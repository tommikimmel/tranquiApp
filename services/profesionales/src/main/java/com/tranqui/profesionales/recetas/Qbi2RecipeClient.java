package com.tranqui.profesionales.recetas;

/**
 * Cliente para QBI2 Recipe (Innovamed), la API externa de recetas electrónicas oficial.
 * Dos implementaciones: {@link Qbi2RecipeClientMock} (cuando {@code qbi2.recipe.enabled=false}, por defecto)
 * y {@link Qbi2RecipeClientHttp} (llamadas HTTP reales, activa cuando {@code qbi2.recipe.enabled=true}).
 */
public interface Qbi2RecipeClient {

    Qbi2CatalogoDtos.DiagnosticoResponse buscarDiagnosticos(String texto);

    Qbi2CatalogoDtos.MedicamentoResponse buscarMedicamentos(String texto, int numeroPagina);

    Qbi2CatalogoDtos.FinanciadorResponse buscarFinanciadores();

    Qbi2RecetaDtos.RecetaResponse generarReceta(Qbi2RecetaDtos.RecetaRequest request);

    Qbi2AnularDtos.FarmalinkResult anularReceta(String hash);
}
