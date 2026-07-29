package com.tranqui.app.model.dto;

import lombok.*;
import java.util.List;

/**
 * Request/response shapes for QBI2 Recipe's POST/DELETE /apirecipe/Receta.
 * Field names and structure come from the live Swagger spec at
 * apirecipe.hml.qbitos.com/swagger/v1/swagger.json cross-referenced with
 * Confluence space DQBI2. Some fields are best-effort/unconfirmed — see
 * inline notes. The API's authentication mechanism itself is undocumented —
 * see Qbi2RecipeClient.
 */
public class Qbi2RecetaDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecetaRequest {
        private Integer clienteAppId;
        private List<MedicamentoRequest> medicamentos;
        private String diagnostico;
        private Integer serviceType; // 1 or 2 — meaning not documented anywhere found
        private String fechaEmision; // ISO date-time UTC
        private String observaciones;
        private String indicaciones;
        private RecetaPosdatadaRequest recetasPostadatas; // if sent at all, cantidad+diasAPosdatar become mandatory
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
    public static class MedicamentoRequest {
        private String nombreProducto;
        private String nombreDroga;
        private String presentacion;
        private Integer cantidad;
        private String permiteSustitucion;
        private String regNo; // if null/empty, nombreProducto+nombreDroga+presentacion become mandatory
        private Integer tratamiento;
        private String diagnostico;
        private String codigoDiagnostico; // presumably the coddiagnostico value from GetDiagnostico — pairing inferred, not confirmed by docs
        private String posologia; // free text in the live schema; Confluence prose implies a separate "structured" mode but no confirmed sub-fields exist
        private String observaciones;
        private Boolean forzarDuplicado;
        private String promoId; // ties to ConsultaPromocion
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
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
    public static class PacienteReceta {
        private String apellido;
        private String nombre;
        private String tipoDoc; // DNI | Pasaporte | LE | LC | CI
        private String nroDoc;
        private String sexo; // F | M | X per Swagger; some Confluence pages also list O — unconfirmed which is authoritative
        private String fechaNacimiento; // AAAA-MM-DD
        private String cuil;
        private String localidad;
        private String provincia;
        private String pais; // mandatory if tipoDoc = Pasaporte
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
        private String firmalink; // signature image URL
        private String firmabase64; // signature image, base64 — alternative to firmalink
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
    public static class MatriculaDto {
        private String tipo; // MN | MP per Confluence prose — Swagger also showed a third value OP, unconfirmed which is authoritative
        private String numero;
        private String provincia; // mandatory if tipo = MP
        private String profesion;
        private String especialidad;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelloDto {
        private String linea1; // e.g. Dr. Juan Pérez — max 40 chars
        private String linea2; // e.g. specialty — max 40 chars
        private String linea3; // e.g. MN 12345 — max 25 chars
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CoberturaDto {
        private String idFinanciador; // prioritized over nroFinanciador if both present
        private String plan;
        private Integer planId; // prioritized over plan name if both present
        private String numero; // affiliate/credential number
        private String dniTitular; // if the plan holder differs from the patient
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
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
        private String direccion; // alternative single-field address entry
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
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
    public static class InformacionExtraDto {
        private String key;
        private String value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecetaResponse {
        private List<RecetaResult> recetas;
        private List<MedicamentoError> errores; // per-medication validation errors — a request can partially succeed
        private String idTransaccion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecetaResult {
        private String id;
        private String idReceta; // which of id/idReceta is "the hash" used later by Anular Receta / S3Link is NOT confirmed by any source — verify against a real response before wiring Anular
        private String fecha;
        private List<String> nroCUIR; // controlled-substance/duplicate registry number
        private String s3Link; // direct PDF link — the creation response can include this directly, no separate S3Link call needed in the common case
        private String verificador; // verification code for the PDF's QR
        private String linkECommerce;
        private String fechavencimiento; // relevant to whether Anular Receta will still succeed — QBI79 = expired
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicamentoError {
        private String error;
        private String mensaje;
        private List<String> medicamento;
    }
}
