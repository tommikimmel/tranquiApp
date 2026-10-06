package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoWebhookHandlerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SolicitudDocumentoRepository solicitudRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private GoogleCalendarService calendarService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private PagoWebhookHandler handler;

    private Usuario medico() {
        return Usuario.builder().id(1L).nombre("Dra. Paula").email("dra@mail.com").rol(Rol.PSIQUIATRA).build();
    }

    private Usuario paciente() {
        return Usuario.builder().id(2L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).build();
    }

    // ── procesarAprobacionConcepto ───────────────────────────────────

    @Test
    void procesarAprobacionConcepto_shouldThrowWhenSolicitudNotFound() {
        when(solicitudRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> handler.procesarAprobacionConcepto(1L, "tx-1"));
    }

    @Test
    void procesarAprobacionConcepto_shouldApproveSaveAndPublishWebSocketNotification() {
        SolicitudDocumento solicitud = SolicitudDocumento.builder()
                .id(1L).paciente(paciente()).medico(medico()).tipoConcepto(TipoConcepto.CERTIFICADO)
                .estado(EstadoPago.PENDIENTE)
                .build();
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        handler.procesarAprobacionConcepto(1L, "tx-abc");

        assertEquals(EstadoPago.APROBADO, solicitud.getEstado());
        assertEquals("tx-abc", solicitud.getTransactionId());
        verify(solicitudRepository).save(solicitud);
        verify(messagingTemplate).convertAndSend(eq("/topic/notificaciones/1"), any(Object.class));
    }

    // ── procesarAprobacionTurno ──────────────────────────────────────

    @Test
    void procesarAprobacionTurno_shouldThrowWhenTurnoNotFound() {
        when(turnoRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> handler.procesarAprobacionTurno(1L, "tx-1"));
    }

    @Test
    void procesarAprobacionTurno_shouldConfirmCreateMeetLinkRegisterPagoAndNotify() {
        Usuario medico = medico();
        Usuario paciente = paciente();
        Turno turno = Turno.builder()
                .id(10L).medico(medico).paciente(paciente)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .modalidad(Modalidad.ONLINE)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(10L)).thenReturn(Optional.of(turno));
        when(calendarService.crearEventoReunion(turno)).thenReturn("https://meet.google.com/xyz");

        handler.procesarAprobacionTurno(10L, "tx-turno-1");

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertEquals("https://meet.google.com/xyz", turno.getTelemedicinaUrl());
        verify(turnoRepository).save(turno);

        ArgumentCaptor<Pago> pagoCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertEquals("tx-turno-1", pagoCaptor.getValue().getTransactionId());
        assertEquals(EstadoPago.APROBADO, pagoCaptor.getValue().getEstado());
        assertEquals(0, new BigDecimal("60000").compareTo(pagoCaptor.getValue().getMonto()));

        verify(notificacionService).crearNotificacion(eq(medico), anyString(), anyString(), eq("TURNO_RESERVADO"));
        verify(notificacionService).crearNotificacion(eq(paciente), anyString(), anyString(), eq("TURNO_CONFIRMADO"));
        verify(whatsAppService).enviarMensajeRecordatorio(turno);
    }

    @Test
    void procesarAprobacionTurno_shouldSwallowNotificationErrorsAndStillSendWhatsapp() {
        Turno turno = Turno.builder()
                .id(11L).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(11L)).thenReturn(Optional.of(turno));
        // Turno con agenda (presencial por default): el handler crea el evento en Google Calendar
        // igual (mock devuelve null). Este test se enfoca en que un error de notificación no
        // impida el envío de WhatsApp.
        doThrow(new RuntimeException("boom")).when(notificacionService)
                .crearNotificacion(any(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(11L, "tx-2"));

        verify(whatsAppService).enviarMensajeRecordatorio(turno);
        verify(calendarService).crearEventoReunion(turno);
    }

    @Test
    void procesarAprobacionTurno_presencial_creaEventoEnGoogleCalendarSinPisarTelemedicinaUrl() {
        Turno turno = Turno.builder()
                .id(13L).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .modalidad(Modalidad.PRESENCIAL)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(13L)).thenReturn(Optional.of(turno));
        when(calendarService.crearEventoReunion(turno)).thenReturn("");

        handler.procesarAprobacionTurno(13L, "tx-presencial");

        verify(calendarService).crearEventoReunion(turno);
        assertNull(turno.getTelemedicinaUrl());
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
    }

    @Test
    void procesarAprobacionTurno_shouldSwallowWhatsappErrors() {
        Turno turno = Turno.builder()
                .id(12L).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(12L)).thenReturn(Optional.of(turno));
        // calendarService no se stubea: el mock devuelve null y el handler lo tolera.
        doThrow(new RuntimeException("whatsapp down")).when(whatsAppService).enviarMensajeRecordatorio(turno);

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(12L, "tx-3"));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        verify(pagoRepository).save(any());
    }

    // ── idempotencia ──────────────────────────────────────────────────

    @Test
    void procesarAprobacionTurno_shouldBeNoOpWhenTurnoYaConfirmado() {
        // Este handler ahora tiene dos llamadores que pueden llegar a procesar el mismo pago
        // aprobado (el webhook async real de Mercado Pago y el fallback síncrono de
        // WebhookController#verificarPago) — reprocesar un turno ya CONFIRMADO no debe duplicar
        // el Pago ni las notificaciones.
        Turno turno = Turno.builder()
                .id(20L).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.CONFIRMADO)
                .modalidad(Modalidad.ONLINE)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(20L)).thenReturn(Optional.of(turno));

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(20L, "tx-idempotente"));

        verify(turnoRepository, never()).save(any());
        verifyNoInteractions(pagoRepository, calendarService, whatsAppService, notificacionService);
    }

    // ── esDocumento (turnos que no ocupan agenda, p.ej. receta/certificado fuera de turno) ──

    @Test
    void procesarAprobacionTurno_shouldUseDocumentWordingAndSkipWhatsappWhenNoOcupaAgenda() {
        Usuario medico = medico();
        Usuario paciente = paciente();
        Turno turno = Turno.builder()
                .id(30L).medico(medico).paciente(paciente)
                .fecha(LocalDate.now()).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 0))
                .tipo(TipoTurno.RECETA).estado(EstadoTurno.PENDIENTE_PAGO)
                .servicioId("receta-fuera")
                .ocupaAgenda(false)
                .precio(new BigDecimal("5000"))
                .build();
        when(turnoRepository.findById(30L)).thenReturn(Optional.of(turno));

        handler.procesarAprobacionTurno(30L, "tx-doc-1");

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        verify(notificacionService).crearNotificacion(eq(medico), eq("Nuevo Documento Pendiente"),
                contains("pagó una receta"), eq("DOCUMENTO_PENDIENTE"));
        verify(notificacionService).crearNotificacion(eq(paciente), eq("Pago confirmado ✓"),
                contains("una receta"), eq("TURNO_CONFIRMADO"));
        // Documentos no llevan fecha/hora real ni link de videollamada que recordar por WhatsApp,
        // y tampoco disparan la creación de evento de Google Meet.
        verifyNoInteractions(whatsAppService);
        verifyNoInteractions(calendarService);
    }

    // ── opt-out de WhatsApp ──────────────────────────────────────────

    @Test
    void procesarAprobacionTurno_shouldSkipWhatsappWhenPacienteOptedOut() {
        Usuario pacienteOptOut = Usuario.builder()
                .id(2L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE)
                .notificacionesWhatsappHabilitadas(false)
                .build();
        Turno turno = Turno.builder()
                .id(40L).medico(medico()).paciente(pacienteOptOut)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(40L)).thenReturn(Optional.of(turno));

        handler.procesarAprobacionTurno(40L, "tx-optout");

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        verify(whatsAppService, never()).enviarMensajeRecordatorio(any());
    }
}
