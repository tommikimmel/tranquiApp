package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoPacientesTest {

    private static final List<EstadoTurno> ESTADOS = List.of(EstadoTurno.CONFIRMADO, EstadoTurno.PENDIENTE_VALIDACION);

    @Mock private TurnoRepository turnoRepository;
    @Mock private ResumenChatCliente resumenChatCliente;

    @InjectMocks
    private AccesoPacientes accesoPacientes;

    @Test
    void conUnTurnoConfirmadoOPendienteDeValidacionLoAtiende() {
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn(1L, 5L, ESTADOS)).thenReturn(true);

        assertDoesNotThrow(() -> accesoPacientes.exigirAtendido(1L, 5L));
        verify(resumenChatCliente, never()).resumen(1L);
    }

    @Test
    void sinTurnoPeroConChatLoAtiende() {
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn(1L, 5L, ESTADOS)).thenReturn(false);
        when(resumenChatCliente.resumen(1L)).thenReturn(new ResumenChat(List.of(5L), Map.of()));

        assertDoesNotThrow(() -> accesoPacientes.exigirAtendido(1L, 5L));
    }

    @Test
    void sinTurnoNiChatSeRechaza() {
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn(1L, 5L, ESTADOS)).thenReturn(false);
        when(resumenChatCliente.resumen(1L)).thenReturn(new ResumenChat(List.of(7L), Map.of()));

        assertThrows(AccessDeniedException.class, () -> accesoPacientes.exigirAtendido(1L, 5L));
    }

    @Test
    void conElChatCaidoYSinTurnoSeRechaza() {
        when(turnoRepository.existsByMedicoIdAndPacienteIdAndEstadoIn(1L, 5L, ESTADOS)).thenReturn(false);
        when(resumenChatCliente.resumen(1L)).thenReturn(ResumenChat.vacio());

        assertThrows(AccessDeniedException.class, () -> accesoPacientes.exigirAtendido(1L, 5L));
    }
}
