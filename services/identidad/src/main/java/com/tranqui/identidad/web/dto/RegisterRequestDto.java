package com.tranqui.identidad.web.dto;

import java.time.LocalDate;

public class RegisterRequestDto {
    private String email;
    private String password;
    private String rol;

    // Common fields
    private String nombre;
    private String apellido;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String telefono;

    // Patient specific fields
    private String obraSocial;
    private String numAfiliado;

    // Professional specific fields
    private String matricula;
    private String titulo;
    private String specialty;
    private String cuit;
    private Long cuil;
    private String domicilioAtencion;
    private Double domicilioLat;
    private Double domicilioLng;
    private String domicilioAtencionTorre;
    private String domicilioAtencionPiso;
    private String domicilioAtencionDepto;
    private String domicilioAtencionBarrio;
    private String matriculaTipo;
    private String matriculaProvincia;
    private Integer matriculaNumero;
    private Boolean ofreceOnline;
    private Boolean ofrecePresencial;
    private String fotoUrl;

    // Profesión, Matrícula extendida y Datos Fiscales
    private String profession;
    private String licenseType;
    private String licenseNumber;
    private String licenseJurisdiction;
    private String licenseDocumentUrl;
    private String taxIdType;
    private String taxId;
    private String legalName;
    private Integer ivaConditionId;
    private String fiscalAddress;

    private Boolean aceptaTerminos;

    public RegisterRequestDto() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public Integer getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(Integer numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getObraSocial() { return obraSocial; }
    public void setObraSocial(String obraSocial) { this.obraSocial = obraSocial; }

    public String getNumAfiliado() { return numAfiliado; }
    public void setNumAfiliado(String numAfiliado) { this.numAfiliado = numAfiliado; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }

    public Long getCuil() { return cuil; }
    public void setCuil(Long cuil) { this.cuil = cuil; }

    public String getDomicilioAtencion() { return domicilioAtencion; }
    public void setDomicilioAtencion(String domicilioAtencion) { this.domicilioAtencion = domicilioAtencion; }

    public Double getDomicilioLat() { return domicilioLat; }
    public void setDomicilioLat(Double domicilioLat) { this.domicilioLat = domicilioLat; }

    public Double getDomicilioLng() { return domicilioLng; }
    public void setDomicilioLng(Double domicilioLng) { this.domicilioLng = domicilioLng; }

    public String getDomicilioAtencionTorre() { return domicilioAtencionTorre; }
    public void setDomicilioAtencionTorre(String domicilioAtencionTorre) { this.domicilioAtencionTorre = domicilioAtencionTorre; }

    public String getDomicilioAtencionPiso() { return domicilioAtencionPiso; }
    public void setDomicilioAtencionPiso(String domicilioAtencionPiso) { this.domicilioAtencionPiso = domicilioAtencionPiso; }

    public String getDomicilioAtencionDepto() { return domicilioAtencionDepto; }
    public void setDomicilioAtencionDepto(String domicilioAtencionDepto) { this.domicilioAtencionDepto = domicilioAtencionDepto; }

    public String getDomicilioAtencionBarrio() { return domicilioAtencionBarrio; }
    public void setDomicilioAtencionBarrio(String domicilioAtencionBarrio) { this.domicilioAtencionBarrio = domicilioAtencionBarrio; }

    public String getMatriculaTipo() { return matriculaTipo; }
    public void setMatriculaTipo(String matriculaTipo) { this.matriculaTipo = matriculaTipo; }

    public String getMatriculaProvincia() { return matriculaProvincia; }
    public void setMatriculaProvincia(String matriculaProvincia) { this.matriculaProvincia = matriculaProvincia; }

    public Integer getMatriculaNumero() { return matriculaNumero; }
    public void setMatriculaNumero(Integer matriculaNumero) { this.matriculaNumero = matriculaNumero; }

    public Boolean getOfreceOnline() { return ofreceOnline; }
    public void setOfreceOnline(Boolean ofreceOnline) { this.ofreceOnline = ofreceOnline; }

    public Boolean getOfrecePresencial() { return ofrecePresencial; }
    public void setOfrecePresencial(Boolean ofrecePresencial) { this.ofrecePresencial = ofrecePresencial; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getLicenseJurisdiction() { return licenseJurisdiction; }
    public void setLicenseJurisdiction(String licenseJurisdiction) { this.licenseJurisdiction = licenseJurisdiction; }

    public String getLicenseDocumentUrl() { return licenseDocumentUrl; }
    public void setLicenseDocumentUrl(String licenseDocumentUrl) { this.licenseDocumentUrl = licenseDocumentUrl; }

    public String getTaxIdType() { return taxIdType; }
    public void setTaxIdType(String taxIdType) { this.taxIdType = taxIdType; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public Integer getIvaConditionId() { return ivaConditionId; }
    public void setIvaConditionId(Integer ivaConditionId) { this.ivaConditionId = ivaConditionId; }

    public String getFiscalAddress() { return fiscalAddress; }
    public void setFiscalAddress(String fiscalAddress) { this.fiscalAddress = fiscalAddress; }

    public Boolean getAceptaTerminos() { return aceptaTerminos; }
    public void setAceptaTerminos(Boolean aceptaTerminos) { this.aceptaTerminos = aceptaTerminos; }
}
