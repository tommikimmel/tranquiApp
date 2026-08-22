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
 * payment.simulation.enabled defaults to false (not set in src/test/resources/application.yml),
 * so this class exercises reservarTurno's behavior when the *real* Mercado Pago integration
 * fails and the simulation escape hatch is OFF — the production posture once mercadopago is
 * actually enabled for real patients. Mocks MercadoPagoService directly (its own Spring context,
 * same isolation pattern as TurnoServiceGoogleCalendarTest) so both failure modes can be forced
 * deterministically instead of fighting the real SDK/EncryptionUtil.
 */
@SpringBootTest
class TurnoServiceMercadoPagoFailureTest {

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
                .nombre("Medico MP")
                .email("medico.mp@gmail.com")
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
    void whenMercadoPagoThrowsIllegalStateException_thenReservarTurnoPropagatesIt() throws Exception {
        when(mercadoPagoService.crearPreferenciaPago(any(Turno.class), any(Usuario.class)))
                .thenThrow(new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago."));

        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 0))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("Sin MP")
                .emailPaciente("sin.mp@gmail.com")
                .build();

        // GlobalExceptionHandler maps IllegalStateException to a clean 409 — reservarTurno must
        // preserve the exception type here instead of wrapping it into an opaque 500.
        assertThrows(IllegalStateException.class, () -> turnoService.reservarTurno(dto));

        // reservarTurno is @Transactional — the whole booking, including the newly-created
        // patient row, must roll back; nothing should be left half-booked.
        assertTrue(usuarioRepository.findByEmail("sin.mp@gmail.com").isEmpty());
    }

    @Test
    void whenMercadoPagoThrowsGenericException_thenReservarTurnoWrapsItInRuntimeException() throws Exception {
        when(mercadoPagoService.crearPreferenciaPago(any(Turno.class), any(Usuario.class)))
                .thenThrow(new java.io.IOException("Mercado Pago no responde"));

        com.tranqui.app.model.dto.ReservaTurnoDto dto = com.tranqui.app.model.dto.ReservaTurnoDto.builder()
                .medicoId(medico.getId())
                .fecha(LocalDate.now().plusDays(2))
                .hora(LocalTime.of(9, 45))
                .tipo(TipoTurno.PARTICULAR)
                .nombrePaciente("MP Caido")
                .emailPaciente("mp.caido@gmail.com")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> turnoService.reservarTurno(dto));
        assertTrue(ex.getMessage().contains("Error al conectar con la pasarela de Mercado Pago"));

        assertTrue(usuarioRepository.findByEmail("mp.caido@gmail.com").isEmpty());
    }
}
