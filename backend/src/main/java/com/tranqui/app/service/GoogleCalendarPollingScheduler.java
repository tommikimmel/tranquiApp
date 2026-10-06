package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Fase 1 of the Google Calendar read-sync: drives GoogleCalendarSyncService by polling every
// connected médico on a fixed interval. This is the same sincronizarIncremental() a future
// push-webhook trigger will call — adding webhooks later only adds a second trigger, it
// doesn't replace this one (this scheduler stays on as the safety net if a push notification
// is ever missed).
@Component
public class GoogleCalendarPollingScheduler {

    @Value("${google.calendar.enabled:false}")
    private boolean isEnabled;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GoogleCalendarSyncService googleCalendarSyncService;

    @Scheduled(fixedRate = 300000, initialDelay = 10000) // every 5 minutes, first run 10s after boot
    public void sincronizarCalendariosConectados() {
        if (!isEnabled) {
            return;
        }
        List<Usuario> medicosConectados = usuarioRepository.findByRolAndGoogleCalendarConnectedTrue(Rol.PSIQUIATRA);
        for (Usuario medico : medicosConectados) {
            googleCalendarSyncService.sincronizarIncremental(medico);
        }
    }
}
