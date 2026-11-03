package com.tranqui.calendario.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Channel;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.EntryPoint;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Google Calendar real, con el cliente oficial (las mismas llamadas que hacía el monolito). */
@Component
public class GoogleCalendarApiHttp implements GoogleCalendarApi {

    private static final String ZONA = "America/Argentina/Buenos_Aires";

    private static Calendar cliente(String accessToken) {
        HttpRequestInitializer inicializador = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
        return new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), inicializador)
                .setApplicationName("TranquiApp")
                .build();
    }

    private static EventDateTime momento(ZonedDateTime instante) {
        return new EventDateTime().setDateTime(new DateTime(instante.toInstant().toEpochMilli())).setTimeZone(ZONA);
    }

    @Override
    public EventoCreado crear(String accessToken, String calendario, String titulo, String descripcion,
                              ZonedDateTime inicio, ZonedDateTime fin, boolean conMeet) throws IOException {
        Event evento = new Event().setSummary(titulo).setDescription(descripcion).setStart(momento(inicio)).setEnd(momento(fin));
        if (conMeet) {
            evento.setConferenceData(new ConferenceData().setCreateRequest(new CreateConferenceRequest()
                    .setRequestId(UUID.randomUUID().toString())
                    .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))));
        }
        Event creado = cliente(accessToken).events().insert(calendario, evento).setConferenceDataVersion(1).execute();

        String meetUrl = "";
        if (creado.getConferenceData() != null && creado.getConferenceData().getEntryPoints() != null) {
            for (EntryPoint entrada : creado.getConferenceData().getEntryPoints()) {
                if ("video".equals(entrada.getEntryPointType())) {
                    meetUrl = entrada.getUri();
                    break;
                }
            }
        }
        if (meetUrl.isEmpty() && creado.getHangoutLink() != null) {
            meetUrl = creado.getHangoutLink();
        }
        return new EventoCreado(creado.getId(), meetUrl);
    }

    @Override
    public void mover(String accessToken, String calendario, String eventId, ZonedDateTime inicio, ZonedDateTime fin)
            throws IOException {
        Calendar servicio = cliente(accessToken);
        Event evento = servicio.events().get(calendario, eventId).execute();
        evento.setStart(momento(inicio));
        evento.setEnd(momento(fin));
        servicio.events().update(calendario, eventId, evento).execute();
    }

    @Override
    public void borrar(String accessToken, String calendario, String eventId) throws IOException {
        cliente(accessToken).events().delete(calendario, eventId).execute();
    }

    @Override
    public Pagina cambios(String accessToken, String calendario, String syncToken, String pagina) throws IOException {
        try {
            Events eventos = cliente(accessToken).events().list(calendario)
                    .setSyncToken(syncToken).setShowDeleted(true).setSingleEvents(true).setPageToken(pagina).execute();
            return new Pagina(eventos.getItems(), eventos.getNextPageToken(), eventos.getNextSyncToken());
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                throw new SyncTokenVencidoException(e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public Pagina ventana(String accessToken, String calendario, ZonedDateTime desde, ZonedDateTime hasta, String pagina)
            throws IOException {
        Events eventos = cliente(accessToken).events().list(calendario)
                .setTimeMin(new DateTime(desde.toInstant().toEpochMilli()))
                .setTimeMax(new DateTime(hasta.toInstant().toEpochMilli()))
                .setSingleEvents(true).setPageToken(pagina).execute();
        return new Pagina(eventos.getItems(), eventos.getNextPageToken(), eventos.getNextSyncToken());
    }

    @Override
    public Canal registrarCanal(String accessToken, String calendario, String direccion, String token, long venceEnMillis)
            throws IOException {
        Channel pedido = new Channel().setId(UUID.randomUUID().toString()).setType("web_hook")
                .setAddress(direccion).setToken(token).setExpiration(venceEnMillis);
        Channel canal = cliente(accessToken).events().watch(calendario, pedido).execute();
        return new Canal(canal.getId(), canal.getResourceId(), canal.getExpiration());
    }

    @Override
    public void detenerCanal(String accessToken, String channelId, String resourceId) throws IOException {
        cliente(accessToken).channels().stop(new Channel().setId(channelId).setResourceId(resourceId)).execute();
    }
}
