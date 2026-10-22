package com.tranqui.notificaciones.contacto;

import com.tranqui.notificaciones.interno.ClienteInterno;
import com.tranqui.notificaciones.notificacion.Notificacion;
import com.tranqui.notificaciones.notificacion.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CargaInicialContactosTest {

    private final ContactoService contactos = mock(ContactoService.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final CargaInicialContactos carga = new CargaInicialContactos(contactos, interno, notificaciones, true);

    @Test
    void primerArranque_copiaContactosYNotificacionesVigentes() {
        when(contactos.cantidad()).thenReturn(0L);
        when(notificaciones.count()).thenReturn(0L);
        when(interno.contactos(0, 500)).thenReturn(List.of(
                new ClienteInterno.ContactoInterno(1L, "a@mail.com", "Ana", "Paz", "PACIENTE", false)));
        LocalDateTime hace2Horas = LocalDateTime.now().minusHours(2);
        when(interno.notificacionesVigentes()).thenReturn(List.of(
                new ClienteInterno.NotificacionInterna(1L, "Turno confirmado", "m", true, "TURNO_CONFIRMADO", hace2Horas)));

        carga.cargarConReintentos();

        verify(contactos).guardar(1L, "a@mail.com", "Ana", "Paz", "PACIENTE", false);
        ArgumentCaptor<Notificacion> copia = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificaciones).save(copia.capture());
        assertThat(copia.getValue().isLeido()).isTrue();
        assertThat(copia.getValue().getFechaCreacion()).isEqualTo(hace2Horas);
    }

    @Test
    void siYaHabiaContactos_noCopiaNada() {
        when(contactos.cantidad()).thenReturn(5L);

        carga.cargarConReintentos();

        verify(interno, never()).contactos(anyInt(), anyInt());
        verify(interno, never()).notificacionesVigentes();
    }

    @Test
    void siFallaLaCopiaDeNotificaciones_reintentaSinRepetirLosContactos() {
        when(contactos.cantidad()).thenReturn(0L, 0L, 1L);
        when(notificaciones.count()).thenReturn(0L);
        when(interno.contactos(0, 500)).thenReturn(List.of(
                new ClienteInterno.ContactoInterno(1L, "a@mail.com", "Ana", "Paz", "PACIENTE", true)));
        when(interno.notificacionesVigentes()).thenThrow(new IllegalStateException("gateway caído")).thenReturn(List.of());

        carga.cargarConReintentos();

        verify(interno, times(1)).contactos(0, 500);
        verify(interno, times(2)).notificacionesVigentes();
        verify(notificaciones, never()).save(any());
    }
}
