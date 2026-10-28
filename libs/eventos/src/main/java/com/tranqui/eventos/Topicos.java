package com.tranqui.eventos;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Catálogo de tópicos de eventos según la sección 5.4 del plan de microservicios.
 * Formato obligatorio: <dominio>.<entidad>.<evento>.v<n>
 */
public final class Topicos {

    private Topicos() {}

    /**
     * Expresión regular que valida el formato <dominio>.<entidad>.<evento>.v<n> en minúsculas.
     */
    public static final Pattern PATRON_TOPICO = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+){2}\\.v[0-9]+$");

    // ==================== Identidad ====================
    public static final String IDENTIDAD_USUARIO_REGISTRADO_V1 = "identidad.usuario.registrado.v1";
    public static final String IDENTIDAD_USUARIO_ACTUALIZADO_V1 = "identidad.usuario.actualizado.v1";
    public static final String IDENTIDAD_USUARIO_ELIMINADO_V1 = "identidad.usuario.eliminado.v1";
    public static final String IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1 = "identidad.codigo-verificacion.generado.v1";
    public static final String IDENTIDAD_TICKET_RESPONDIDO_V1 = "identidad.ticket.respondido.v1";
    public static final String IDENTIDAD_TICKET_CREADO_V1 = "identidad.ticket.creado.v1";
    public static final String IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1 = "identidad.ticket.mensaje-recibido.v1";
    /** Fase 8: el admin resolvió la verificación de matrícula de un profesional (VERIFICADO o RECHAZADO). */
    public static final String IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1 = "identidad.profesional.verificacion-resuelta.v1";
    /** Fase 9: el admin generó una contraseña temporal para un usuario. */
    public static final String IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1 = "identidad.password-temporal.generada.v1";
    /** Fase 9: el usuario solicitó una copia de sus datos personales. */
    public static final String IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1 = "identidad.soporte.solicitud-datos.v1";

    // ==================== Profesionales ====================
    public static final String PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1 = "profesionales.profesional.actualizado.v1";
    public static final String PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1 = "profesionales.profesional.visibilidad-cambiada.v1";
    public static final String PROFESIONALES_TARIFA_ACTUALIZADA_V1 = "profesionales.tarifa.actualizada.v1";
    public static final String PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V1 = "profesionales.disponibilidad.actualizada.v1";
    public static final String PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1 = "profesionales.historia-clinica.actualizada.v1";
    /** Fase 4: un paciente pidió un documento pago (API de conceptos); pagos crea la preferencia. */
    public static final String PROFESIONALES_CONCEPTO_SOLICITADO_V1 = "profesionales.concepto.solicitado.v1";
    /** Fase 5: la disponibilidad completa del profesional (el v1 iba franja por franja). */
    public static final String PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2 = "profesionales.disponibilidad.actualizada.v2";
    /** Fase 5: todas las tarifas del profesional (el v1 iba tarifa por tarifa y sin los servicios). */
    public static final String PROFESIONALES_TARIFA_ACTUALIZADA_V2 = "profesionales.tarifa.actualizada.v2";
    /** Fase 6: el profesional emitió recetas a un paciente (turnos marca los turnos de receta como enviados). */
    public static final String PROFESIONALES_RECETA_EMITIDA_V1 = "profesionales.receta.emitida.v1";

    // ==================== Turnos ====================
    public static final String TURNOS_TURNO_RESERVADO_V1 = "turnos.turno.reservado.v1";
    public static final String TURNOS_TURNO_CONFIRMADO_V1 = "turnos.turno.confirmado.v1";
    public static final String TURNOS_TURNO_CANCELADO_V1 = "turnos.turno.cancelado.v1";
    public static final String TURNOS_TURNO_REPROGRAMADO_V1 = "turnos.turno.reprogramado.v1";
    public static final String TURNOS_TURNO_EXPIRADO_V1 = "turnos.turno.expirado.v1";
    public static final String TURNOS_RECORDATORIO_PROGRAMADO_V1 = "turnos.recordatorio.programado.v1";
    public static final String TURNOS_DOCUMENTO_SOLICITADO_V1 = "turnos.documento.solicitado.v1";
    public static final String TURNOS_DOCUMENTO_ENVIADO_V1 = "turnos.documento.enviado.v1";
    public static final String TURNOS_ASISTENCIA_CONFIRMADA_V1 = "turnos.asistencia.confirmada.v1";
    /** Fase 6: el turno completo en cada cambio, para la copia del monolito. */
    public static final String TURNOS_TURNO_ACTUALIZADO_V1 = "turnos.turno.actualizado.v1";

    // ==================== Pagos ====================
    public static final String PAGOS_PAGO_APROBADO_V1 = "pagos.pago.aprobado.v1";
    public static final String PAGOS_PAGO_RECHAZADO_V1 = "pagos.pago.rechazado.v1";
    public static final String PAGOS_REEMBOLSO_REALIZADO_V1 = "pagos.reembolso.realizado.v1";
    public static final String PAGOS_CUENTA_MP_VINCULADA_V1 = "pagos.cuenta-mp.vinculada.v1";
    public static final String PAGOS_CUENTA_MP_DESVINCULADA_V1 = "pagos.cuenta-mp.desvinculada.v1";
    public static final String PAGOS_CONCEPTO_APROBADO_V1 = "pagos.concepto.aprobado.v1";
    /** Fase 4: el servicio de pagos creó la preferencia (checkout) de un turno o un documento. */
    public static final String PAGOS_CHECKOUT_CREADO_V1 = "pagos.checkout.creado.v1";
    /** Fase 4: Mercado Pago no aceptó un reembolso que correspondía; queda para revisar a mano. */
    public static final String PAGOS_REEMBOLSO_FALLIDO_V1 = "pagos.reembolso.fallido.v1";

    // ==================== Suscripciones ====================
    /**
     * @deprecated Fase 8 (S1): reemplazado por {@link #SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2}. Nadie lo consume.
     */
    @Deprecated
    public static final String SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V1 = "suscripciones.suscripcion.cambio-estado.v1";
    /** Fase 8: estado de la suscripción con flags calculados de acceso y fechas de período y gracia. */
    public static final String SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2 = "suscripciones.suscripcion.cambio-estado.v2";
    public static final String SUSCRIPCIONES_AVISO_PROGRAMADO_V1 = "suscripciones.aviso.programado.v1";

    // ==================== Calendario ====================
    public static final String CALENDARIO_MEET_CREADO_V1 = "calendario.meet.creado.v1";
    public static final String CALENDARIO_HORARIO_BLOQUEADO_V1 = "calendario.horario.bloqueado.v1";

    // ==================== Chat ====================
    public static final String CHAT_MENSAJE_ENVIADO_V1 = "chat.mensaje.enviado.v1";

    /**
     * Lista inmutable de todos los tópicos del catálogo de la sección 5.4, más los agregados en las Fases 1 (notificaciones), 2 (chat), 4 (pagos), 5 (profesionales) y 6 (turnos).
     */
    public static final List<String> TODOS_LOS_TOPICOS = List.of(
        IDENTIDAD_USUARIO_REGISTRADO_V1,
        IDENTIDAD_USUARIO_ACTUALIZADO_V1,
        IDENTIDAD_USUARIO_ELIMINADO_V1,
        IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
        IDENTIDAD_TICKET_RESPONDIDO_V1,
        IDENTIDAD_TICKET_CREADO_V1,
        IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1,
        IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1,
        IDENTIDAD_PASSWORD_TEMPORAL_GENERADA_V1,
        IDENTIDAD_SOPORTE_SOLICITUD_DATOS_V1,
        PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
        PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
        PROFESIONALES_TARIFA_ACTUALIZADA_V1,
        PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V1,
        PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1,
        PROFESIONALES_CONCEPTO_SOLICITADO_V1,
        PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2,
        PROFESIONALES_TARIFA_ACTUALIZADA_V2,
        PROFESIONALES_RECETA_EMITIDA_V1,
        TURNOS_TURNO_RESERVADO_V1,
        TURNOS_TURNO_CONFIRMADO_V1,
        TURNOS_TURNO_CANCELADO_V1,
        TURNOS_TURNO_REPROGRAMADO_V1,
        TURNOS_TURNO_EXPIRADO_V1,
        TURNOS_RECORDATORIO_PROGRAMADO_V1,
        TURNOS_DOCUMENTO_SOLICITADO_V1,
        TURNOS_DOCUMENTO_ENVIADO_V1,
        TURNOS_ASISTENCIA_CONFIRMADA_V1,
        TURNOS_TURNO_ACTUALIZADO_V1,
        PAGOS_PAGO_APROBADO_V1,
        PAGOS_PAGO_RECHAZADO_V1,
        PAGOS_REEMBOLSO_REALIZADO_V1,
        PAGOS_CUENTA_MP_VINCULADA_V1,
        PAGOS_CUENTA_MP_DESVINCULADA_V1,
        PAGOS_CONCEPTO_APROBADO_V1,
        PAGOS_CHECKOUT_CREADO_V1,
        PAGOS_REEMBOLSO_FALLIDO_V1,
        SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V1,
        SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2,
        SUSCRIPCIONES_AVISO_PROGRAMADO_V1,
        CALENDARIO_MEET_CREADO_V1,
        CALENDARIO_HORARIO_BLOQUEADO_V1,
        CHAT_MENSAJE_ENVIADO_V1
    );

    /**
     * Construye un nombre de tópico combinando eventType y número de versión.
     * Ejemplo: armarTopico("turnos.turno.confirmado", 1) -> "turnos.turno.confirmado.v1"
     */
    public static String armarTopico(String eventType, int version) {
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("El eventType no puede ser nulo ni vacío");
        }
        String limpio = eventType.trim();
        if (limpio.matches(".*\\.v\\d+$")) {
            return limpio;
        }
        return limpio + ".v" + version;
    }

    /**
     * Valida si una cadena respeta la convención de formato de tópicos.
     */
    public static boolean esValido(String topico) {
        return topico != null && PATRON_TOPICO.matcher(topico.trim()).matches();
    }

    /**
     * Extrae el dominio (primer segmento) de un nombre de tópico.
     * Ejemplo: "turnos.turno.confirmado.v1" -> "turnos"
     */
    public static String extraerDominio(String topico) {
        if (topico == null || topico.trim().isEmpty()) {
            return "monolito";
        }
        int primerPunto = topico.indexOf('.');
        return primerPunto > 0 ? topico.substring(0, primerPunto) : topico;
    }

    /**
     * Extrae el eventType (sin sufijo de versión) de un tópico.
     * Ejemplo: "turnos.turno.confirmado.v1" -> "turnos.turno.confirmado"
     */
    public static String extraerTipoEvento(String topico) {
        if (topico == null || topico.trim().isEmpty()) {
            return "";
        }
        int ultimoPunto = topico.lastIndexOf('.');
        if (ultimoPunto > 0 && topico.substring(ultimoPunto + 1).matches("v\\d+")) {
            return topico.substring(0, ultimoPunto);
        }
        return topico;
    }

    /**
     * Extrae la versión numérica del sufijo de versión de un tópico.
     * Ejemplo: "turnos.turno.confirmado.v1" -> 1
     */
    public static int extraerVersion(String topico) {
        if (topico == null) {
            return 1;
        }
        int ultimoPunto = topico.lastIndexOf('.');
        if (ultimoPunto > 0 && topico.substring(ultimoPunto + 1).matches("v\\d+")) {
            try {
                return Integer.parseInt(topico.substring(ultimoPunto + 2));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }
}
