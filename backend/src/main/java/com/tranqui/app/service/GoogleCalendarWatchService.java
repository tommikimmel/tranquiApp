package com.tranqui.app.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Channel;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

// Registers/renews/cancels a Google Calendar push notification channel (events.watch) per
// médico so Google can tell us about calendar changes as they happen, instead of only finding
// out on the next GoogleCalendarPollingScheduler run. That poller keeps running regardless —
// this is an additional, faster trigger for the exact same GoogleCalendarSyncService method,
// and doubles as the safety net if a push notification is ever missed or the channel lapses.
@Service
public class GoogleCalendarWatchService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarWatchService.class);

    // Google caps push channels at a maximum lifetime; we ask for less (6 days) and renew with
    // a comfortable margin (see GoogleCalendarWatchScheduler) rather than push right up against
    // whatever Google's actual internal limit is today.
    private static final long CHANNEL_LIFETIME_MILLIS = 6L * 24 * 60 * 60 * 1000;

    @Value("${google.calendar.enabled:false}")
    private boolean calendarEnabled;

    @Value("${google.calendar.watch.enabled:false}")
    private boolean watchEnabled;

    @Value("${app.public-url:http://localhost:8081}")
    private String appPublicUrl;

    @Value("${security.encryption.key:masterdecryptionkey32charspart12}")
    private String channelTokenSigningKey;

    @Autowired
    private GoogleCalendarOAuthService googleCalendarOAuthService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public boolean isWatchEnabled() {
        return calendarEnabled && watchEnabled;
    }

    @Transactional
    public void registrarCanal(Usuario medico) {
        if (!isWatchEnabled()) {
            return;
        }
        boolean medicoConectado = medico.getGoogleCalendarConnected() != null && medico.getGoogleCalendarConnected();
        if (!medicoConectado) {
            return;
        }

        String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
        if (accessToken == null) {
            log.warn("No se pudo obtener Access Token para registrar el canal de Google Calendar del médico ID {}", medico.getId());
            return;
        }

        try {
            Calendar service = GoogleCalendarService.construirCliente(accessToken);
            String calendarId = medico.getGoogleCalendarId() != null ? medico.getGoogleCalendarId() : "primary";

            Channel request = new Channel()
                    .setId(UUID.randomUUID().toString())
                    .setType("web_hook")
                    .setAddress(appPublicUrl + "/api/medicos/google-calendar/webhook")
                    .setToken(signChannelToken(medico.getId()))
                    .setExpiration(System.currentTimeMillis() + CHANNEL_LIFETIME_MILLIS);

            Channel response = service.events().watch(calendarId, request).execute();

            medico.setGoogleWatchChannelId(response.getId());
            medico.setGoogleWatchResourceId(response.getResourceId());
            medico.setGoogleWatchExpiresAt(response.getExpiration() != null
                    ? java.time.Instant.ofEpochMilli(response.getExpiration()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now().plusDays(6));
            medico.setGoogleWatchActive(true);
            usuarioRepository.save(medico);

            log.info("Canal de Google Calendar registrado para médico ID {} (channelId={})", medico.getId(), response.getId());
        } catch (Exception e) {
            // Registering the watch channel is a best-effort enhancement on top of the polling
            // sync — never let a failure here (e.g. APP_PUBLIC_URL not HTTPS yet, quota, médico
            // token revoked) block connecting Google Calendar or break anything else.
            log.error("Fallo al registrar el canal de Google Calendar para médico ID {}: {}", medico.getId(), e.getMessage());
        }
    }

    @Transactional
    public void detenerCanal(Usuario medico) {
        if (medico.getGoogleWatchChannelId() == null || medico.getGoogleWatchResourceId() == null) {
            return;
        }
        try {
            String accessToken = googleCalendarOAuthService.obtenerAccessToken(medico);
            if (accessToken != null) {
                Calendar service = GoogleCalendarService.construirCliente(accessToken);
                Channel channel = new Channel()
                        .setId(medico.getGoogleWatchChannelId())
                        .setResourceId(medico.getGoogleWatchResourceId());
                service.channels().stop(channel).execute();
            }
        } catch (Exception e) {
            // The channel may already be expired/gone on Google's side (e.g. 404) — that's the
            // desired end state anyway, so just log and keep clearing our local record.
            log.warn("No se pudo detener el canal de Google Calendar del médico ID {} (puede que ya haya expirado): {}", medico.getId(), e.getMessage());
        } finally {
            medico.setGoogleWatchChannelId(null);
            medico.setGoogleWatchResourceId(null);
            medico.setGoogleWatchExpiresAt(null);
            medico.setGoogleWatchActive(false);
            usuarioRepository.save(medico);
        }
    }

    @Transactional
    public void renovarCanal(Usuario medico) {
        detenerCanal(medico);
        registrarCanal(medico);
    }

    // The channel token Google echoes back on every webhook POST (X-Goog-Channel-Token) — same
    // HMAC scheme as GoogleCalendarOAuthService's OAuth "state", just scoped to this concern, so
    // the webhook handler can trust a notification actually corresponds to a channel we created
    // for this médico without needing session/auth on a public endpoint.
    public String signChannelToken(Long medicoId) {
        String payload = String.valueOf(medicoId);
        return payload + "." + hmac(payload);
    }

    public Long verificarChannelToken(String token) {
        if (token == null || !token.contains(".")) {
            return null;
        }
        int sep = token.lastIndexOf('.');
        String payload = token.substring(0, sep);
        String signature = token.substring(sep + 1);
        if (!constantTimeEquals(hmac(payload), signature)) {
            return null;
        }
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hmac(String payload) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(channelTokenSigningKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Fallo al firmar el token de canal de Google Calendar", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
