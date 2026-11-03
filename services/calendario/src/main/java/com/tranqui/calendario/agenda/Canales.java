package com.tranqui.calendario.agenda;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.google.Cifrado;
import com.tranqui.calendario.google.GoogleCalendarApi;
import com.tranqui.calendario.google.TokensGoogle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Canales de notificación push de Google Calendar (antes GoogleCalendarWatchService del monolito):
 * Google avisa por webhook cuando cambia el calendario del profesional. Es un disparador más rápido de
 * la misma sincronización incremental; la pasada cada 5 minutos sigue siendo la red de seguridad.
 * La dirección y el token firmado son los mismos que generaba el monolito, así los canales ya
 * registrados siguen llegando sin renovarlos.
 */
@Service
public class Canales {

    private static final Logger log = LoggerFactory.getLogger(Canales.class);

    // Google limita la vida de un canal; se pide menos (6 días) y se renueva con margen.
    private static final long VIDA_MILLIS = 6L * 24 * 60 * 60 * 1000;

    private final CuentasGoogle cuentas;
    private final TokensGoogle tokens;
    private final GoogleCalendarApi google;
    private final Cifrado cifrado;
    private final boolean habilitados;
    private final String publicUrl;

    public Canales(CuentasGoogle cuentas, TokensGoogle tokens, GoogleCalendarApi google, Cifrado cifrado,
                   @Value("${google.calendar.enabled:false}") boolean googleHabilitado,
                   @Value("${google.calendar.watch.enabled:false}") boolean watchHabilitado,
                   @Value("${app.public-url}") String publicUrl) {
        this.cuentas = cuentas;
        this.tokens = tokens;
        this.google = google;
        this.cifrado = cifrado;
        this.habilitados = googleHabilitado && watchHabilitado;
        this.publicUrl = publicUrl;
    }

    public boolean habilitados() {
        return habilitados;
    }

    public void registrar(Long profesionalId) {
        if (!habilitados) {
            return;
        }
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(profesionalId).orElse(null);
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken == null) {
            log.warn("Sin access token para registrar el canal de Google del profesional {}", profesionalId);
            return;
        }
        try {
            GoogleCalendarApi.Canal canal = google.registrarCanal(accessToken, cuenta.calendario(),
                    publicUrl + "/api/medicos/google-calendar/webhook", cifrado.firmar(profesionalId),
                    System.currentTimeMillis() + VIDA_MILLIS);
            LocalDateTime vence = canal.venceEnMillis() != null
                    ? Instant.ofEpochMilli(canal.venceEnMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now().plusDays(6);
            cuentas.guardarCanal(profesionalId, canal.id(), canal.resourceId(), vence, true);
            log.info("Canal de Google registrado para el profesional {} ({})", profesionalId, canal.id());
        } catch (Exception e) {
            // Mejora sobre la pasada periódica: un fallo acá nunca bloquea la conexión.
            log.error("No se pudo registrar el canal de Google del profesional {}: {}", profesionalId, e.getMessage());
        }
    }

    public void detener(Long profesionalId) {
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(profesionalId).orElse(null);
        if (cuenta == null || cuenta.watchChannelId() == null || cuenta.watchResourceId() == null) {
            return;
        }
        try {
            String accessToken = tokens.accessToken(cuenta);
            if (accessToken != null) {
                google.detenerCanal(accessToken, cuenta.watchChannelId(), cuenta.watchResourceId());
            }
        } catch (Exception e) {
            // Puede que ya haya vencido del lado de Google: es el estado buscado.
            log.warn("No se pudo detener el canal de Google del profesional {}: {}", profesionalId, e.getMessage());
        } finally {
            cuentas.guardarCanal(profesionalId, null, null, null, false);
        }
    }

    public void renovar(Long profesionalId) {
        detener(profesionalId);
        registrar(profesionalId);
    }

    /** El profesional del token que Google devuelve en cada webhook, si la firma es nuestra. */
    public Long profesionalDelToken(String token) {
        return cifrado.verificar(token);
    }
}
