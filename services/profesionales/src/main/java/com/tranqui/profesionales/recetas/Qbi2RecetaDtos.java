package com.tranqui.profesionales.recetas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Formas de request y response para POST/DELETE /apirecipe/Receta de QBI2 Recipe (Innovamed).
 * No usa Lombok: clases con getters, setters y builders fluidos para compatibilidad total con Jackson.
 */
public class Qbi2RecetaDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaRequest {
        private Integer clienteAppId;
        private List<MedicamentoRequest> medicamentos;
        private String diagnostico;
        private Integer serviceType;
        private String fechaEmision;
        private String observaciones;
        private String indicaciones;
        private RecetaPosdatadaRequest recetasPostadatas;
        private PacienteReceta paciente;
        private MedicoReceta medico;
        private SubemisorDto subemisor;
        private LugarAtencionDto lugarAtencion;
        private List<InformacionExtraDto> informacionExtra;

        public RecetaRequest() {}

        public Integer getClienteAppId() { return clienteAppId; }
        public void setClienteAppId(Integer clienteAppId) { this.clienteAppId = clienteAppId; }
        public List<MedicamentoRequest> getMedicamentos() { return medicamentos; }
        public void setMedicamentos(List<MedicamentoRequest> medicamentos) { this.medicamentos = medicamentos; }
        public String getDiagnostico() { return diagnostico; }
        public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
        public Integer getServiceType() { return serviceType; }
        public void setServiceType(Integer serviceType) { this.serviceType = serviceType; }
        public String getFechaEmision() { return fechaEmision; }
        public void setFechaEmision(String fechaEmision) { this.fechaEmision = fechaEmision; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
        public String getIndicaciones() { return indicaciones; }
        public void setIndicaciones(String indicaciones) { this.indicaciones = indicaciones; }
        public RecetaPosdatadaRequest getRecetasPostadatas() { return recetasPostadatas; }
        public void setRecetasPostadatas(RecetaPosdatadaRequest recetasPostadatas) { this.recetasPostadatas = recetasPostadatas; }
        public PacienteReceta getPaciente() { return paciente; }
        public void setPaciente(PacienteReceta paciente) { this.paciente = paciente; }
        public MedicoReceta getMedico() { return medico; }
        public void setMedico(MedicoReceta medico) { this.medico = medico; }
        public SubemisorDto getSubemisor() { return subemisor; }
        public void setSubemisor(SubemisorDto subemisor) { this.subemisor = subemisor; }
        public LugarAtencionDto getLugarAtencion() { return lugarAtencion; }
        public void setLugarAtencion(LugarAtencionDto lugarAtencion) { this.lugarAtencion = lugarAtencion; }
        public List<InformacionExtraDto> getInformacionExtra() { return informacionExtra; }
        public void setInformacionExtra(List<InformacionExtraDto> informacionExtra) { this.informacionExtra = informacionExtra; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final RecetaRequest req = new RecetaRequest();
            public Builder clienteAppId(Integer v) { req.clienteAppId = v; return this; }
            public Builder medicamentos(List<MedicamentoRequest> v) { req.medicamentos = v; return this; }
            public Builder diagnostico(String v) { req.diagnostico = v; return this; }
            public Builder serviceType(Integer v) { req.serviceType = v; return this; }
            public Builder fechaEmision(String v) { req.fechaEmision = v; return this; }
            public Builder observaciones(String v) { req.observaciones = v; return this; }
            public Builder indicaciones(String v) { req.indicaciones = v; return this; }
            public Builder recetasPostadatas(RecetaPosdatadaRequest v) { req.recetasPostadatas = v; return this; }
            public Builder paciente(PacienteReceta v) { req.paciente = v; return this; }
            public Builder medico(MedicoReceta v) { req.medico = v; return this; }
            public Builder subemisor(SubemisorDto v) { req.subemisor = v; return this; }
            public Builder lugarAtencion(LugarAtencionDto v) { req.lugarAtencion = v; return this; }
            public Builder informacionExtra(List<InformacionExtraDto> v) { req.informacionExtra = v; return this; }
            public RecetaRequest build() { return req; }
        }
    }

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

        public MedicamentoRequest() {}

        public String getNombreProducto() { return nombreProducto; }
        public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
        public String getNombreDroga() { return nombreDroga; }
        public void setNombreDroga(String nombreDroga) { this.nombreDroga = nombreDroga; }
        public String getPresentacion() { return presentacion; }
        public void setPresentacion(String presentacion) { this.presentacion = presentacion; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public String getPermiteSustitucion() { return permiteSustitucion; }
        public void setPermiteSustitucion(String permiteSustitucion) { this.permiteSustitucion = permiteSustitucion; }
        public String getRegNo() { return regNo; }
        public void setRegNo(String regNo) { this.regNo = regNo; }
        public Integer getTratamiento() { return tratamiento; }
        public void setTratamiento(Integer tratamiento) { this.tratamiento = tratamiento; }
        public String getDiagnostico() { return diagnostico; }
        public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
        public String getCodigoDiagnostico() { return codigoDiagnostico; }
        public void setCodigoDiagnostico(String codigoDiagnostico) { this.codigoDiagnostico = codigoDiagnostico; }
        public String getPosologia() { return posologia; }
        public void setPosologia(String posologia) { this.posologia = posologia; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
        public Boolean getForzarDuplicado() { return forzarDuplicado; }
        public void setForzarDuplicado(Boolean forzarDuplicado) { this.forzarDuplicado = forzarDuplicado; }
        public String getPromoId() { return promoId; }
        public void setPromoId(String promoId) { this.promoId = promoId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicamentoRequest m = new MedicamentoRequest();
            public Builder nombreProducto(String v) { m.nombreProducto = v; return this; }
            public Builder nombreDroga(String v) { m.nombreDroga = v; return this; }
            public Builder presentacion(String v) { m.presentacion = v; return this; }
            public Builder cantidad(Integer v) { m.cantidad = v; return this; }
            public Builder permiteSustitucion(String v) { m.permiteSustitucion = v; return this; }
            public Builder regNo(String v) { m.regNo = v; return this; }
            public Builder tratamiento(Integer v) { m.tratamiento = v; return this; }
            public Builder diagnostico(String v) { m.diagnostico = v; return this; }
            public Builder codigoDiagnostico(String v) { m.codigoDiagnostico = v; return this; }
            public Builder posologia(String v) { m.posologia = v; return this; }
            public Builder observaciones(String v) { m.observaciones = v; return this; }
            public Builder forzarDuplicado(Boolean v) { m.forzarDuplicado = v; return this; }
            public Builder promoId(String v) { m.promoId = v; return this; }
            public MedicamentoRequest build() { return m; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaPosdatadaRequest {
        private Integer cantidad;
        private Integer diasAPosdatar;
        private List<String> fechas;

        public RecetaPosdatadaRequest() {}

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public Integer getDiasAPosdatar() { return diasAPosdatar; }
        public void setDiasAPosdatar(Integer diasAPosdatar) { this.diasAPosdatar = diasAPosdatar; }
        public List<String> getFechas() { return fechas; }
        public void setFechas(List<String> fechas) { this.fechas = fechas; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final RecetaPosdatadaRequest r = new RecetaPosdatadaRequest();
            public Builder cantidad(Integer v) { r.cantidad = v; return this; }
            public Builder diasAPosdatar(Integer v) { r.diasAPosdatar = v; return this; }
            public Builder fechas(List<String> v) { r.fechas = v; return this; }
            public RecetaPosdatadaRequest build() { return r; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PacienteReceta {
        private String apellido;
        private String nombre;
        private String tipoDoc;
        private String nroDoc;
        private String sexo;
        private String fechaNacimiento;
        private String cuil;
        private String localidad;
        private String provincia;
        private String pais;
        private String email;
        private String telefono;
        private Boolean ocultarPaciente;
        private CoberturaDto cobertura;
        private DomicilioDto domicilio;

        public PacienteReceta() {}

        public String getApellido() { return apellido; }
        public void setApellido(String apellido) { this.apellido = apellido; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getTipoDoc() { return tipoDoc; }
        public void setTipoDoc(String tipoDoc) { this.tipoDoc = tipoDoc; }
        public String getNroDoc() { return nroDoc; }
        public void setNroDoc(String nroDoc) { this.nroDoc = nroDoc; }
        public String getSexo() { return sexo; }
        public void setSexo(String sexo) { this.sexo = sexo; }
        public String getFechaNacimiento() { return fechaNacimiento; }
        public void setFechaNacimiento(String fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
        public String getCuil() { return cuil; }
        public void setCuil(String cuil) { this.cuil = cuil; }
        public String getLocalidad() { return localidad; }
        public void setLocalidad(String localidad) { this.localidad = localidad; }
        public String getProvincia() { return provincia; }
        public void setProvincia(String provincia) { this.provincia = provincia; }
        public String getPais() { return pais; }
        public void setPais(String pais) { this.pais = pais; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public Boolean getOcultarPaciente() { return ocultarPaciente; }
        public void setOcultarPaciente(Boolean ocultarPaciente) { this.ocultarPaciente = ocultarPaciente; }
        public CoberturaDto getCobertura() { return cobertura; }
        public void setCobertura(CoberturaDto cobertura) { this.cobertura = cobertura; }
        public DomicilioDto getDomicilio() { return domicilio; }
        public void setDomicilio(DomicilioDto domicilio) { this.domicilio = domicilio; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final PacienteReceta p = new PacienteReceta();
            public Builder apellido(String v) { p.apellido = v; return this; }
            public Builder nombre(String v) { p.nombre = v; return this; }
            public Builder tipoDoc(String v) { p.tipoDoc = v; return this; }
            public Builder nroDoc(String v) { p.nroDoc = v; return this; }
            public Builder sexo(String v) { p.sexo = v; return this; }
            public Builder fechaNacimiento(String v) { p.fechaNacimiento = v; return this; }
            public Builder cuil(String v) { p.cuil = v; return this; }
            public Builder localidad(String v) { p.localidad = v; return this; }
            public Builder provincia(String v) { p.provincia = v; return this; }
            public Builder pais(String v) { p.pais = v; return this; }
            public Builder email(String v) { p.email = v; return this; }
            public Builder telefono(String v) { p.telefono = v; return this; }
            public Builder ocultarPaciente(Boolean v) { p.ocultarPaciente = v; return this; }
            public Builder cobertura(CoberturaDto v) { p.cobertura = v; return this; }
            public Builder domicilio(DomicilioDto v) { p.domicilio = v; return this; }
            public PacienteReceta build() { return p; }
        }
    }

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

        public MedicoReceta() {}

        public String getApellido() { return apellido; }
        public void setApellido(String apellido) { this.apellido = apellido; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getTipoDoc() { return tipoDoc; }
        public void setTipoDoc(String tipoDoc) { this.tipoDoc = tipoDoc; }
        public String getNroDoc() { return nroDoc; }
        public void setNroDoc(String nroDoc) { this.nroDoc = nroDoc; }
        public String getEspecialidad() { return especialidad; }
        public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
        public String getSexo() { return sexo; }
        public void setSexo(String sexo) { this.sexo = sexo; }
        public String getFechaNacimiento() { return fechaNacimiento; }
        public void setFechaNacimiento(String fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getPais() { return pais; }
        public void setPais(String pais) { this.pais = pais; }
        public SelloDto getSello() { return sello; }
        public void setSello(SelloDto sello) { this.sello = sello; }
        public String getLogoInstitucion() { return logoInstitucion; }
        public void setLogoInstitucion(String logoInstitucion) { this.logoInstitucion = logoInstitucion; }
        public MatriculaDto getMatricula() { return matricula; }
        public void setMatricula(MatriculaDto matricula) { this.matricula = matricula; }
        public String getIdTributario() { return idTributario; }
        public void setIdTributario(String idTributario) { this.idTributario = idTributario; }
        public String getProfesion() { return profesion; }
        public void setProfesion(String profesion) { this.profesion = profesion; }
        public String getIdREFEPS() { return idREFEPS; }
        public void setIdREFEPS(String idREFEPS) { this.idREFEPS = idREFEPS; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicoReceta m = new MedicoReceta();
            public Builder apellido(String v) { m.apellido = v; return this; }
            public Builder nombre(String v) { m.nombre = v; return this; }
            public Builder tipoDoc(String v) { m.tipoDoc = v; return this; }
            public Builder nroDoc(String v) { m.nroDoc = v; return this; }
            public Builder especialidad(String v) { m.especialidad = v; return this; }
            public Builder sexo(String v) { m.sexo = v; return this; }
            public Builder fechaNacimiento(String v) { m.fechaNacimiento = v; return this; }
            public Builder email(String v) { m.email = v; return this; }
            public Builder telefono(String v) { m.telefono = v; return this; }
            public Builder pais(String v) { m.pais = v; return this; }
            public Builder sello(SelloDto v) { m.sello = v; return this; }
            public Builder logoInstitucion(String v) { m.logoInstitucion = v; return this; }
            public Builder matricula(MatriculaDto v) { m.matricula = v; return this; }
            public Builder idTributario(String v) { m.idTributario = v; return this; }
            public Builder profesion(String v) { m.profesion = v; return this; }
            public Builder idREFEPS(String v) { m.idREFEPS = v; return this; }
            public MedicoReceta build() { return m; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatriculaDto {
        private String tipo;
        private String numero;
        private String provincia;
        private String profesion;
        private String especialidad;

        public MatriculaDto() {}

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public String getProvincia() { return provincia; }
        public void setProvincia(String provincia) { this.provincia = provincia; }
        public String getProfesion() { return profesion; }
        public void setProfesion(String profesion) { this.profesion = profesion; }
        public String getEspecialidad() { return especialidad; }
        public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MatriculaDto m = new MatriculaDto();
            public Builder tipo(String v) { m.tipo = v; return this; }
            public Builder numero(String v) { m.numero = v; return this; }
            public Builder provincia(String v) { m.provincia = v; return this; }
            public Builder profesion(String v) { m.profesion = v; return this; }
            public Builder especialidad(String v) { m.especialidad = v; return this; }
            public MatriculaDto build() { return m; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SelloDto {
        private String linea1;
        private String linea2;
        private String linea3;

        public SelloDto() {}

        public String getLinea1() { return linea1; }
        public void setLinea1(String linea1) { this.linea1 = linea1; }
        public String getLinea2() { return linea2; }
        public void setLinea2(String linea2) { this.linea2 = linea2; }
        public String getLinea3() { return linea3; }
        public void setLinea3(String linea3) { this.linea3 = linea3; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final SelloDto s = new SelloDto();
            public Builder linea1(String v) { s.linea1 = v; return this; }
            public Builder linea2(String v) { s.linea2 = v; return this; }
            public Builder linea3(String v) { s.linea3 = v; return this; }
            public SelloDto build() { return s; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoberturaDto {
        private String idFinanciador;
        private String plan;
        private Integer planId;
        private String numero;
        private String dniTitular;

        public CoberturaDto() {}

        public String getIdFinanciador() { return idFinanciador; }
        public void setIdFinanciador(String idFinanciador) { this.idFinanciador = idFinanciador; }
        public String getPlan() { return plan; }
        public void setPlan(String plan) { this.plan = plan; }
        public Integer getPlanId() { return planId; }
        public void setPlanId(Integer planId) { this.planId = planId; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public String getDniTitular() { return dniTitular; }
        public void setDniTitular(String dniTitular) { this.dniTitular = dniTitular; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final CoberturaDto c = new CoberturaDto();
            public Builder idFinanciador(String v) { c.idFinanciador = v; return this; }
            public Builder plan(String v) { c.plan = v; return this; }
            public Builder planId(Integer v) { c.planId = v; return this; }
            public Builder numero(String v) { c.numero = v; return this; }
            public Builder dniTitular(String v) { c.dniTitular = v; return this; }
            public CoberturaDto build() { return c; }
        }
    }

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

        public DomicilioDto() {}

        public String getCalle() { return calle; }
        public void setCalle(String calle) { this.calle = calle; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public String getPiso() { return piso; }
        public void setPiso(String piso) { this.piso = piso; }
        public String getDpto() { return dpto; }
        public void setDpto(String dpto) { this.dpto = dpto; }
        public String getCodigoPostal() { return codigoPostal; }
        public void setCodigoPostal(String codigoPostal) { this.codigoPostal = codigoPostal; }
        public String getLocalidad() { return localidad; }
        public void setLocalidad(String localidad) { this.localidad = localidad; }
        public String getMunicipio() { return municipio; }
        public void setMunicipio(String municipio) { this.municipio = municipio; }
        public String getProvincia() { return provincia; }
        public void setProvincia(String provincia) { this.provincia = provincia; }
        public String getPais() { return pais; }
        public void setPais(String pais) { this.pais = pais; }
        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final DomicilioDto d = new DomicilioDto();
            public Builder calle(String v) { d.calle = v; return this; }
            public Builder numero(String v) { d.numero = v; return this; }
            public Builder piso(String v) { d.piso = v; return this; }
            public Builder dpto(String v) { d.dpto = v; return this; }
            public Builder codigoPostal(String v) { d.codigoPostal = v; return this; }
            public Builder localidad(String v) { d.localidad = v; return this; }
            public Builder municipio(String v) { d.municipio = v; return this; }
            public Builder provincia(String v) { d.provincia = v; return this; }
            public Builder pais(String v) { d.pais = v; return this; }
            public Builder observacion(String v) { d.observacion = v; return this; }
            public Builder direccion(String v) { d.direccion = v; return this; }
            public DomicilioDto build() { return d; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubemisorDto {
        private String nombre;
        private String cuit;
        private String direccion;
        private String logoLink;
        private String logoBase64;

        public SubemisorDto() {}

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getCuit() { return cuit; }
        public void setCuit(String cuit) { this.cuit = cuit; }
        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }
        public String getLogoLink() { return logoLink; }
        public void setLogoLink(String logoLink) { this.logoLink = logoLink; }
        public String getLogoBase64() { return logoBase64; }
        public void setLogoBase64(String logoBase64) { this.logoBase64 = logoBase64; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final SubemisorDto s = new SubemisorDto();
            public Builder nombre(String v) { s.nombre = v; return this; }
            public Builder cuit(String v) { s.cuit = v; return this; }
            public Builder direccion(String v) { s.direccion = v; return this; }
            public Builder logoLink(String v) { s.logoLink = v; return this; }
            public Builder logoBase64(String v) { s.logoBase64 = v; return this; }
            public SubemisorDto build() { return s; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LugarAtencionDto {
        private String nombreConsultorio;
        private DomicilioDto domicilio;
        private String datosContacto;
        private String email;
        private String logo;

        public LugarAtencionDto() {}

        public String getNombreConsultorio() { return nombreConsultorio; }
        public void setNombreConsultorio(String nombreConsultorio) { this.nombreConsultorio = nombreConsultorio; }
        public DomicilioDto getDomicilio() { return domicilio; }
        public void setDomicilio(DomicilioDto domicilio) { this.domicilio = domicilio; }
        public String getDatosContacto() { return datosContacto; }
        public void setDatosContacto(String datosContacto) { this.datosContacto = datosContacto; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final LugarAtencionDto l = new LugarAtencionDto();
            public Builder nombreConsultorio(String v) { l.nombreConsultorio = v; return this; }
            public Builder domicilio(DomicilioDto v) { l.domicilio = v; return this; }
            public Builder datosContacto(String v) { l.datosContacto = v; return this; }
            public Builder email(String v) { l.email = v; return this; }
            public Builder logo(String v) { l.logo = v; return this; }
            public LugarAtencionDto build() { return l; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InformacionExtraDto {
        private String key;
        private String value;

        public InformacionExtraDto() {}

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final InformacionExtraDto i = new InformacionExtraDto();
            public Builder key(String v) { i.key = v; return this; }
            public Builder value(String v) { i.value = v; return this; }
            public InformacionExtraDto build() { return i; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaResponse {
        private List<RecetaResult> recetas;
        private List<MedicamentoError> errores;
        private List<RecetaDetalle> response;
        private String idTransaccion;

        public RecetaResponse() {}

        public List<RecetaResult> getRecetas() { return recetas; }
        public void setRecetas(List<RecetaResult> recetas) { this.recetas = recetas; }
        public List<MedicamentoError> getErrores() { return errores; }
        public void setErrores(List<MedicamentoError> errores) { this.errores = errores; }
        public List<RecetaDetalle> getResponse() { return response; }
        public void setResponse(List<RecetaDetalle> response) { this.response = response; }
        public String getIdTransaccion() { return idTransaccion; }
        public void setIdTransaccion(String idTransaccion) { this.idTransaccion = idTransaccion; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final RecetaResponse r = new RecetaResponse();
            public Builder recetas(List<RecetaResult> v) { r.recetas = v; return this; }
            public Builder errores(List<MedicamentoError> v) { r.errores = v; return this; }
            public Builder response(List<RecetaDetalle> v) { r.response = v; return this; }
            public Builder idTransaccion(String v) { r.idTransaccion = v; return this; }
            public RecetaResponse build() { return r; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaResult {
        private String id;
        private String idReceta;
        private String fecha;
        private List<String> nroCUIR;
        private String s3Link;
        private String verificador;
        private String linkECommerce;

        public RecetaResult() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIdReceta() { return idReceta; }
        public void setIdReceta(String idReceta) { this.idReceta = idReceta; }
        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
        public List<String> getNroCUIR() { return nroCUIR; }
        public void setNroCUIR(List<String> nroCUIR) { this.nroCUIR = nroCUIR; }
        public String getS3Link() { return s3Link; }
        public void setS3Link(String s3Link) { this.s3Link = s3Link; }
        public String getVerificador() { return verificador; }
        public void setVerificador(String verificador) { this.verificador = verificador; }
        public String getLinkECommerce() { return linkECommerce; }
        public void setLinkECommerce(String linkECommerce) { this.linkECommerce = linkECommerce; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final RecetaResult r = new RecetaResult();
            public Builder id(String v) { r.id = v; return this; }
            public Builder idReceta(String v) { r.idReceta = v; return this; }
            public Builder fecha(String v) { r.fecha = v; return this; }
            public Builder nroCUIR(List<String> v) { r.nroCUIR = v; return this; }
            public Builder s3Link(String v) { r.s3Link = v; return this; }
            public Builder verificador(String v) { r.verificador = v; return this; }
            public Builder linkECommerce(String v) { r.linkECommerce = v; return this; }
            public RecetaResult build() { return r; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecetaDetalle {
        private String fecha;
        private String fechavencimiento;
        private String status;

        public RecetaDetalle() {}

        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
        public String getFechavencimiento() { return fechavencimiento; }
        public void setFechavencimiento(String fechavencimiento) { this.fechavencimiento = fechavencimiento; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final RecetaDetalle r = new RecetaDetalle();
            public Builder fecha(String v) { r.fecha = v; return this; }
            public Builder fechavencimiento(String v) { r.fechavencimiento = v; return this; }
            public Builder status(String v) { r.status = v; return this; }
            public RecetaDetalle build() { return r; }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicamentoError {
        private String error;
        private String mensaje;
        private List<String> medicamento;

        public MedicamentoError() {}

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        public List<String> getMedicamento() { return medicamento; }
        public void setMedicamento(List<String> medicamento) { this.medicamento = medicamento; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicamentoError m = new MedicamentoError();
            public Builder error(String v) { m.error = v; return this; }
            public Builder mensaje(String v) { m.mensaje = v; return this; }
            public Builder medicamento(List<String> v) { m.medicamento = v; return this; }
            public MedicamentoError build() { return m; }
        }
    }
}
