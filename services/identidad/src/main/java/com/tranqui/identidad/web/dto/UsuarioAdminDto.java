package com.tranqui.identidad.web.dto;

import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.identidad.datos.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para la administración de usuarios (AdminController), idéntico al del monolito (I1).
 * Expone únicamente los campos requeridos por el panel de administración, excluyendo
 * hashes, códigos de verificación y tokens.
 *
 * <p>Nota: los campos del perfil provienen de perfil_registro (lo que cargó al registrarse);
 * el perfil actualizado vive en profesionales.
 */
public record UsuarioAdminDto(
        Long id,
        String nombre,
        String apellido,
        String email,
        String rol,
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
    public static UsuarioAdminDto desde(Usuario u, PerfilProfesionalData perfil) {
        if (u == null) {
            return null;
        }
        return new UsuarioAdminDto(
                u.id(),
                u.nombre(),
                u.apellido(),
                u.email(),
                u.rol(),
                u.profession(),
                u.verificadoAdmin(),
                perfil != null ? perfil.titulo() : null,
                perfil != null ? perfil.especialidad() : null,
                perfil != null ? perfil.matricula() : null,
                perfil != null ? perfil.cuit() : null,
                u.cuil(),
                perfil != null ? perfil.domicilioAtencion() : null,
                perfil != null ? perfil.matriculaTipo() : null,
                perfil != null ? perfil.matriculaProvincia() : null,
                perfil != null ? perfil.matriculaNumero() : null,
                u.licenseType(),
                u.licenseNumber(),
                u.licenseJurisdiction(),
                u.licenseDocumentUrl(),
                u.licenseVerifiedAt(),
                u.taxIdType(),
                u.taxId(),
                u.legalName(),
                u.ivaConditionId(),
                u.fiscalAddress(),
                perfil != null ? perfil.ofreceOnline() : null,
                perfil != null ? perfil.ofrecePresencial() : null,
                u.telefono(),
                u.sexo(),
                u.fechaNacimiento(),
                u.tipoDocumento(),
                u.numeroDocumento(),
                u.fotoUrl(),
                perfil != null ? perfil.descripcionPerfil() : null,
                perfil != null ? perfil.tags() : null,
                perfil != null ? perfil.pacientesAtiende() : null,
                perfil != null ? perfil.institucionFormacion() : null,
                perfil != null ? perfil.aniosExperiencia() : null,
                perfil != null ? perfil.experiencia() : null,
                u.fechaRegistro()
        );
    }
}
