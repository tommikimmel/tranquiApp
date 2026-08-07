package com.tranqui.app.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

/**
 * Request/response shapes for QBI2 Recipe's POST/DELETE /apirecipe/Receta.
 * Field names and structure come from the live Swagger spec at
 * apirecipe.hml.qbitos.com/swagger/v1/swagger.json cross-referenced with
 * Confluence space DQBI2.
 */
public class Qbi2RecetaDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaRequest {
        private Integer clienteAppId;
        private List<MedicamentoRequest> medicamentos;
        private String diagnostico;
        private Integer serviceType; // 1 or 2
        private String fechaEmision; // ISO date-time UTC
        private String observaciones;
        private String indicaciones;
        private RecetaPosdatadaRequest recetasPostadatas;
        private PacienteReceta paciente;
        private MedicoReceta medico;
        private SubemisorDto subemisor;
        private LugarAtencionDto lugarAtencion;
        private List<InformacionExtraDto> informacionExtra;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoRequest {
        private String nombreProducto;
        private String nombreDroga;
        private String presentacion;
        private Integer cantidad;
        private String permiteSustitucion;
        private String regNo;
        private Integer tratamiento;
        private String diagnostico;
        private String codigoDiagnostico;
        private String posologia;
        private String observaciones;
        private Boolean forzarDuplicado;
        private String promoId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaPosdatadaRequest {
        private Integer cantidad;
        private Integer diasAPosdatar;
        private List<String> fechas;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PacienteReceta {
        private String apellido;
        private String nombre;
        private String tipoDoc; // DNI | Pasaporte | LE | LC | CI
        private String nroDoc;
        private String sexo; // F | M | X
        private String fechaNacimiento; // AAAA-MM-DD
        private String cuil;
        private String localidad;
        private String provincia;
        private String pais;
        private String email;
        private String telefono;
        private Boolean ocultarPaciente;
        private CoberturaDto cobertura;
        private DomicilioDto domicilio;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicoReceta {
        private String apellido;
        private String nombre;
        private String tipoDoc;
        private String nroDoc;
        private String especialidad;
        private String sexo;
        private String fechaNacimiento;
        private String email;
        private String telefono;
        private String pais;
        private SelloDto sello;
        private String logoInstitucion;
        private MatriculaDto matricula;
        private String idTributario;
        private String profesion;
        private String idREFEPS;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatriculaDto {
        private String tipo; // MN | MP
        private String numero;
        private String provincia;
        private String profesion;
        private String especialidad;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SelloDto {
        private String linea1; // e.g. Dr. Juan Pérez — max 40 chars
        private String linea2; // e.g. especialidad — max 40 chars
        private String linea3; // e.g. MN 12345 — max 25 chars
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoberturaDto {
        private String idFinanciador;
        private String plan;
        private Integer planId;
        private String numero;
        private String dniTitular;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomicilioDto {
        private String calle;
        private String numero;
        private String piso;
        private String dpto;
        private String codigoPostal;
        private String localidad;
        private String municipio;
        private String provincia;
        private String pais;
        private String observacion;
        private String direccion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubemisorDto {
        private String nombre;
        private String cuit;
        private String direccion;
        private String logoLink;
        private String logoBase64;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LugarAtencionDto {
        private String nombreConsultorio;
        private DomicilioDto domicilio;
        private String datosContacto;
        private String email;
        private String logo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InformacionExtraDto {
        private String key;
        private String value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaResponse {
        // Real shape is ApiRecipe.UseCases.NewReceta.RecetaPdfResponse (per the live swagger):
        // "recetas" only carries the PDF/CUIR data (Core.Dtos.RecetaPdfResponseDto — no
        // fechavencimiento/status there). Those two live in the separate "response" array
        // (Core.Dtos.RecetaResponseDto), which we weren't reading at all before — every receta
        // was persisted locally with qbi2FechaVencimiento/status silently null.
        private List<RecetaResult> recetas;
        private List<MedicamentoError> errores;
        private List<RecetaDetalle> response;
        private String idTransaccion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaResult {
        private String id;
        private String idReceta;
        private String fecha;
        private List<String> nroCUIR;
        private String s3Link;
        private String verificador;
        private String linkECommerce;
    }

    // Subset of Core.Dtos.RecetaResponseDto — only the fields RecetaService actually reads.
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaDetalle {
        private String fecha;
        private String fechavencimiento;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoError {
        private String error;
        private String mensaje;
        private List<String> medicamento;
    }
}

