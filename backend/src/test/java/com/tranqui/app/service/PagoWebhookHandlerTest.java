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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import com.tranqui.app.eventos.OutboxPublisher;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoWebhookHandlerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SolicitudDocumentoRepository solicitudRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private GoogleCalendarService calendarService;
    @Mock private NotificacionService notificacionService;
    @Mock private ResendEmailService resendEmailService;
    @Mock private OutboxPublisher outboxPublisher;

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
        verify(turnoRepository).saveAndFlush(turno);

        ArgumentCaptor<Pago> pagoCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertEquals("tx-turno-1", pagoCaptor.getValue().getTransactionId());
        assertEquals(EstadoPago.APROBADO, pagoCaptor.getValue().getEstado());
        assertEquals(0, new BigDecimal("60000").compareTo(pagoCaptor.getValue().getMonto()));

        verify(notificacionService).crearNotificacion(eq(medico), anyString(), anyString(), eq("TURNO_RESERVADO"));
        verify(notificacionService).crearNotificacion(eq(paciente), anyString(), anyString(), eq("TURNO_CONFIRMADO"));
    }

    @Test
    void procesarAprobacionTurno_shouldSwallowNotificationErrors() {
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
        // impida confirmar el turno.
        doThrow(new RuntimeException("boom")).when(notificacionService)
                .crearNotificacion(any(), anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> handler.procesarAprobacionTurno(11L, "tx-2"));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
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
        verifyNoInteractions(pagoRepository, calendarService, notificacionService);
    }

    // ── esDocumento (turnos que no ocupan agenda, p.ej. receta/certificado fuera de turno) ──

    @Test
    void procesarAprobacionTurno_shouldUseDocumentWordingWhenNoOcupaAgenda() {
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
        verify(notificacionService).crearNotificacion(eq(paciente), eq("Pago confirmado"),
                contains("una receta"), eq("TURNO_CONFIRMADO"));
        // Documentos no llevan fecha/hora real: no disparan la creación de evento de Google Meet.
        verifyNoInteractions(calendarService);
    }

    // ── mails al paciente ─────────────────────────────────────────────

    @Test
    void procesarAprobacionTurno_mandaMailDeConfirmacionAlPacienteConElLinkDeMeet() {
        Usuario paciente = paciente();
        Turno turno = Turno.builder()
                .id(50L).medico(medico()).paciente(paciente)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .modalidad(Modalidad.ONLINE)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(50L)).thenReturn(Optional.of(turno));
        when(calendarService.crearEventoReunion(turno)).thenReturn("https://meet.google.com/abc-defg-hij");

        handler.procesarAprobacionTurno(50L, "tx-mail");

        verify(resendEmailService).enviarTurnoConfirmadoPaciente(eq("ana@mail.com"), eq("Ana"), eq("Dra. Paula"),
                eq(turno.getFecha()), eq(LocalTime.of(10, 0)), eq(true), anyString(), isNull(),
                eq("https://meet.google.com/abc-defg-hij"), eq(new BigDecimal("60000")));
    }

    @Test
    void procesarAprobacionTurno_documentoMandaMailDePagoConfirmado() {
        Turno turno = Turno.builder()
                .id(51L).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now()).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 0))
                .tipo(TipoTurno.CERTIFICADO).estado(EstadoTurno.PENDIENTE_PAGO)
                .servicioId("certificado").ocupaAgenda(false)
                .precio(new BigDecimal("8000"))
                .build();
        when(turnoRepository.findById(51L)).thenReturn(Optional.of(turno));

        handler.procesarAprobacionTurno(51L, "tx-doc-mail");

        verify(resendEmailService).enviarDocumentoPagadoPaciente(eq("ana@mail.com"), eq("Ana"), eq("Dra. Paula"),
                eq("un certificado"), eq(new BigDecimal("8000")));
        verify(resendEmailService, never()).enviarTurnoConfirmadoPaciente(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void procesarAprobacionTurno_noMandaMailSiElPacienteDesactivoLasNotificaciones() {
        Usuario paciente = paciente();
        paciente.setNotificacionesEmailHabilitadas(false);
        Turno turno = Turno.builder()
                .id(52L).medico(medico()).paciente(paciente)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(new BigDecimal("60000"))
                .build();
        when(turnoRepository.findById(52L)).thenReturn(Optional.of(turno));

        handler.procesarAprobacionTurno(52L, "tx-optout");

        verify(resendEmailService, never()).enviarTurnoConfirmadoPaciente(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    // ── Fase 4: eventos del servicio de pagos ────────────────────────

    private Turno turnoPendiente(Long id) {
        return Turno.builder()
                .id(id).medico(medico()).paciente(paciente())
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).estado(EstadoTurno.PENDIENTE_PAGO)
                .modalidad(Modalidad.ONLINE)
                .precio(new BigDecimal("60000"))
                .build();
    }

    @Test
    void aplicarPagoAprobado_confirmaYGuardaLaCopiaSinVolverAPublicarElPago() {
        Turno turno = turnoPendiente(20L);
        when(turnoRepository.findById(20L)).thenReturn(Optional.of(turno));

        handler.aplicarPagoAprobado(new com.tranqui.eventos.data.PagoAprobadoData(7L, 20L, "mp-555", new BigDecimal("60000"), "mercadopago"));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pago.capture());
        assertEquals("mp-555", pago.getValue().getTransactionId());
        verify(outboxPublisher, never()).publicar(eq(com.tranqui.eventos.Topicos.PAGOS_PAGO_APROBADO_V1), anyString(), anyInt(), any());
        verify(outboxPublisher).publicar(eq(com.tranqui.eventos.Topicos.TURNOS_TURNO_CONFIRMADO_V1), anyString(), anyInt(), any());
    }

    @Test
    void aplicarPagoAprobado_esIdempotenteYToleraUnTurnoQueNoExiste() {
        Turno confirmado = turnoPendiente(21L);
        confirmado.setEstado(EstadoTurno.CONFIRMADO);
        when(turnoRepository.findById(21L)).thenReturn(Optional.of(confirmado));
        when(turnoRepository.findById(99L)).thenReturn(Optional.empty());

        handler.aplicarPagoAprobado(new com.tranqui.eventos.data.PagoAprobadoData(7L, 21L, "mp-1", BigDecimal.TEN, "mercadopago"));
        handler.aplicarPagoAprobado(new com.tranqui.eventos.data.PagoAprobadoData(8L, 99L, "mp-2", BigDecimal.TEN, "mercadopago"));

        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void aplicarPagoRechazado_guardaLaCopiaUnaSolaVez() {
        Turno turno = turnoPendiente(22L);
        when(turnoRepository.findById(22L)).thenReturn(Optional.of(turno));
        when(pagoRepository.findByTransactionId("mp-r1")).thenReturn(Optional.empty());
        when(pagoRepository.findByTransactionId("mp-r2")).thenReturn(Optional.of(new Pago()));

        handler.aplicarPagoRechazado(new com.tranqui.eventos.data.PagoRechazadoData(1L, 22L, "mp-r1", "rejected"));
        handler.aplicarPagoRechazado(new com.tranqui.eventos.data.PagoRechazadoData(2L, 22L, "mp-r2", "rejected"));

        ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pago.capture());
        assertEquals(EstadoPago.RECHAZADO, pago.getValue().getEstado());
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void aplicarReembolsoRealizado_marcaLaCopiaDelPago() {
        Turno turno = turnoPendiente(23L);
        Pago pago = Pago.builder().turno(turno).transactionId("mp-9").estado(EstadoPago.APROBADO).build();
        turno.setPago(pago);
        when(turnoRepository.findById(23L)).thenReturn(Optional.of(turno));

        handler.aplicarReembolsoRealizado(new com.tranqui.eventos.data.ReembolsoRealizadoData(1L, 1L, 23L, BigDecimal.TEN, "x"));

        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
        verify(pagoRepository).save(pago);
    }

    @Test
    void aplicarConceptoAprobado_marcaLaSolicitudSinPublicarNiEmpujar() {
        SolicitudDocumento solicitud = SolicitudDocumento.builder()
                .id(5L).paciente(paciente()).medico(medico()).tipoConcepto(TipoConcepto.CERTIFICADO)
                .estado(EstadoPago.PENDIENTE).build();
        when(solicitudRepository.existsById(5L)).thenReturn(true);
        when(solicitudRepository.findById(5L)).thenReturn(Optional.of(solicitud));

        handler.aplicarConceptoAprobado(new com.tranqui.eventos.data.ConceptoAprobadoData(5L, 1L, 2L, "CERTIFICADO", "mp-doc"));

        assertEquals(EstadoPago.APROBADO, solicitud.getEstado());
        assertEquals("mp-doc", solicitud.getTransactionId());
        verifyNoInteractions(outboxPublisher, messagingTemplate);
    }

    @Test
    void aplicarPagoAprobado_despuesDeUnRechazo_reusaElPagoDelTurno() {
        Turno turno = turnoPendiente(24L);
        Pago rechazado = Pago.builder().id(3L).turno(turno).transactionId("mp-rechazado").estado(EstadoPago.RECHAZADO).build();
        turno.setPago(rechazado);
        when(turnoRepository.findById(24L)).thenReturn(Optional.of(turno));

        handler.aplicarPagoAprobado(new com.tranqui.eventos.data.PagoAprobadoData(9L, 24L, "mp-aprobado", new BigDecimal("60000"), "mercadopago"));
        handler.aplicarPagoRechazado(new com.tranqui.eventos.data.PagoRechazadoData(10L, 24L, "mp-otro", "rejected"));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertEquals(EstadoPago.APROBADO, rechazado.getEstado());
        assertEquals("mp-aprobado", rechazado.getTransactionId());
        verify(pagoRepository, times(1)).save(rechazado);
    }
}
