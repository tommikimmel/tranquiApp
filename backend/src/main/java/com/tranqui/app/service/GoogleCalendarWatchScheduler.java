package com.tranqui.app.service;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Google Calendar push channels (events.watch) aren't renewable in place — Google only lets you
// stop one and start a new one. This runs daily and renews any channel expiring within 48h so a
// médico's real-time sync doesn't silently lapse into "only the 5-minute poller" without anyone
// noticing.
@Component
public class GoogleCalendarWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarWatchScheduler.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GoogleCalendarWatchService googleCalendarWatchService;

    @Scheduled(cron = "0 0 4 * * ?", zone = "America/Argentina/Cordoba")
    public void renovarCanalesPorVencer() {
        if (!googleCalendarWatchService.isWatchEnabled()) {
            return;
        }
        LocalDateTime limite = LocalDateTime.now().plusHours(48);
        List<Usuario> medicos = usuarioRepository.findByGoogleWatchActiveTrueAndGoogleWatchExpiresAtBefore(limite);
        for (Usuario medico : medicos) {
            try {
                googleCalendarWatchService.renovarCanal(medico);
            } catch (Exception e) {
                log.error("Fallo al renovar el canal de Google Calendar del médico ID {}. Se reintentará mañana.", medico.getId(), e);
            }
        }
    }
}
