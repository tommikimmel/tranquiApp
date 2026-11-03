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
}
