package com.tranqui.identidad.datos;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad Usuario de identidad (Fase 9, I2a), con exactamente las 57 columnas
 * correspondientes a la cuenta, credenciales, datos personales, fiscales y preferencias.
 */
public record Usuario(
        Long id,
        Integer version,
        String email,
        String password,
        String rol,
        LocalDateTime fechaRegistro,
        Boolean emailVerificado,
        String codigoVerificacion,
        LocalDateTime codigoVerificacionExpiresAt,
        String resetPasswordCode,
        LocalDateTime resetPasswordExpiresAt,
        boolean mustChangePassword,
        LocalDateTime terminosAceptadosEn,
        Boolean perfilCompleto,
        Boolean cuentaEliminada,
        LocalDateTime anonimizadoEn,
        Boolean notificacionesEmailHabilitadas,
        Boolean recibirNovedades,
        String nombre,
        String apellido,
        String telefono,
        String sexo,
        LocalDate fechaNacimiento,
        String tipoDocumento,
        Integer numeroDocumento,
        String dni,
        Long cuil,
        String direccion,
        String fotoUrl,
        String domicilioCalle,
        String domicilioNumero,
        String domicilioPiso,
        String domicilioDpto,
        String domicilioCodigoPostal,
        String domicilioLocalidad,
        String domicilioProvincia,
        String domicilioPais,
        String obraSocial,
        String numAfiliado,
        String datosOfuscado,
        Integer credencialCodEntidad,
        String credencialPan,
        String credencialPlan,
        String credencialToken,
        Boolean verificadoAdmin,
        String profession,
        String licenseType,
        String licenseNumber,
        String licenseJurisdiction,
        String licenseDocumentUrl,
        LocalDateTime licenseVerifiedAt,
        String licenseVerifiedBy,
        String taxIdType,
        String taxId,
        String legalName,
        Integer ivaConditionId,
        String fiscalAddress
) {
    public int aggregateVersion() {
        return (version != null ? version : 0) + 1;
    }

    public boolean isPerfilCompleto() {
        return perfilCompleto == null || perfilCompleto;
    }

    public boolean isCuentaEliminada() {
        return cuentaEliminada != null && cuentaEliminada;
    }

    public boolean isNotificacionesEmailHabilitadas() {
        return notificacionesEmailHabilitadas == null || notificacionesEmailHabilitadas;
    }

    public boolean isRecibirNovedades() {
        return recibirNovedades == null || recibirNovedades;
    }

    public String emailNormalizado() {
        return email != null ? email.trim().toLowerCase() : null;
    }

    public boolean isCanPrescribe() {
        boolean isMedical = "psiquiatra".equalsIgnoreCase(profession) || "medico".equalsIgnoreCase(profession)
                || "PSIQUIATRA".equalsIgnoreCase(rol);
        return isMedical && (licenseVerifiedAt != null || Boolean.TRUE.equals(verificadoAdmin));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .version(version)
                .email(email)
                .password(password)
                .rol(rol)
                .fechaRegistro(fechaRegistro)
                .emailVerificado(emailVerificado)
                .codigoVerificacion(codigoVerificacion)
                .codigoVerificacionExpiresAt(codigoVerificacionExpiresAt)
                .resetPasswordCode(resetPasswordCode)
                .resetPasswordExpiresAt(resetPasswordExpiresAt)
                .mustChangePassword(mustChangePassword)
                .terminosAceptadosEn(terminosAceptadosEn)
                .perfilCompleto(perfilCompleto)
                .cuentaEliminada(cuentaEliminada)
                .anonimizadoEn(anonimizadoEn)
                .notificacionesEmailHabilitadas(notificacionesEmailHabilitadas)
                .recibirNovedades(recibirNovedades)
                .nombre(nombre)
                .apellido(apellido)
                .telefono(telefono)
                .sexo(sexo)
                .fechaNacimiento(fechaNacimiento)
                .tipoDocumento(tipoDocumento)
                .numeroDocumento(numeroDocumento)
                .dni(dni)
                .cuil(cuil)
                .direccion(direccion)
                .fotoUrl(fotoUrl)
                .domicilioCalle(domicilioCalle)
                .domicilioNumero(domicilioNumero)
                .domicilioPiso(domicilioPiso)
                .domicilioDpto(domicilioDpto)
                .domicilioCodigoPostal(domicilioCodigoPostal)
                .domicilioLocalidad(domicilioLocalidad)
                .domicilioProvincia(domicilioProvincia)
                .domicilioPais(domicilioPais)
                .obraSocial(obraSocial)
                .numAfiliado(numAfiliado)
                .datosOfuscado(datosOfuscado)
                .credencialCodEntidad(credencialCodEntidad)
                .credencialPan(credencialPan)
                .credencialPlan(credencialPlan)
                .credencialToken(credencialToken)
                .verificadoAdmin(verificadoAdmin)
                .profession(profession)
                .licenseType(licenseType)
                .licenseNumber(licenseNumber)
                .licenseJurisdiction(licenseJurisdiction)
                .licenseDocumentUrl(licenseDocumentUrl)
                .licenseVerifiedAt(licenseVerifiedAt)
                .licenseVerifiedBy(licenseVerifiedBy)
                .taxIdType(taxIdType)
                .taxId(taxId)
                .legalName(legalName)
                .ivaConditionId(ivaConditionId)
                .fiscalAddress(fiscalAddress);
    }

    public static final class Builder {
        private Long id;
        private Integer version = 0;
        private String email;
        private String password;
        private String rol;
        private LocalDateTime fechaRegistro = LocalDateTime.now();
        private Boolean emailVerificado = false;
        private String codigoVerificacion;
        private LocalDateTime codigoVerificacionExpiresAt;
        private String resetPasswordCode;
        private LocalDateTime resetPasswordExpiresAt;
        private boolean mustChangePassword = false;
        private LocalDateTime terminosAceptadosEn;
        private Boolean perfilCompleto = true;
        private Boolean cuentaEliminada = false;
        private LocalDateTime anonimizadoEn;
        private Boolean notificacionesEmailHabilitadas = true;
        private Boolean recibirNovedades = true;
        private String nombre;
        private String apellido;
        private String telefono;
        private String sexo;
        private LocalDate fechaNacimiento;
        private String tipoDocumento;
        private Integer numeroDocumento;
        private String dni;
        private Long cuil;
        private String direccion;
        private String fotoUrl;
        private String domicilioCalle;
        private String domicilioNumero;
        private String domicilioPiso;
        private String domicilioDpto;
        private String domicilioCodigoPostal;
        private String domicilioLocalidad;
        private String domicilioProvincia;
        private String domicilioPais;
        private String obraSocial;
        private String numAfiliado;
        private String datosOfuscado;
        private Integer credencialCodEntidad;
        private String credencialPan;
        private String credencialPlan;
        private String credencialToken;
        private Boolean verificadoAdmin;
        private String profession;
        private String licenseType;
        private String licenseNumber;
        private String licenseJurisdiction;
        private String licenseDocumentUrl;
        private LocalDateTime licenseVerifiedAt;
        private String licenseVerifiedBy;
        private String taxIdType;
        private String taxId;
        private String legalName;
        private Integer ivaConditionId;
        private String fiscalAddress;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder version(Integer version) { this.version = version; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder rol(String rol) { this.rol = rol; return this; }
        public Builder fechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; return this; }
        public Builder emailVerificado(Boolean emailVerificado) { this.emailVerificado = emailVerificado; return this; }
        public Builder codigoVerificacion(String codigoVerificacion) { this.codigoVerificacion = codigoVerificacion; return this; }
        public Builder codigoVerificacionExpiresAt(LocalDateTime codigoVerificacionExpiresAt) { this.codigoVerificacionExpiresAt = codigoVerificacionExpiresAt; return this; }
        public Builder resetPasswordCode(String resetPasswordCode) { this.resetPasswordCode = resetPasswordCode; return this; }
        public Builder resetPasswordExpiresAt(LocalDateTime resetPasswordExpiresAt) { this.resetPasswordExpiresAt = resetPasswordExpiresAt; return this; }
        public Builder mustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; return this; }
        public Builder terminosAceptadosEn(LocalDateTime terminosAceptadosEn) { this.terminosAceptadosEn = terminosAceptadosEn; return this; }
        public Builder perfilCompleto(Boolean perfilCompleto) { this.perfilCompleto = perfilCompleto; return this; }
        public Builder cuentaEliminada(Boolean cuentaEliminada) { this.cuentaEliminada = cuentaEliminada; return this; }
        public Builder anonimizadoEn(LocalDateTime anonimizadoEn) { this.anonimizadoEn = anonimizadoEn; return this; }
        public Builder notificacionesEmailHabilitadas(Boolean notificacionesEmailHabilitadas) { this.notificacionesEmailHabilitadas = notificacionesEmailHabilitadas; return this; }
        public Builder recibirNovedades(Boolean recibirNovedades) { this.recibirNovedades = recibirNovedades; return this; }
        public Builder nombre(String nombre) { this.nombre = nombre; return this; }
        public Builder apellido(String apellido) { this.apellido = apellido; return this; }
        public Builder telefono(String telefono) { this.telefono = telefono; return this; }
        public Builder sexo(String sexo) { this.sexo = sexo; return this; }
        public Builder fechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; return this; }
        public Builder tipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; return this; }
        public Builder numeroDocumento(Integer numeroDocumento) { this.numeroDocumento = numeroDocumento; return this; }
        public Builder dni(String dni) { this.dni = dni; return this; }
        public Builder cuil(Long cuil) { this.cuil = cuil; return this; }
        public Builder direccion(String direccion) { this.direccion = direccion; return this; }
        public Builder fotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; return this; }
        public Builder domicilioCalle(String domicilioCalle) { this.domicilioCalle = domicilioCalle; return this; }
        public Builder domicilioNumero(String domicilioNumero) { this.domicilioNumero = domicilioNumero; return this; }
        public Builder domicilioPiso(String domicilioPiso) { this.domicilioPiso = domicilioPiso; return this; }
        public Builder domicilioDpto(String domicilioDpto) { this.domicilioDpto = domicilioDpto; return this; }
        public Builder domicilioCodigoPostal(String domicilioCodigoPostal) { this.domicilioCodigoPostal = domicilioCodigoPostal; return this; }
        public Builder domicilioLocalidad(String domicilioLocalidad) { this.domicilioLocalidad = domicilioLocalidad; return this; }
        public Builder domicilioProvincia(String domicilioProvincia) { this.domicilioProvincia = domicilioProvincia; return this; }
        public Builder domicilioPais(String domicilioPais) { this.domicilioPais = domicilioPais; return this; }
        public Builder obraSocial(String obraSocial) { this.obraSocial = obraSocial; return this; }
        public Builder numAfiliado(String numAfiliado) { this.numAfiliado = numAfiliado; return this; }
        public Builder datosOfuscado(String datosOfuscado) { this.datosOfuscado = datosOfuscado; return this; }
        public Builder credencialCodEntidad(Integer credencialCodEntidad) { this.credencialCodEntidad = credencialCodEntidad; return this; }
        public Builder credencialPan(String credencialPan) { this.credencialPan = credencialPan; return this; }
        public Builder credencialPlan(String credencialPlan) { this.credencialPlan = credencialPlan; return this; }
        public Builder credencialToken(String credencialToken) { this.credencialToken = credencialToken; return this; }
        public Builder verificadoAdmin(Boolean verificadoAdmin) { this.verificadoAdmin = verificadoAdmin; return this; }
        public Builder profession(String profession) { this.profession = profession; return this; }
        public Builder licenseType(String licenseType) { this.licenseType = licenseType; return this; }
        public Builder licenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; return this; }
        public Builder licenseJurisdiction(String licenseJurisdiction) { this.licenseJurisdiction = licenseJurisdiction; return this; }
        public Builder licenseDocumentUrl(String licenseDocumentUrl) { this.licenseDocumentUrl = licenseDocumentUrl; return this; }
        public Builder licenseVerifiedAt(LocalDateTime licenseVerifiedAt) { this.licenseVerifiedAt = licenseVerifiedAt; return this; }
        public Builder licenseVerifiedBy(String licenseVerifiedBy) { this.licenseVerifiedBy = licenseVerifiedBy; return this; }
        public Builder taxIdType(String taxIdType) { this.taxIdType = taxIdType; return this; }
        public Builder taxId(String taxId) { this.taxId = taxId; return this; }
        public Builder legalName(String legalName) { this.legalName = legalName; return this; }
        public Builder ivaConditionId(Integer ivaConditionId) { this.ivaConditionId = ivaConditionId; return this; }
        public Builder fiscalAddress(String fiscalAddress) { this.fiscalAddress = fiscalAddress; return this; }

        public Usuario build() {
            return new Usuario(
                    id, version, email, password, rol, fechaRegistro, emailVerificado,
                    codigoVerificacion, codigoVerificacionExpiresAt, resetPasswordCode,
                    resetPasswordExpiresAt, mustChangePassword, terminosAceptadosEn,
                    perfilCompleto, cuentaEliminada, anonimizadoEn, notificacionesEmailHabilitadas,
                    recibirNovedades, nombre, apellido, telefono, sexo, fechaNacimiento,
                    tipoDocumento, numeroDocumento, dni, cuil, direccion, fotoUrl,
                    domicilioCalle, domicilioNumero, domicilioPiso, domicilioDpto,
                    domicilioCodigoPostal, domicilioLocalidad, domicilioProvincia, domicilioPais,
                    obraSocial, numAfiliado, datosOfuscado, credencialCodEntidad,
                    credencialPan, credencialPlan, credencialToken, verificadoAdmin,
                    profession, licenseType, licenseNumber, licenseJurisdiction,
                    licenseDocumentUrl, licenseVerifiedAt, licenseVerifiedBy, taxIdType,
                    taxId, legalName, ivaConditionId, fiscalAddress
            );
        }
    }
}
