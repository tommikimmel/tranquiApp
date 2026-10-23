package com.tranqui.calendario.google;

import com.google.api.services.calendar.model.Event;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Las llamadas a la API de Google Calendar que hace el servicio, detrás de una interfaz para poder
 * probar el resto sin Google. La implementación real es {@link GoogleCalendarApiHttp}.
 */
public interface GoogleCalendarApi {

    record EventoCreado(String eventId, String meetUrl) {}

    record Pagina(List<Event> eventos, String siguientePagina, String siguienteSyncToken) {}

    record Canal(String id, String resourceId, Long venceEnMillis) {}

    /** Google invalidó el syncToken (410): la única salida es una sincronización completa. */
    class SyncTokenVencidoException extends IOException {
        public SyncTokenVencidoException(String mensaje) {
            super(mensaje);
        }
    }

    EventoCreado crear(String accessToken, String calendario, String titulo, String descripcion,
                       ZonedDateTime inicio, ZonedDateTime fin, boolean conMeet) throws IOException;

    void mover(String accessToken, String calendario, String eventId, ZonedDateTime inicio, ZonedDateTime fin) throws IOException;

    void borrar(String accessToken, String calendario, String eventId) throws IOException;

    Pagina cambios(String accessToken, String calendario, String syncToken, String pagina) throws IOException;

    Pagina ventana(String accessToken, String calendario, ZonedDateTime desde, ZonedDateTime hasta, String pagina) throws IOException;

    Canal registrarCanal(String accessToken, String calendario, String direccion, String token, long venceEnMillis) throws IOException;

    void detenerCanal(String accessToken, String channelId, String resourceId) throws IOException;
}
