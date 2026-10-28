package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.consumo.EventoInvalidoException;
import com.tranqui.eventos.data.PasswordTemporalGeneradaData;
import com.tranqui.eventos.data.SolicitudDatosData;
import com.tranqui.notificaciones.contacto.Contacto;
import com.tranqui.notificaciones.contacto.ContactoService;
import com.tranqui.notificaciones.mail.ResendEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManejadoresIdentidadTest {

    private Avisos avisos;
    private ResendEmailService mails;
    private ContactoService contactos;
    private ManejadoresIdentidad manejadores;

    @BeforeEach
    void preparar() {
        avisos = mock(Avisos.class);
        mails = mock(ResendEmailService.class);
        contactos = mock(ContactoService.class);
        manejadores = new ManejadoresIdentidad();

        // En tests unitarios avisos.mail ejecuta el Runnable
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return null;
        }).when(avisos).mail(any(), anyString(), any());
    }

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType("test.evento")
                .data(data)
                .build();
    }

    // ------------------------------------------------------------------ Password temporal

    @Test
    void passwordTemporal_enviaMailConContraseniaYNombre() {
        PasswordTemporalGeneradaData data = new PasswordTemporalGeneradaData(
                10L, "usuario@mail.com", "Carlos Gomez", "Temporal_1234!"
        );

        manejadores.mailPasswordTemporal(avisos, mails, contactos).manejar(evento(data));

        verify(mails).enviarPasswordTemporal("usuario@mail.com", "Carlos Gomez", "Temporal_1234!");
    }

    @Test
    void passwordTemporal_obtieneNombreDeContactosSiVieneNull() {
        Contacto contacto = new Contacto(10L, "usuario@mail.com");
        contacto.setNombre("Carlos Contacto");
        when(contactos.buscarPorEmail("usuario@mail.com")).thenReturn(Optional.of(contacto));

        PasswordTemporalGeneradaData data = new PasswordTemporalGeneradaData(
                10L, "usuario@mail.com", null, "Temporal_1234!"
        );

        manejadores.mailPasswordTemporal(avisos, mails, contactos).manejar(evento(data));

        verify(mails).enviarPasswordTemporal("usuario@mail.com", "Carlos Contacto", "Temporal_1234!");
    }

    @Test
    void passwordTemporal_datosInvalidos_lanzaEventoInvalidoException() {
        var handler = manejadores.mailPasswordTemporal(avisos, mails, contactos);

        // Data nulo
        assertThatThrownBy(() -> handler.manejar(evento(null)))
                .isInstanceOf(EventoInvalidoException.class);

        // usuarioId nulo
        assertThatThrownBy(() -> handler.manejar(evento(new PasswordTemporalGeneradaData(
                null, "user@mail.com", "Carlos", "Temp123"))))
                .isInstanceOf(EventoInvalidoException.class);

        // email nulo
        assertThatThrownBy(() -> handler.manejar(evento(new PasswordTemporalGeneradaData(
                1L, null, "Carlos", "Temp123"))))
                .isInstanceOf(EventoInvalidoException.class);

        // email inválido
        assertThatThrownBy(() -> handler.manejar(evento(new PasswordTemporalGeneradaData(
                1L, "invalido", "Carlos", "Temp123"))))
                .isInstanceOf(EventoInvalidoException.class);

        // passwordTemporal nula o vacía
        assertThatThrownBy(() -> handler.manejar(evento(new PasswordTemporalGeneradaData(
                1L, "user@mail.com", "Carlos", null))))
                .isInstanceOf(EventoInvalidoException.class);

        assertThatThrownBy(() -> handler.manejar(evento(new PasswordTemporalGeneradaData(
                1L, "user@mail.com", "Carlos", "   "))))
                .isInstanceOf(EventoInvalidoException.class);

        verify(mails, never()).enviarPasswordTemporal(any(), any(), any());
    }

    @Test
    void passwordTemporal_eventoRepetido_noMandaDosVeces() {
        PasswordTemporalGeneradaData data = new PasswordTemporalGeneradaData(
                10L, "usuario@mail.com", "Carlos", "Temp123"
        );
        EventEnvelope<PasswordTemporalGeneradaData> env = evento(data);

        // Simulamos comportamiento del inbox/idempotencia en avisos.mail: solo ejecuta en la primera entrega
        AtomicInteger ejecuciones = new AtomicInteger(0);
        doAnswer(inv -> {
            if (ejecuciones.incrementAndGet() == 1) {
                ((Runnable) inv.getArgument(2)).run();
            }
            return null;
        }).when(avisos).mail(eq(env), eq("mail-password-temporal"), any());

        var handler = manejadores.mailPasswordTemporal(avisos, mails, contactos);
        handler.manejar(env);
        handler.manejar(env);

        verify(mails, times(1)).enviarPasswordTemporal("usuario@mail.com", "Carlos", "Temp123");
    }

    // ------------------------------------------------------------------ Solicitud de datos (Ley 25.326)

    @Test
    void solicitudDatos_enviaMailConDatosYRol() {
        SolicitudDatosData data = new SolicitudDatosData(
                20L, "Dra. Laura Martinez", "laura@mail.com", "PSIQUIATRA"
        );

        manejadores.mailSolicitudDatos(avisos, mails).manejar(evento(data));

        verify(mails).enviarSolicitudCopiaDatos("Dra. Laura Martinez", "laura@mail.com", "PSIQUIATRA");
    }

    @Test
    void solicitudDatos_datosInvalidos_lanzaEventoInvalidoException() {
        var handler = manejadores.mailSolicitudDatos(avisos, mails);

        // Data nulo
        assertThatThrownBy(() -> handler.manejar(evento(null)))
                .isInstanceOf(EventoInvalidoException.class);

        // usuarioId nulo
        assertThatThrownBy(() -> handler.manejar(evento(new SolicitudDatosData(
                null, "Laura", "laura@mail.com", "PSIQUIATRA"))))
                .isInstanceOf(EventoInvalidoException.class);

        // email nulo
        assertThatThrownBy(() -> handler.manejar(evento(new SolicitudDatosData(
                20L, "Laura", null, "PSIQUIATRA"))))
                .isInstanceOf(EventoInvalidoException.class);

        // email inválido
        assertThatThrownBy(() -> handler.manejar(evento(new SolicitudDatosData(
                20L, "Laura", "no-es-un-email", "PSIQUIATRA"))))
                .isInstanceOf(EventoInvalidoException.class);

        // rol nulo o vacío
        assertThatThrownBy(() -> handler.manejar(evento(new SolicitudDatosData(
                20L, "Laura", "laura@mail.com", null))))
                .isInstanceOf(EventoInvalidoException.class);

        assertThatThrownBy(() -> handler.manejar(evento(new SolicitudDatosData(
                20L, "Laura", "laura@mail.com", "   "))))
                .isInstanceOf(EventoInvalidoException.class);

        verify(mails, never()).enviarSolicitudCopiaDatos(any(), any(), any());
    }

    @Test
    void solicitudDatos_eventoRepetido_noMandaDosVeces() {
        SolicitudDatosData data = new SolicitudDatosData(
                20L, "Laura", "laura@mail.com", "PSIQUIATRA"
        );
        EventEnvelope<SolicitudDatosData> env = evento(data);

        AtomicInteger ejecuciones = new AtomicInteger(0);
        doAnswer(inv -> {
            if (ejecuciones.incrementAndGet() == 1) {
                ((Runnable) inv.getArgument(2)).run();
            }
            return null;
        }).when(avisos).mail(eq(env), eq("mail-soporte-solicitud-datos"), any());

        var handler = manejadores.mailSolicitudDatos(avisos, mails);
        handler.manejar(env);
        handler.manejar(env);

        verify(mails, times(1)).enviarSolicitudCopiaDatos("Laura", "laura@mail.com", "PSIQUIATRA");
    }
}
