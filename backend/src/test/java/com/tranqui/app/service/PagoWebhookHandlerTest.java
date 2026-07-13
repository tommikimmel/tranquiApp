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
        when(calendarService.crearEventoReunion(turno)).thenReturn("https://meet.google.com/xyz");
        doThrow(new RuntimeException("boom")).when(notificacionService)
                .crearNotificacion(any(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(11L, "tx-2"));

        verify(whatsAppService).enviarMensajeRecordatorio(turno);
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
        when(calendarService.crearEventoReunion(turno)).thenReturn("https://meet.google.com/xyz");
        doThrow(new RuntimeException("whatsapp down")).when(whatsAppService).enviarMensajeRecordatorio(turno);

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(12L, "tx-3"));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        verify(pagoRepository).save(any());
    }
}
