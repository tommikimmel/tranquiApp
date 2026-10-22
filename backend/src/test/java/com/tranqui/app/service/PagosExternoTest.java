package com.tranqui.app.service;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Pago;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoConcepto;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.ReservaTurnoDto;
import com.tranqui.app.model.dto.SolicitarConceptoDto;
import com.tranqui.app.model.dto.TurnoResponseDto;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.ConceptoSolicitadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoReservadoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Fase 4 (P1): con {@code pagos.externo=true} el monolito no habla con Mercado Pago. La reserva publica el
 * turno sin crear la preferencia (pero sigue exigiendo la cuenta vinculada), la cancelación solo decide si
 * corresponde reembolso y la API de conceptos publica la solicitud.
 */
@SpringBootTest(properties = {"pagos.externo=true", "mercadopago.enabled=true", "payment.simulation.enabled=false"})
class PagosExternoTest {

    @Autowired private TurnoService turnoService;
    @Autowired private ConceptoService conceptoService;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private DisponibilidadRepository disponibilidadRepository;
    @Autowired private SolicitudDocumentoRepository solicitudRepository;

    @MockBean private MercadoPagoService mercadoPagoService;
    @MockBean private ReembolsoService reembolsoService;
    @MockBean private OutboxPublisher outboxPublisher;

    private Usuario medico;
    private Usuario paciente;
    private com.tranqui.app.model.Disponibilidad disponibilidad;

    @BeforeEach
    void setUp() {
        medico = usuarioRepository.save(Usuario.builder().nombre("Lucía").email("lucia.externo@gmail.com")
                .rol(Rol.PSIQUIATRA).mpVinculada(true).build());
        paciente = usuarioRepository.save(Usuario.builder().nombre("Martina").email("martina.externo@gmail.com")
                .rol(Rol.PACIENTE).build());
        disponibilidad = disponibilidadRepository.save(com.tranqui.app.model.Disponibilidad.builder()
                .medico(medico)
                .diaSemana(LocalDate.now().plusDays(3).getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(12, 0))
                .build());
    }

    @AfterEach
    void tearDown() {
        pagoRepository.deleteAll();
        solicitudRepository.deleteAll();
        turnoRepository.deleteAll();
        try { disponibilidadRepository.delete(disponibilidad); } catch (Exception e) {}
        usuarioRepository.findByEmail("reserva.externo@gmail.com").ifPresent(usuarioRepository::delete);
        try { usuarioRepository.delete(paciente); } catch (Exception e) {}
        try { usuarioRepository.delete(medico); } catch (Exception e) {}
    }

    private ReservaTurnoDto reserva() {
        return ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(3))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("Reserva")
                .emailPaciente("reserva.externo@gmail.com")
                .build();
    }

    @Test
    void reservar_publicaElTurnoConElTituloYNoCreaLaPreferencia() throws Exception {
        TurnoResponseDto respuesta = turnoService.reservarTurno(reserva());

        assertEquals("PENDIENTE_PAGO", respuesta.getEstado());
        assertEquals("", respuesta.getCheckoutUrl());
        verify(mercadoPagoService, never()).crearPreferenciaPago(any(), any());
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publicar(eq(Topicos.TURNOS_TURNO_RESERVADO_V1), anyString(), anyInt(), data.capture());
        TurnoReservadoData reservado = (TurnoReservadoData) data.getValue();
        assertEquals(respuesta.getTurnoId(), reservado.turnoId());
        assertEquals("Consulta Psiquiátrica - Lucía", reservado.tituloCobro());
    }

    @Test
    void reservar_sinLaCuentaVinculada_cortaConElMismoError() {
        medico.setMpVinculada(false);
        usuarioRepository.save(medico);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> turnoService.reservarTurno(reserva()));

        assertTrue(error.getMessage().contains("no vinculó su cuenta de Mercado Pago"));
        assertEquals(0, turnoRepository.count());
    }

    @Test
    void cancelar_unTurnoPagado_soloDecideElReembolso() throws Exception {
        Turno turno = turnoRepository.save(Turno.builder().medico(medico).paciente(paciente)
                .fecha(LocalDate.now().plusDays(5)).horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(10, 45))
                .tipo(TipoTurno.PARTICULAR).modalidad(Modalidad.ONLINE).estado(EstadoTurno.CONFIRMADO)
                .precio(new BigDecimal("60000")).ocupaAgenda(true).build());
        pagoRepository.save(Pago.builder().turno(turno).transactionId("mp-externo-1").estado(EstadoPago.APROBADO)
                .monto(new BigDecimal("60000")).fechaPago(LocalDateTime.now()).build());

        turnoService.cancelarTurno(turno.getId(), paciente.getEmail());

        verifyNoInteractions(reembolsoService);
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publicar(eq(Topicos.TURNOS_TURNO_CANCELADO_V1), anyString(), anyInt(), data.capture());
        TurnoCanceladoData cancelado = (TurnoCanceladoData) data.getValue();
        assertTrue(cancelado.correspondeReembolso());
        assertEquals("EN_PROCESO", cancelado.resultadoReembolso());
        assertEquals(EstadoPago.APROBADO, pagoRepository.findByTransactionId("mp-externo-1").orElseThrow().getEstado());
    }

    @Test
    void conceptos_publicaLaSolicitudSinCrearLaPreferencia() throws Exception {
        SolicitarConceptoDto dto = new SolicitarConceptoDto();
        dto.setMedicoId(medico.getId());
        dto.setTipoConcepto(TipoConcepto.CERTIFICADO);
        dto.setPrecio(new BigDecimal("55000"));

        var solicitud = conceptoService.crearSolicitud(dto, paciente.getEmail());

        assertEquals("", conceptoService.generarCheckoutUrl(solicitud));
        verify(mercadoPagoService, never()).crearPreferenciaDocumento(any());
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publicar(eq(Topicos.PROFESIONALES_CONCEPTO_SOLICITADO_V1), eq("solicitud"),
                anyString(), anyInt(), data.capture());
        ConceptoSolicitadoData solicitada = (ConceptoSolicitadoData) data.getValue();
        assertEquals(solicitud.getId(), solicitada.solicitudId());
        assertEquals("CERTIFICADO - Lucía", solicitada.titulo());
    }
}
