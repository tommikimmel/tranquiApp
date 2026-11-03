package com.tranqui.eventos.data;

import java.math.BigDecimal;

/**
 * Fase 5: los campos del perfil que administra el servicio de profesionales (todo lo que escribe
 * {@code PUT /api/medicos/perfil} menos los datos de identidad, que siguen en el monolito). Viaja dentro
 * de {@link ProfesionalActualizadoData#perfil()} para que el monolito mantenga su copia; no lleva datos de
 * salud ni la foto.
 */
public record PerfilProfesionalData(
    String emailContacto,
    String domicilioAtencion,
    Double domicilioLat,
    Double domicilioLng,
    String domicilioAtencionTorre,
    String domicilioAtencionPiso,
    String domicilioAtencionDepto,
    String domicilioAtencionBarrio,
    String matricula,
    String matriculaTipo,
    String matriculaProvincia,
    Integer matriculaNumero,
    String matriculaEspecialidad,
    String matriculaAsocTipo,
    String matriculaAsocProvincia,
    Integer matriculaAsocNumero,
    String titulo,
    String especialidad,
    String cuit,
    BigDecimal precio,
    String color,
    boolean ofreceOnline,
    boolean ofrecePresencial,
    String experiencia,
    String publicaciones,
    String instagramUrl,
    String facebookUrl,
    String linkedinUrl,
    String sitioWebUrl,
    String codigoRefeps,
    String selloLinea1,
    String selloLinea2,
    String selloLinea3,
    String tags,
    String descripcionPerfil,
    String institucionFormacion,
    Integer aniosExperiencia,
    String pacientesAtiende,
    Integer duracionTurnoMinutos,
    Integer intervaloEntreTurnosMinutos
) {}
