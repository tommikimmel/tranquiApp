package com.tranqui.app.model.dto;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para la administración de usuarios (AdminController).
 * Expone únicamente los campos requeridos por el panel de administración del frontend,
 * excluyendo explícitamente credenciales, hashes, códigos de verificación y tokens.
 */
public record UsuarioAdminDto(
        Long id,
        String nombre,
        String apellido,
        String email,
        Rol rol,
        String profession,
        Boolean verificadoAdmin,
        String titulo,
        String specialty,
        String matricula,
        String cuit,
        Long cuil,
        String domicilioAtencion,
        String matriculaTipo,
        String matriculaProvincia,
        Integer matriculaNumero,
        String licenseType,
        String licenseNumber,
        String licenseJurisdiction,
        String licenseDocumentUrl,
        LocalDateTime licenseVerifiedAt,
        String taxIdType,
        String taxId,
        String legalName,
        Integer ivaConditionId,
        String fiscalAddress,
        Boolean ofreceOnline,
        Boolean ofrecePresencial,
        String telefono,
        String sexo,
        LocalDate fechaNacimiento,
        String tipoDocumento,
        Integer numeroDocumento,
        String fotoUrl,
        String descripcionPerfil,
        String tags,
        String pacientesAtiende,
        String institucionFormacion,
        Integer aniosExperiencia,
        String experiencia,
        LocalDateTime fechaRegistro
) {
    public static UsuarioAdminDto desde(Usuario u) {
        if (u == null) {
            return null;
        }
        return new UsuarioAdminDto(
                u.getId(),
                u.getNombre(),
                u.getApellido(),
                u.getEmail(),
                u.getRol(),
                u.getProfession(),
                u.getVerificadoAdmin(),
                u.getTitulo(),
                u.getSpecialty(),
                u.getMatricula(),
                u.getCuit(),
                u.getCuil(),
                u.getDomicilioAtencion(),
                u.getMatriculaTipo(),
                u.getMatriculaProvincia(),
                u.getMatriculaNumero(),
                u.getLicenseType(),
                u.getLicenseNumber(),
                u.getLicenseJurisdiction(),
                u.getLicenseDocumentUrl(),
                u.getLicenseVerifiedAt(),
                u.getTaxIdType(),
                u.getTaxId(),
                u.getLegalName(),
                u.getIvaConditionId(),
                u.getFiscalAddress(),
                u.getOfreceOnline(),
                u.getOfrecePresencial(),
                u.getTelefono(),
                u.getSexo(),
                u.getFechaNacimiento(),
                u.getTipoDocumento(),
                u.getNumeroDocumento(),
                u.getFotoUrl(),
                u.getDescripcionPerfil(),
                u.getTags(),
                u.getPacientesAtiende(),
                u.getInstitucionFormacion(),
                u.getAniosExperiencia(),
                u.getExperiencia(),
                u.getFechaRegistro()
        );
    }
}
