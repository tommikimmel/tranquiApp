package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * payment.simulation.enabled=true is the deliberate escape hatch documented on
 * TurnoService#paymentSimulationEnabled: only meant for exercising the rest of the booking flow
 * (Google Calendar sync, notifications, etc) while the real Mercado Pago integration is
 * broken/unlinked — never meant to be left on for real patients. Needs its own Spring context
 * (the property override changes the bean's @Value binding), same isolation pattern as
 * TurnoServiceGoogleCalendarTest.
 */
@SpringBootTest(properties = "payment.simulation.enabled=true")
class TurnoServicePaymentSimulationTest {

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private com.tranqui.app.repository.DisponibilidadRepository disponibilidadRepository;

    @MockBean
    private MercadoPagoService mercadoPagoService;

    private Usuario medico;
    private com.tranqui.app.model.Disponibilidad disponibilidad;

    @BeforeEach
    void setUp() {
        medico = Usuario.builder()
                .nombre("Medico Simulado")
                .email("medico.simulado@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .build();
        usuarioRepository.save(medico);

        disponibilidad = com.tranqui.app.model.Disponibilidad.builder()
                .medico(medico)
                .diaSemana(LocalDate.now().plusDays(2).getDayOfWeek().getValue())
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(12, 0))
                .build();
        disponibilidadRepository.save(disponibilidad);
    }

    @AfterEach
    void tearDown() {
        try { disponibilidadRepository.delete(disponibilidad); } catch (Exception e) {}
        try { usuarioRepository.delete(medico); } catch (Exception e) {}
    }

    @Test
    void whenMercadoPagoFailsAndSimulationEnabled_thenBookingStillConfirmsWithMockCheckoutUrl() throws Exception {
        when(mercadoPagoService.crearPreferenciaPago(any(Turno.class), any(Usuario.class)))
                .thenThrow(new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago."));

        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("Simulado")
                .emailPaciente("simulado@gmail.com")
                .build();

        com.tranqui.app.model.dto.TurnoResponseDto response = turnoService.reservarTurno(dto);

        // The booking is NOT blocked by the real Mercado Pago failure — it confirms with the
        // simulated mock checkout URL instead of propagating the IllegalStateException.
        assertNotNull(response);
        assertEquals("PENDIENTE_PAGO", response.getEstado());
        assertNotNull(response.getCheckoutUrl());
        assertTrue(response.getCheckoutUrl().contains("mock-preference-id"));

        if (response.getTurnoId() != null) {
            try { turnoRepository.deleteById(response.getTurnoId()); } catch (Exception e) {}
        }
        usuarioRepository.findByEmail("simulado@gmail.com").ifPresent(u -> {
            try { usuarioRepository.delete(u); } catch (Exception e) {}
        });
    }
}
