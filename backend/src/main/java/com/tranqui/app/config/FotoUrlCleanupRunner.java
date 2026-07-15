package com.tranqui.app.config;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.util.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time-effective cleanup (runs every startup, but a no-op once photos are already
 * normalized) for profile photos saved before MedicoService/AuthController started enforcing a
 * max size. Some existing fotoUrl values were several megabytes of inline base64 — a phone
 * photo plus embedded EXIF/C2PA provenance metadata — which made /api/medicos slow to transfer
 * on every homepage load since it embeds every professional's photo inline. Resizes/re-encodes
 * anything over the threshold in place.
 */
@Component
public class FotoUrlCleanupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FotoUrlCleanupRunner.class);
    private static final long MAX_FOTO_BYTES = 3L * 1024 * 1024;
    private static final int MAX_DIMENSION = 640;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public void run(String... args) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        int cleaned = 0;
        for (Usuario u : usuarios) {
            String fotoUrl = u.getFotoUrl();
            if (fotoUrl == null) continue;

            long sizeBefore = ImageUtils.decodedByteSize(fotoUrl);
            if (sizeBefore <= MAX_FOTO_BYTES) continue;

            String resized = ImageUtils.resizeIfNeeded(fotoUrl, MAX_DIMENSION, MAX_FOTO_BYTES);
            if (!resized.equals(fotoUrl)) {
                u.setFotoUrl(resized);
                usuarioRepository.save(u);
                cleaned++;
                log.info("Redujo la foto de perfil del usuario ID {} de {} KB a {} KB",
                        u.getId(), sizeBefore / 1024, ImageUtils.decodedByteSize(resized) / 1024);
            } else {
                log.warn("No se pudo reducir la foto de perfil del usuario ID {} ({} KB) — se dejó sin cambios.",
                        u.getId(), sizeBefore / 1024);
            }
        }
        if (cleaned > 0) {
            log.info("Limpieza de fotos de perfil: {} imagen(es) redimensionada(s).", cleaned);
        }
    }
}
