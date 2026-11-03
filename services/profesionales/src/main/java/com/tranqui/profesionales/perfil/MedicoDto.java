package com.tranqui.profesionales.perfil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

/**
 * El mismo JSON que {@code MedicoDto} del monolito (el buscador, "Mi perfil" y lo que manda el frontend al
 * guardarlo): el frontend no cambia con la Fase 5.
 */
public record MedicoDto(
        Long id,
        String name,
        String nombre,
        String email,
        String emailContacto,
        String telefono,
        String initials,
        String degree,
        String specialty,
        String profession,
        String matricula,
        String cuit,
        BigDecimal price,
        List<String> tags,
        String color,
        String fotoUrl,
        boolean ofreceOnline,
        boolean ofrecePresencial,
        List<TarifaDto> tariffs,
        String apellido,
        String sexo,
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        LocalDate fechaNacimiento,
        Long cuil,
        String tipoDocumento,
        Integer numeroDocumento,
        String domicilioAtencion,
        Double domicilioLat,
        Double domicilioLng,
        String domicilioAtencionTorre,
        String domicilioAtencionPiso,
        String domicilioAtencionDepto,
        String domicilioAtencionBarrio,
        MatriculaInfoDto matriculaInfo,
        boolean verificado,
        Boolean verificadoAdmin,
        String experiencia,
        String publicaciones,
        RedesSocialesDto redesSociales,
        String descripcionPerfil,
        List<String> pacientesAtiende,
        String institucionFormacion,
        Integer aniosExperiencia,
        Integer duracionTurnoMinutos,
        Integer intervaloEntreTurnosMinutos,
        String codigoRefeps,
        String selloLinea1,
        String selloLinea2,
        String selloLinea3
) {

    public record RedesSocialesDto(String instagram, String facebook, String linkedin, String sitioWeb) {}

    public record MatriculaInfoDto(String tipo, String provincia, Integer numero, EspecialidadDto especialidad,
                                   AsociadaDto asociada) {}

    public record EspecialidadDto(String textoLibre) {}

    public record AsociadaDto(String tipo, String provincia, Integer numero) {}

    public record TarifaDto(String id, String label, BigDecimal price, boolean enabled, boolean requiereObraSocial,
                            String obraSocial, BigDecimal precioOnline, BigDecimal precioPresencial,
                            boolean requiereAgenda, boolean esReceta) {}

    /** {@code DisponibilidadDto} del monolito: una franja de la grilla de "Agenda". */
    public record DisponibilidadDto(Integer diaSemana, String horaInicio, String horaFin) {}
}
