package com.tranqui.turnos.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El mismo JSON que los DTOs de turnos del monolito ({@code ReservaTurnoDto}, {@code TurnoResponseDto},
 * {@code TurnoMedicoDto}, {@code PacienteDto}, {@code MarcarDocumentoEnviadoRequest}): el frontend no cambia.
 */
public final class Dtos {

    private Dtos() {}

    public record ReservaTurnoDto(Long medicoId, LocalDate fecha, LocalTime hora, String tipo, String modalidad, String servicioId,
                                  String obraSocial, String idFinanciador, String metadataAfiliado, String nombrePaciente,
                                  String emailPaciente, String telefonoPaciente) {}

    public record TurnoResponseDto(Long turnoId, String estado, String attendanceStatus, LocalDate fecha, LocalTime horaInicio,
                                   BigDecimal precio, String checkoutUrl, Boolean checkoutPendiente, String tokenReserva,
                                   String meetLink) {}

    public record TurnoMedicoDto(Long id, String patientName, String hour, String horaInicio, String ampm, String type,
                                 String modalidad, String status, String attendanceStatus, String meetLink, String fecha,
                                 String checkoutUrl, boolean firstConsultation, PacienteDto patientInfo, String domicilioAtencion,
                                 Double domicilioLat, Double domicilioLng, String domicilioAtencionTorre,
                                 String domicilioAtencionPiso, String domicilioAtencionDepto, String domicilioAtencionBarrio,
                                 String metadataAfiliado, boolean ocupaAgenda, boolean documentoEnviado, boolean esReceta) {}

    public record PacienteDto(Long id, String nombre, String email, String telefono, String dni, String direccion,
                              String obraSocial, String numAfiliado, String ultimaVisita, String prioridadClinica, boolean sinTurno,
                              int unreadMessagesCount, String apellido, String sexo, String fechaNacimiento, Long cuil, String mail,
                              String tipoDocumento, Integer numeroDocumento, String datosOfuscado, CredencialInfoDto credencial,
                              DomicilioDto domicilio) {}

    public record DomicilioDto(String calle, String numero, String piso, String dpto, String codigoPostal, String localidad,
                               String provincia, String pais) {}

    public record CredencialInfoDto(Integer codEntidad, String pan, String plan, String token) {}

    public record MarcarDocumentoEnviadoRequest(String archivoData, String archivoNombre) {}

    public record DashboardStatsDto(int sessionsToday, String sessionsTodayChange, BigDecimal earningsThisWeek,
                                    String earningsThisWeekChange, int activePatients, String activePatientsChange,
                                    int noShowsThisMonth, String noShowsChange) {}
}
