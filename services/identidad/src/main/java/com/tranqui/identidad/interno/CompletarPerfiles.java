package com.tranqui.identidad.interno;

import com.tranqui.identidad.datos.PerfilesRegistro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fase 11: los profesionales que vienen de main no tienen fila en perfil_registro (solo la llena el registro y, desde
 * #101, profesional.actualizado), y el panel admin los mostraba con el perfil incompleto. Se completan con el perfil
 * que tiene profesionales; los que ya tienen fila no se tocan (los mantiene el evento).
 */
@Component
public class CompletarPerfiles {

    private static final Logger log = LoggerFactory.getLogger(CompletarPerfiles.class);

    private final ClienteInterno interno;
    private final PerfilesRegistro perfiles;

    public CompletarPerfiles(ClienteInterno interno, PerfilesRegistro perfiles) {
        this.interno = interno;
        this.perfiles = perfiles;
    }

    // Periódica y no al arrancar: así no depende de que profesionales esté arriba antes que identidad.
    @Scheduled(initialDelayString = "${identidad.completar-perfiles.demora-ms:60000}",
            fixedDelayString = "${identidad.completar-perfiles.cada-ms:21600000}")
    public void completar() {
        java.util.List<ClienteInterno.PerfilProfesional> lista;
        try {
            lista = interno.perfilesProfesionales();
        } catch (RuntimeException e) {
            log.warn("No se pudieron pedir los perfiles a profesionales (se reintenta): {}", e.getMessage());
            return;
        }
        int completados = 0;
        for (ClienteInterno.PerfilProfesional p : lista) {
            if (perfiles.buscarPorUsuarioId(p.profesionalId()).isPresent()) {
                continue;
            }
            try {
                perfiles.guardar(p.profesionalId(), p.perfil());
                completados++;
            } catch (RuntimeException e) {
                // Por ejemplo, un profesional que todavía no está en identidad (FK a usuario): se reintenta en la próxima.
                log.warn("No se pudo completar el perfil de {}: {}", p.profesionalId(), e.getMessage());
            }
        }
        if (completados > 0) {
            log.info("Perfiles profesionales completados desde profesionales: {}", completados);
        }
    }
}
