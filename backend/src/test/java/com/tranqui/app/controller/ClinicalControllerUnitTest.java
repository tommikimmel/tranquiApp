package com.tranqui.app.controller;

import com.tranqui.app.model.InformeClinico;
import com.tranqui.app.model.SeguimientoDiario;
import com.tranqui.app.model.dto.InformeClinicoDto;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.service.ClinicalService;
import com.tranqui.app.service.NotificacionService;
import com.tranqui.app.service.TurnoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicalControllerUnitTest {

    @Mock private ClinicalService clinicalService;
    @Mock private NotificacionService notificacionService;
    @Mock private TurnoService turnoService;

    @InjectMocks
    private ClinicalController controller;

    private final UserDetails medico = new User("dra@mail.com", "x", Collections.emptyList());

    @Test
    void obtenerNotificaciones_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerNotificaciones(null).getStatusCodeValue());
    }

    @Test
    void obtenerNotificaciones_shouldDelegateToService() {
        when(notificacionService.obtenerNotificaciones("dra@mail.com")).thenReturn(List.of());
        ResponseEntity<?> response = controller.obtenerNotificaciones(medico);
        assertEquals(200, response.getStatusCodeValue());
        verify(notificacionService).obtenerNotificaciones("dra@mail.com");
    }

    @Test
    void marcarLeidas_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.marcarLeidas(null).getStatusCodeValue());
    }

    @Test
    void marcarLeidas_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.marcarLeidas(medico);
        assertEquals(200, response.getStatusCodeValue());
        verify(notificacionService).marcarTodasComoLeidas("dra@mail.com");
    }

    @Test
    void obtenerPacientesAtendidos_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerPacientesAtendidos(null).getStatusCodeValue());
    }

    @Test
    void obtenerPacientesAtendidos_shouldDelegateToService() {
        when(clinicalService.obtenerPacientesAtendidos("dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerPacientesAtendidos(medico).getStatusCodeValue());
    }

    @Test
    void obtenerSeguimientos_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerSeguimientos(1L, null).getStatusCodeValue());
    }

    @Test
    void obtenerSeguimientos_shouldDelegateToService() {
        when(clinicalService.obtenerSeguimientos(1L, "dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerSeguimientos(1L, medico).getStatusCodeValue());
    }

    @Test
    void crearSeguimiento_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.crearSeguimiento(1L, SeguimientoDiario.builder().build(), null).getStatusCodeValue());
    }

    @Test
    void crearSeguimiento_shouldDelegateToService() {
        SeguimientoDiario entry = SeguimientoDiario.builder().build();
        when(clinicalService.guardarSeguimiento(1L, "dra@mail.com", entry)).thenReturn(entry);
        assertEquals(200, controller.crearSeguimiento(1L, entry, medico).getStatusCodeValue());
    }

    @Test
    void obtenerInformes_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerInformes(1L, null).getStatusCodeValue());
    }

    @Test
    void obtenerInformes_shouldDelegateToService() {
        when(clinicalService.obtenerInformes(1L, "dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerInformes(1L, medico).getStatusCodeValue());
    }

    @Test
    void crearInforme_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.crearInforme(1L, InformeClinicoDto.builder().build(), null).getStatusCodeValue());
    }

    @Test
    void crearInforme_shouldDelegateToServiceWithDtoFields() {
        InformeClinicoDto dto = InformeClinicoDto.builder()
                .tipoInforme("GENERAL").planTrabajo("plan").contenido("contenido").nombreArchivo("a.pdf").build();
        InformeClinico informe = InformeClinico.builder().id(1L).build();
        when(clinicalService.guardarInforme("dra@mail.com", 1L, "GENERAL", "plan", "contenido", "a.pdf")).thenReturn(informe);

        ResponseEntity<InformeClinico> response = controller.crearInforme(1L, dto, medico);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(informe, response.getBody());
    }

    @Test
    void obtenerMisTurnos_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerMisTurnos(null).getStatusCodeValue());
    }

    @Test
    void obtenerMisTurnos_shouldDelegateToTurnoService() {
        when(turnoService.obtenerTurnosPaciente("dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerMisTurnos(medico).getStatusCodeValue());
    }

    @Test
    void obtenerMisSeguimientos_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerMisSeguimientos(null).getStatusCodeValue());
    }

    @Test
    void obtenerMisSeguimientos_shouldDelegateToService() {
        when(clinicalService.obtenerSeguimientosPaciente("dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerMisSeguimientos(medico).getStatusCodeValue());
    }

    @Test
    void crearMiSeguimiento_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.crearMiSeguimiento(SeguimientoDiario.builder().build(), null).getStatusCodeValue());
    }

    @Test
    void crearMiSeguimiento_shouldDelegateToService() {
        SeguimientoDiario entry = SeguimientoDiario.builder().build();
        when(clinicalService.guardarSeguimientoPaciente("dra@mail.com", entry)).thenReturn(entry);
        assertEquals(200, controller.crearMiSeguimiento(entry, medico).getStatusCodeValue());
    }

    @Test
    void obtenerMisInformes_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.obtenerMisInformes(null).getStatusCodeValue());
    }

    @Test
    void obtenerMisInformes_shouldDelegateToService() {
        when(clinicalService.obtenerInformesPaciente("dra@mail.com")).thenReturn(List.of());
        assertEquals(200, controller.obtenerMisInformes(medico).getStatusCodeValue());
    }

    @Test
    void actualizarPaciente_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.actualizarPaciente(1L, PacienteDto.builder().build(), null).getStatusCodeValue());
    }

    @Test
    void actualizarPaciente_shouldDelegateToService() {
        PacienteDto dto = PacienteDto.builder().nombre("Ana").build();
        when(clinicalService.actualizarPaciente(1L, "dra@mail.com", dto)).thenReturn(dto);
        assertEquals(200, controller.actualizarPaciente(1L, dto, medico).getStatusCodeValue());
    }

    @Test
    void eliminarInforme_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.eliminarInforme(1L, 2L, null).getStatusCodeValue());
    }

    @Test
    void eliminarInforme_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.eliminarInforme(1L, 2L, medico);
        assertEquals(200, response.getStatusCodeValue());
        verify(clinicalService).eliminarInforme(2L, "dra@mail.com");
    }

    @Test
    void editarInforme_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.editarInforme(1L, 2L, InformeClinicoDto.builder().build(), null).getStatusCodeValue());
    }

    @Test
    void editarInforme_shouldDelegateToServiceWithDtoFields() {
        InformeClinicoDto dto = InformeClinicoDto.builder().tipoInforme("GENERAL").planTrabajo("p").contenido("c").build();
        InformeClinico updated = InformeClinico.builder().id(2L).build();
        when(clinicalService.editarInforme(2L, "dra@mail.com", "GENERAL", "p", "c")).thenReturn(updated);

        ResponseEntity<InformeClinico> response = controller.editarInforme(1L, 2L, dto, medico);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(updated, response.getBody());
    }

    @Test
    void eliminarSeguimiento_shouldReturn401WhenAnonymous() {
        assertEquals(401, controller.eliminarSeguimiento(1L, 2L, null).getStatusCodeValue());
    }

    @Test
    void eliminarSeguimiento_shouldDelegateToService() {
        ResponseEntity<Void> response = controller.eliminarSeguimiento(1L, 2L, medico);
        assertEquals(200, response.getStatusCodeValue());
        verify(clinicalService).eliminarSeguimiento(2L, "dra@mail.com");
    }
}
