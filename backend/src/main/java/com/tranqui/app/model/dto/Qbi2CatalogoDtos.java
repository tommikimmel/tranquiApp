package com.tranqui.app.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

/**
 * Response shapes for QBI2 Recipe's catalog search endpoints: GetDiagnostico,
 * GetMedicamento and GetFinanciadores.
 */
public class Qbi2CatalogoDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiagnosticoResponse {
        private List<DiagnosticoItem> diagnosticos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiagnosticoItem {
        private Integer iddiagnostico;
        private String coddiagnostico; // CIE-10 code, e.g. B010
        private String descdiagnostico;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoResponse {
        private List<MedicamentoItem> medicamentos;
        private PageInfo pageInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoItem {
        private String presentacion;
        private String nombreProducto;
        private String nombreDroga;
        private String regNo;
        private Boolean tieneCobertura;
        private Boolean requiereAprobacion;
        private Float descuento;
        private Boolean psicofarmaco;
        private Boolean estupefaciente;
        private Boolean ventaControlada;
        private Boolean hiv;
        private Boolean requiereDuplicado;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private Integer numeroPagina;
        private Integer cantidadPaginas;
        private Integer cantidadMaxResultadosXPagina;
        private Boolean tieneMasResultados;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinanciadorResponse {
        private List<FinanciadorItem> financiadores;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinanciadorItem {
        private Integer idfinanciador;
        private String nrofinanciador;
        private String nombreComercial;
        private List<PlanItem> planes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanItem {
        private Integer id;
        private String nombre;
    }
}

