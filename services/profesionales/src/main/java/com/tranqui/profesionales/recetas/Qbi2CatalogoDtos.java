package com.tranqui.profesionales.recetas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Formas de response para las búsquedas de catálogo de QBI2 Recipe: GetDiagnostico,
 * GetMedicamento y GetFinanciadores.
 */
public class Qbi2CatalogoDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiagnosticoResponse {
        private List<DiagnosticoItem> diagnosticos;

        public DiagnosticoResponse() {}
        public DiagnosticoResponse(List<DiagnosticoItem> diagnosticos) { this.diagnosticos = diagnosticos; }

        public List<DiagnosticoItem> getDiagnosticos() { return diagnosticos; }
        public void setDiagnosticos(List<DiagnosticoItem> diagnosticos) { this.diagnosticos = diagnosticos; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<DiagnosticoItem> diagnosticos;
            public Builder diagnosticos(List<DiagnosticoItem> v) { this.diagnosticos = v; return this; }
            public DiagnosticoResponse build() { return new DiagnosticoResponse(diagnosticos); }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiagnosticoItem {
        private Integer iddiagnostico;
        private String coddiagnostico;
        private String descdiagnostico;

        public DiagnosticoItem() {}
        public DiagnosticoItem(Integer iddiagnostico, String coddiagnostico, String descdiagnostico) {
            this.iddiagnostico = iddiagnostico;
            this.coddiagnostico = coddiagnostico;
            this.descdiagnostico = descdiagnostico;
        }

        public Integer getIddiagnostico() { return iddiagnostico; }
        public void setIddiagnostico(Integer iddiagnostico) { this.iddiagnostico = iddiagnostico; }
        public String getCoddiagnostico() { return coddiagnostico; }
        public void setCoddiagnostico(String coddiagnostico) { this.coddiagnostico = coddiagnostico; }
        public String getDescdiagnostico() { return descdiagnostico; }
        public void setDescdiagnostico(String descdiagnostico) { this.descdiagnostico = descdiagnostico; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer iddiagnostico;
            private String coddiagnostico;
            private String descdiagnostico;
            public Builder iddiagnostico(Integer v) { this.iddiagnostico = v; return this; }
            public Builder coddiagnostico(String v) { this.coddiagnostico = v; return this; }
            public Builder descdiagnostico(String v) { this.descdiagnostico = v; return this; }
            public DiagnosticoItem build() { return new DiagnosticoItem(iddiagnostico, coddiagnostico, descdiagnostico); }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoResponse {
        private List<MedicamentoItem> medicamentos;
        private PageInfo pageInfo;

        public MedicamentoResponse() {}
        public MedicamentoResponse(List<MedicamentoItem> medicamentos, PageInfo pageInfo) {
            this.medicamentos = medicamentos;
            this.pageInfo = pageInfo;
        }

        public List<MedicamentoItem> getMedicamentos() { return medicamentos; }
        public void setMedicamentos(List<MedicamentoItem> medicamentos) { this.medicamentos = medicamentos; }
        public PageInfo getPageInfo() { return pageInfo; }
        public void setPageInfo(PageInfo pageInfo) { this.pageInfo = pageInfo; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<MedicamentoItem> medicamentos;
            private PageInfo pageInfo;
            public Builder medicamentos(List<MedicamentoItem> v) { this.medicamentos = v; return this; }
            public Builder pageInfo(PageInfo v) { this.pageInfo = v; return this; }
            public MedicamentoResponse build() { return new MedicamentoResponse(medicamentos, pageInfo); }
        }
    }

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

        public MedicamentoItem() {}

        public String getPresentacion() { return presentacion; }
        public void setPresentacion(String presentacion) { this.presentacion = presentacion; }
        public String getNombreProducto() { return nombreProducto; }
        public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
        public String getNombreDroga() { return nombreDroga; }
        public void setNombreDroga(String nombreDroga) { this.nombreDroga = nombreDroga; }
        public String getRegNo() { return regNo; }
        public void setRegNo(String regNo) { this.regNo = regNo; }
        public Boolean getTieneCobertura() { return tieneCobertura; }
        public void setTieneCobertura(Boolean tieneCobertura) { this.tieneCobertura = tieneCobertura; }
        public Boolean getRequiereAprobacion() { return requiereAprobacion; }
        public void setRequiereAprobacion(Boolean requiereAprobacion) { this.requiereAprobacion = requiereAprobacion; }
        public Float getDescuento() { return descuento; }
        public void setDescuento(Float descuento) { this.descuento = descuento; }
        public Boolean getPsicofarmaco() { return psicofarmaco; }
        public void setPsicofarmaco(Boolean psicofarmaco) { this.psicofarmaco = psicofarmaco; }
        public Boolean getEstupefaciente() { return estupefaciente; }
        public void setEstupefaciente(Boolean estupefaciente) { this.estupefaciente = estupefaciente; }
        public Boolean getVentaControlada() { return ventaControlada; }
        public void setVentaControlada(Boolean ventaControlada) { this.ventaControlada = ventaControlada; }
        public Boolean getHiv() { return hiv; }
        public void setHiv(Boolean hiv) { this.hiv = hiv; }
        public Boolean getRequiereDuplicado() { return requiereDuplicado; }
        public void setRequiereDuplicado(Boolean requiereDuplicado) { this.requiereDuplicado = requiereDuplicado; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicamentoItem m = new MedicamentoItem();
            public Builder presentacion(String v) { m.presentacion = v; return this; }
            public Builder nombreProducto(String v) { m.nombreProducto = v; return this; }
            public Builder nombreDroga(String v) { m.nombreDroga = v; return this; }
            public Builder regNo(String v) { m.regNo = v; return this; }
            public Builder tieneCobertura(Boolean v) { m.tieneCobertura = v; return this; }
            public Builder requiereAprobacion(Boolean v) { m.requiereAprobacion = v; return this; }
            public Builder descuento(Float v) { m.descuento = v; return this; }
            public Builder psicofarmaco(Boolean v) { m.psicofarmaco = v; return this; }
            public Builder estupefaciente(Boolean v) { m.estupefaciente = v; return this; }
            public Builder ventaControlada(Boolean v) { m.ventaControlada = v; return this; }
            public Builder hiv(Boolean v) { m.hiv = v; return this; }
            public Builder requiereDuplicado(Boolean v) { m.requiereDuplicado = v; return this; }
            public MedicamentoItem build() { return m; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private Integer numeroPagina;
        private Integer cantidadPaginas;
        private Integer cantidadMaxResultadosXPagina;
        private Boolean tieneMasResultados;

        public PageInfo() {}

        public Integer getNumeroPagina() { return numeroPagina; }
        public void setNumeroPagina(Integer numeroPagina) { this.numeroPagina = numeroPagina; }
        public Integer getCantidadPaginas() { return cantidadPaginas; }
        public void setCantidadPaginas(Integer cantidadPaginas) { this.cantidadPaginas = cantidadPaginas; }
        public Integer getCantidadMaxResultadosXPagina() { return cantidadMaxResultadosXPagina; }
        public void setCantidadMaxResultadosXPagina(Integer cantidadMaxResultadosXPagina) { this.cantidadMaxResultadosXPagina = cantidadMaxResultadosXPagina; }
        public Boolean getTieneMasResultados() { return tieneMasResultados; }
        public void setTieneMasResultados(Boolean tieneMasResultados) { this.tieneMasResultados = tieneMasResultados; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final PageInfo p = new PageInfo();
            public Builder numeroPagina(Integer v) { p.numeroPagina = v; return this; }
            public Builder cantidadPaginas(Integer v) { p.cantidadPaginas = v; return this; }
            public Builder cantidadMaxResultadosXPagina(Integer v) { p.cantidadMaxResultadosXPagina = v; return this; }
            public Builder tieneMasResultados(Boolean v) { p.tieneMasResultados = v; return this; }
            public PageInfo build() { return p; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinanciadorResponse {
        private List<FinanciadorItem> financiadores;

        public FinanciadorResponse() {}
        public FinanciadorResponse(List<FinanciadorItem> financiadores) { this.financiadores = financiadores; }

        public List<FinanciadorItem> getFinanciadores() { return financiadores; }
        public void setFinanciadores(List<FinanciadorItem> financiadores) { this.financiadores = financiadores; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<FinanciadorItem> financiadores;
            public Builder financiadores(List<FinanciadorItem> v) { this.financiadores = v; return this; }
            public FinanciadorResponse build() { return new FinanciadorResponse(financiadores); }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinanciadorItem {
        private Integer idfinanciador;
        private String nrofinanciador;
        private String nombreComercial;
        private List<PlanItem> planes;

        public FinanciadorItem() {}

        public Integer getIdfinanciador() { return idfinanciador; }
        public void setIdfinanciador(Integer idfinanciador) { this.idfinanciador = idfinanciador; }
        public String getNrofinanciador() { return nrofinanciador; }
        public void setNrofinanciador(String nrofinanciador) { this.nrofinanciador = nrofinanciador; }
        public String getNombreComercial() { return nombreComercial; }
        public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
        public List<PlanItem> getPlanes() { return planes; }
        public void setPlanes(List<PlanItem> planes) { this.planes = planes; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final FinanciadorItem f = new FinanciadorItem();
            public Builder idfinanciador(Integer v) { f.idfinanciador = v; return this; }
            public Builder nrofinanciador(String v) { f.nrofinanciador = v; return this; }
            public Builder nombreComercial(String v) { f.nombreComercial = v; return this; }
            public Builder planes(List<PlanItem> v) { f.planes = v; return this; }
            public FinanciadorItem build() { return f; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanItem {
        private Integer id;
        private String nombre;

        public PlanItem() {}
        public PlanItem(Integer id, String nombre) { this.id = id; this.nombre = nombre; }

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Integer id;
            private String nombre;
            public Builder id(Integer v) { this.id = v; return this; }
            public Builder nombre(String v) { this.nombre = v; return this; }
            public PlanItem build() { return new PlanItem(id, nombre); }
        }
    }
}
