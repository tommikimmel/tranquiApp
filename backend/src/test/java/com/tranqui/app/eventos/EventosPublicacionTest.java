package com.tranqui.app.eventos;

import com.tranqui.app.controller.AdminController;
import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.repository.*;
import com.tranqui.app.service.*;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventosPublicacionTest {

    @Mock private OutboxPublisher outboxPublisher;

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionEventRepository subscriptionEventRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private MensajeRepository mensajeRepository;
    @Mock private GoogleCalendarService googleCalendarService;

    @Test
    @DisplayName("AdminController.verifyProfessional publica PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1")
    void verifyProfessional_publicaEvento() {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarioRepository);

        Usuario user = Usuario.builder().id(42L).rol(Rol.PACIENTE).nombre("Dr. Test").build();
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(user));

        controller.verifyProfessional(42L);

        ArgumentCaptor<ProfesionalVisibilidadCambiadaData> dataCaptor = ArgumentCaptor.forClass(ProfesionalVisibilidadCambiadaData.class);
        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1),
                eq("42"),
                anyInt(),
                dataCaptor.capture()
        );

        ProfesionalVisibilidadCambiadaData data = dataCaptor.getValue();
        assertThat(data.profesionalId()).isEqualTo(42L);
        assertThat(data.visible()).isTrue();
    }

    @Test
    @DisplayName("AdminController.rejectProfessional publica PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1 con visible false")
    void rejectProfessional_publicaEvento() {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarioRepository);

        Usuario user = Usuario.builder().id(42L).rol(Rol.PSIQUIATRA).build();
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(user));

        controller.rejectProfessional(42L);

        ArgumentCaptor<ProfesionalVisibilidadCambiadaData> dataCaptor = ArgumentCaptor.forClass(ProfesionalVisibilidadCambiadaData.class);
        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1),
                eq("42"),
                anyInt(),
                dataCaptor.capture()
        );

        assertThat(dataCaptor.getValue().visible()).isFalse();
    }

    @Test
    @DisplayName("SubscriptionService.logEvent publica SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2")
    void subscriptionService_logEvent_publicaEvento() {
        SubscriptionService service = new SubscriptionService();
        ReflectionTestUtils.setField(service, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(service, "eventRepository", subscriptionEventRepository);
        ReflectionTestUtils.setField(service, "subscriptionRepository", subscriptionRepository);

        Usuario prof = Usuario.builder().id(99L).build();
        Subscription sub = Subscription.builder().id(10L).professional(prof).build();
        when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(sub));

        service.logEvent(10L, "ACTIVATED", "INACTIVE", "ACTIVE", "SYSTEM", "system", "Pago exitoso");

        ArgumentCaptor<SuscripcionCambioEstadoV2Data> captor = ArgumentCaptor.forClass(SuscripcionCambioEstadoV2Data.class);
        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2),
                eq("10"),
                anyInt(),
                captor.capture()
        );

        SuscripcionCambioEstadoV2Data data = captor.getValue();
        assertThat(data.suscripcionId()).isEqualTo(10L);
        assertThat(data.profesionalId()).isEqualTo(99L);
        assertThat(data.estadoNuevo()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PagoWebhookHandler.procesarAprobacionTurno publica PAGOS_PAGO_APROBADO_V1 y TURNOS_TURNO_CONFIRMADO_V1")
    void procesarAprobacionTurno_publicaEventos() {
        PagoWebhookHandler handler = new PagoWebhookHandler();
        ReflectionTestUtils.setField(handler, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(handler, "turnoRepository", turnoRepository);
        ReflectionTestUtils.setField(handler, "pagoRepository", pagoRepository);
        ReflectionTestUtils.setField(handler, "calendarService", googleCalendarService);

        Usuario med = Usuario.builder().id(1L).nombre("Dr").email("dr@mail.com").build();
        Usuario pac = Usuario.builder().id(2L).nombre("Pac").email("pac@mail.com").build();
        Turno turno = Turno.builder()
                .id(100L)
                .medico(med)
                .paciente(pac)
                .fecha(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.of(10, 0))
                .precio(new BigDecimal("15000.00"))
                .modalidad(Modalidad.ONLINE)
                .ocupaAgenda(true)
                .build();

        when(turnoRepository.findById(100L)).thenReturn(Optional.of(turno));
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            return Pago.builder()
                    .id(501L)
                    .turno(p.getTurno())
                    .transactionId(p.getTransactionId())
                    .monto(p.getMonto())
                    .build();
        });

        handler.procesarAprobacionTurno(100L, "mp-tx-999");

        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.PAGOS_PAGO_APROBADO_V1),
                eq("501"),
                anyInt(),
                argThat((PagoAprobadoData d) -> d.pagoId().equals(501L) && d.monto().compareTo(new BigDecimal("15000.00")) == 0)
        );

        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.TURNOS_TURNO_CONFIRMADO_V1),
                eq("100"),
                anyInt(),
                argThat((TurnoConfirmadoData d) -> d.turnoId().equals(100L) && d.pacienteId().equals(2L))
        );
    }

    @Test
    @DisplayName("PagoWebhookHandler.procesarRechazoTurno publica PAGOS_PAGO_RECHAZADO_V1")
    void procesarRechazoTurno_publicaEvento() {
        PagoWebhookHandler handler = new PagoWebhookHandler();
        ReflectionTestUtils.setField(handler, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(handler, "turnoRepository", turnoRepository);
        ReflectionTestUtils.setField(handler, "pagoRepository", pagoRepository);

        Turno turno = Turno.builder().id(200L).precio(new BigDecimal("5000.00")).build();
        when(turnoRepository.findById(200L)).thenReturn(Optional.of(turno));
        when(pagoRepository.save(any())).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            return Pago.builder().id(701L).turno(turno).transactionId("mp-fail").build();
        });

        handler.procesarRechazoTurno(200L, "mp-fail", "Fondos insuficientes");

        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.PAGOS_PAGO_RECHAZADO_V1),
                eq("701"),
                anyInt(),
                argThat((PagoRechazadoData d) -> d.pagoId().equals(701L) && d.motivo().equals("Fondos insuficientes"))
        );
    }

    @Mock private TicketMensajeRepository ticketMensajeRepository;
    @Mock private NotificacionService notificacionService;
    @Mock private ResendEmailService resendEmailService;

    @Test
    @DisplayName("TicketService.agregarMensaje publica IDENTIDAD_TICKET_RESPONDIDO_V1")
    void ticketService_agregarMensaje_publicaEvento() {
        TicketService service = new TicketService();
        ReflectionTestUtils.setField(service, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(service, "ticketRepository", ticketRepository);
        ReflectionTestUtils.setField(service, "ticketMensajeRepository", ticketMensajeRepository);
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(service, "notificacionService", notificacionService);
        ReflectionTestUtils.setField(service, "resendEmailService", resendEmailService);

        Usuario creador = Usuario.builder().id(15L).email("paciente@mail.com").rol(Rol.PACIENTE).build();
        Usuario admin = Usuario.builder().id(1L).email("admin@tranqui.com").rol(Rol.ADMIN).build();
        Ticket ticket = Ticket.builder().id(301L).creador(creador).asunto("Problema con login").estado(TicketEstado.ACTIVO).build();

        when(ticketRepository.findById(301L)).thenReturn(Optional.of(ticket));
        when(usuarioRepository.findByEmail("admin@tranqui.com")).thenReturn(Optional.of(admin));

        service.agregarMensaje(301L, "admin@tranqui.com", "Hola, respondemos tu consulta");

        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1),
                eq("ticket"),
                eq("301"),
                anyInt(),
                argThat((TicketRespondidoData d) -> d.ticketId().equals(301L) && d.respuesta().equals("Hola, respondemos tu consulta"))
        );
    }

    @Test
    @DisplayName("MensajeService.guardarMensaje publica CHAT_MENSAJE_ENVIADO_V1 sin contenido privado")
    void mensajeService_guardarMensaje_publicaEvento() {
        MensajeService service = new MensajeService();
        ReflectionTestUtils.setField(service, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(service, "mensajeRepository", mensajeRepository);

        Usuario rem = Usuario.builder().id(11L).email("rem@mail.com").build();
        Usuario dest = Usuario.builder().id(22L).email("dest@mail.com").build();

        when(usuarioRepository.findByEmail("rem@mail.com")).thenReturn(Optional.of(rem));
        when(usuarioRepository.findById(22L)).thenReturn(Optional.of(dest));
        when(mensajeRepository.save(any())).thenAnswer(inv -> {
            Mensaje m = inv.getArgument(0);
            return Mensaje.builder()
                    .id(901L)
                    .remitente(rem)
                    .destinatario(dest)
                    .fechaEnvio(LocalDateTime.now())
                    .build();
        });

        MensajeDto dto = new MensajeDto();
        dto.setDestinatarioId(22L);
        dto.setContenido("Mensaje clínico privado");

        service.guardarMensaje(dto, "rem@mail.com");

        ArgumentCaptor<MensajeEnviadoData> captor = ArgumentCaptor.forClass(MensajeEnviadoData.class);
        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.CHAT_MENSAJE_ENVIADO_V1),
                eq("901"),
                anyInt(),
                captor.capture()
        );

        MensajeEnviadoData data = captor.getValue();
        assertThat(data.mensajeId()).isEqualTo(901L);
        assertThat(data.remitenteId()).isEqualTo(11L);
        assertThat(data.destinatarioId()).isEqualTo(22L);
    }

    @Test
    @DisplayName("LiberarTurnosScheduler.liberarTurnosExpirados publica TURNOS_TURNO_EXPIRADO_V1")
    void liberarTurnosScheduler_publicaEvento() {
        LiberarTurnosScheduler scheduler = new LiberarTurnosScheduler();
        ReflectionTestUtils.setField(scheduler, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(scheduler, "turnoRepository", turnoRepository);

        Usuario med = Usuario.builder().id(1L).build();
        Usuario pac = Usuario.builder().id(2L).build();
        Turno expirado = Turno.builder()
                .id(404L)
                .medico(med)
                .paciente(pac)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .build();

        when(turnoRepository.findByEstadoAndFechaCreacionBefore(any(), any())).thenReturn(List.of(expirado));

        scheduler.liberarTurnosExpirados();

        verify(outboxPublisher, times(1)).publicar(
                eq(Topicos.TURNOS_TURNO_EXPIRADO_V1),
                eq("404"),
                anyInt(),
                argThat((TurnoExpiradoData d) -> d.turnoId().equals(404L))
        );
    }

    @Test
    @DisplayName("Si la acción de negocio falla con excepción, no se publica ningún evento")
    void accionConFalla_noPublicaEvento() {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarioRepository);

        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.persistence.EntityNotFoundException.class,
                () -> controller.verifyProfessional(999L)
        );

        verifyNoInteractions(outboxPublisher);
    }
}
