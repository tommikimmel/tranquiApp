package com.tranqui.profesionales.clinica;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTOs de historia clínica, ficha de pacientes y conceptos (documentos pagos).
 * El mismo JSON y anidamiento que respondía el monolito: el frontend no cambia.
 */
public final class Dtos {

    private Dtos() {}

    public record DomicilioDto(
            String calle,
            String numero,
            String piso,
            String dpto,
            String codigoPostal,
            String localidad,
            String provincia,
            String pais
    ) {}

    public record CredencialInfoDto(
            Integer codEntidad,
            String pan,
            String plan,
            String token
    ) {}

    public record PacienteDto(
            Long id,
            String nombre,
            String email,
            String telefono,
            String dni,
            String direccion,
            String obraSocial,
            String numAfiliado,
            String ultimaVisita,
            String prioridadClinica,
            boolean sinTurno,
            int unreadMessagesCount,
            String apellido,
            String sexo,
            String fechaNacimiento,
            Long cuil,
            String mail,
            String tipoDocumento,
            Integer numeroDocumento,
            String datosOfuscado,
            CredencialInfoDto credencial,
            DomicilioDto domicilio
    ) {}

    public record MedicoRef(Long id) {}

    public record SeguimientoRequest(
            LocalDate fecha,
            String estadoAnimo,
            String sintomas,
            String notas,
            MedicoRef medico
    ) {}

    public record SeguimientoResponse(
            Long id,
            LocalDate fecha,
            String estadoAnimo,
            String sintomas,
            String notas,
            LocalDateTime fechaCreacion,
            String estado,
            String motivo,
            String hashIntegridad,
            @JsonProperty("nombreMedico") String nombreMedico
    ) {}

    public record InformeClinicoRequest(
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            String motivo
    ) {}

    public record InformeClinicoResponse(
            Long id,
            LocalDate fecha,
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            LocalDateTime fechaCreacion,
            String estado,
            Long informeOriginalId,
            String motivo,
            String hashIntegridad,
            @JsonProperty("nombreMedico") String nombreMedico
    ) {}

    public record SolicitarConceptoDto(
            Long medicoId,
            String tipoConcepto,
            BigDecimal precio
    ) {}

    public record PagoResponseDto(
            Long id,
            String checkoutUrl
    ) {}

    public record UsuarioResumen(
            Long id,
            String nombre,
            String apellido,
            String email,
            String telefono,
            String dni
    ) {}

    public record SolicitudResponse(
            Long id,
            UsuarioResumen paciente,
            UsuarioResumen medico,
            String tipoConcepto,
            BigDecimal precio,
            String estado,
            String transactionId,
            boolean emitido,
            String urlDescarga,
            LocalDateTime fechaEmision,
            LocalDateTime fechaCreacion
    ) {}
}
