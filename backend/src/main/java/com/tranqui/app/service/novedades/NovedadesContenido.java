package com.tranqui.app.service.novedades;

import java.util.List;

/**
 * Contenido de un envío de novedades. Un solo texto dividido por público: cada destinatario recibe
 * "general" más la sección de su rol. Las secciones vacías se omiten.
 *
 * @param id            identificador del envío; hace idempotente la operación (ej. "mant-2026-10-08")
 * @param titulo        título del mail (también es el asunto)
 * @param general       viñetas para todos
 * @param pacientes     viñetas solo para pacientes
 * @param profesionales viñetas solo para profesionales
 */
public record NovedadesContenido(String id, String titulo, List<String> general,
                                 List<String> pacientes, List<String> profesionales) {

    public enum Publico { PACIENTES, PROFESIONALES }

    public NovedadesContenido {
        general = limpiar(general);
        pacientes = limpiar(pacientes);
        profesionales = limpiar(profesionales);
    }

    /** Viñetas que recibe un público: las generales y después las de su sección. */
    public List<String> itemsPara(Publico publico) {
        List<String> propias = publico == Publico.PACIENTES ? pacientes : profesionales;
        return java.util.stream.Stream.concat(general.stream(), propias.stream()).toList();
    }

    public boolean tieneContenidoPara(Publico publico) {
        return !itemsPara(publico).isEmpty();
    }

    private static List<String> limpiar(List<String> items) {
        if (items == null) return List.of();
        return items.stream().filter(i -> i != null && !i.isBlank()).map(String::trim).toList();
    }
}
