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

    // ==================== Profesionales ====================
    public static final String PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1 = "profesionales.profesional.actualizado.v1";
    public static final String PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1 = "profesionales.profesional.visibilidad-cambiada.v1";
    public static final String PROFESIONALES_TARIFA_ACTUALIZADA_V1 = "profesionales.tarifa.actualizada.v1";
    public static final String PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V1 = "profesionales.disponibilidad.actualizada.v1";
    public static final String PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1 = "profesionales.historia-clinica.actualizada.v1";

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

    // ==================== Pagos ====================
    public static final String PAGOS_PAGO_APROBADO_V1 = "pagos.pago.aprobado.v1";
    public static final String PAGOS_PAGO_RECHAZADO_V1 = "pagos.pago.rechazado.v1";
    public static final String PAGOS_REEMBOLSO_REALIZADO_V1 = "pagos.reembolso.realizado.v1";
    public static final String PAGOS_CUENTA_MP_VINCULADA_V1 = "pagos.cuenta-mp.vinculada.v1";
    public static final String PAGOS_CUENTA_MP_DESVINCULADA_V1 = "pagos.cuenta-mp.desvinculada.v1";

    // ==================== Suscripciones ====================
    public static final String SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V1 = "suscripciones.suscripcion.cambio-estado.v1";
    public static final String SUSCRIPCIONES_AVISO_PROGRAMADO_V1 = "suscripciones.aviso.programado.v1";

    // ==================== Calendario ====================
    public static final String CALENDARIO_MEET_CREADO_V1 = "calendario.meet.creado.v1";
    public static final String CALENDARIO_HORARIO_BLOQUEADO_V1 = "calendario.horario.bloqueado.v1";

    // ==================== Chat ====================
    public static final String CHAT_MENSAJE_ENVIADO_V1 = "chat.mensaje.enviado.v1";

    /**
     * Lista inmutable de todos los tópicos del catálogo de la sección 5.4, más los agregados en la Fase 1 (notificaciones).
     */
    public static final List<String> TODOS_LOS_TOPICOS = List.of(
        IDENTIDAD_USUARIO_REGISTRADO_V1,
        IDENTIDAD_USUARIO_ACTUALIZADO_V1,
        IDENTIDAD_USUARIO_ELIMINADO_V1,
        IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
        IDENTIDAD_TICKET_RESPONDIDO_V1,
        IDENTIDAD_TICKET_CREADO_V1,
        IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1,
        PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
        PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
        PROFESIONALES_TARIFA_ACTUALIZADA_V1,
        PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V1,
        PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1,
        TURNOS_TURNO_RESERVADO_V1,
        TURNOS_TURNO_CONFIRMADO_V1,
        TURNOS_TURNO_CANCELADO_V1,
        TURNOS_TURNO_REPROGRAMADO_V1,
        TURNOS_TURNO_EXPIRADO_V1,
        TURNOS_RECORDATORIO_PROGRAMADO_V1,
        TURNOS_DOCUMENTO_SOLICITADO_V1,
        TURNOS_DOCUMENTO_ENVIADO_V1,
        TURNOS_ASISTENCIA_CONFIRMADA_V1,
        PAGOS_PAGO_APROBADO_V1,
        PAGOS_PAGO_RECHAZADO_V1,
        PAGOS_REEMBOLSO_REALIZADO_V1,
        PAGOS_CUENTA_MP_VINCULADA_V1,
        PAGOS_CUENTA_MP_DESVINCULADA_V1,
        SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V1,
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
