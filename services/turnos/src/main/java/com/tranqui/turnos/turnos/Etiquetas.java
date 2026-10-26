package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Turno;

/** Los textos de tipo de turno que usaba el monolito (agenda, mails y eventos), sin cambios. */
public final class Etiquetas {

    private Etiquetas() {}

    /** {@code TurnoService#resolverTypeLabel}: la agenda, los recordatorios y los documentos enviados. */
    public static String tipo(Turno t) {
        if ("receta-fuera".equals(t.servicioId)) return "Receta fuera de turno";
        if ("certificado".equals(t.servicioId)) return "Certificado";
        if ("OBRA_SOCIAL".equals(t.tipo) || "OSDE".equals(t.tipo)) return "Obra Social";
        if (!t.ocupaAgenda) return "Documento solicitado";
        return "Consulta particular";
    }

    /** {@code PagoWebhookHandler#resolverTipoTurnoLabel}: el turno confirmado por un pago. */
    public static String tipoTurno(Turno t) {
        if ("OBRA_SOCIAL".equals(t.tipo) || "OSDE".equals(t.tipo)) return "Obra Social";
        if ("SOBRETUNO".equals(t.tipo)) return "Sobreturno";
        return "Consulta particular";
    }

    /** {@code PagoWebhookHandler#resolverTipoDocumentoLabel}: un documento pago. */
    public static String tipoDocumento(Turno t) {
        if ("receta-fuera".equals(t.servicioId)) return "una receta";
        if ("certificado".equals(t.servicioId)) return "un certificado";
        if ("RECETA".equals(t.tipo)) return "una receta";
        if ("CERTIFICADO".equals(t.tipo)) return "un certificado";
        return "un documento";
    }

    /** {@code MercadoPagoService#tituloCobroTurno}: el ítem del checkout. */
    public static String tituloCobro(String nombreProfesional) {
        return "Consulta Psiquiátrica - " + nombreProfesional;
    }
}
