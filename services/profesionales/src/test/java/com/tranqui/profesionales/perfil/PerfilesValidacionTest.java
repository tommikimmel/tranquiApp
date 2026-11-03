package com.tranqui.profesionales.perfil;

import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import com.tranqui.profesionales.datos.Tarifas;
import com.tranqui.profesionales.interno.ClienteInterno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las validaciones del perfil y de la agenda (las mismas que MedicoService del monolito), antes de guardar nada. */
class PerfilesValidacionTest {

    private final Profesionales profesionales = mock(Profesionales.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final Perfiles perfiles = new Perfiles(profesionales, mock(Tarifas.class), mock(Publicador.class), interno, tx, false);
    private MedicoDto dto;

    @BeforeEach
    void setUp() {
        Profesional p = mock(Profesional.class);
        when(p.id()).thenReturn(2L);
        when(p.perfil()).thenReturn(mock(PerfilProfesionalData.class));
        when(profesionales.porEmail("lucia@mail.com")).thenReturn(Optional.of(p));
        when(interno.guardarIdentidad(anyLong(), any())).thenThrow(new ResourceAccessException("identidad caída"));
        dto = mock(MedicoDto.class);
    }

    private void rechaza(String mensaje) {
        assertThatThrownBy(() -> perfiles.actualizarPerfil("lucia@mail.com", dto))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(mensaje);
    }

    @Test
    void cadaReglaDelPerfil() {
        when(dto.emailContacto()).thenReturn(" no-es-mail ");
        rechaza("email de contacto");
        when(dto.emailContacto()).thenReturn(null);

        when(dto.domicilioAtencion()).thenReturn("corto");
        rechaza("entre 8 y 140");
        when(dto.domicilioAtencion()).thenReturn(null);

        when(dto.ofrecePresencial()).thenReturn(true);
        when(dto.domicilioAtencionPiso()).thenReturn("x".repeat(21));
        rechaza("largo máximo permitido (20");
        when(dto.domicilioAtencionPiso()).thenReturn(" ");

        when(dto.fotoUrl()).thenReturn("data:image/png;base64," + "A".repeat(5 * 1024 * 1024));
        rechaza("demasiado grande");
        when(dto.fotoUrl()).thenReturn(null);

        when(dto.codigoRefeps()).thenReturn("123");
        rechaza("REFEPS");
        when(dto.codigoRefeps()).thenReturn(null);

        when(dto.selloLinea1()).thenReturn("x".repeat(41));
        rechaza("línea 1");
        when(dto.selloLinea1()).thenReturn(null);
        when(dto.selloLinea2()).thenReturn("x".repeat(41));
        rechaza("línea 2");
        when(dto.selloLinea2()).thenReturn(null);
        when(dto.selloLinea3()).thenReturn("x".repeat(26));
        rechaza("línea 3");
        verify(interno, never()).guardarIdentidad(anyLong(), any());
    }

    @Test
    void conMatriculaInfoYConIdentidadCaida_noGuardaNada() {
        when(dto.matriculaInfo()).thenReturn(new MedicoDto.MatriculaInfoDto("MN", "CABA", 12345,
                new MedicoDto.EspecialidadDto("Psiquiatría"), new MedicoDto.AsociadaDto("MP", "BA", 999)));
        when(dto.telefono()).thenReturn(" 1155556666 ");
        assertThatThrownBy(() -> perfiles.actualizarPerfil("lucia@mail.com", dto)).isInstanceOf(ServicioNoDisponibleException.class);
        when(dto.matriculaInfo()).thenReturn(new MedicoDto.MatriculaInfoDto("MN", "CABA", null, null, null));
        assertThatThrownBy(() -> perfiles.actualizarPerfil("lucia@mail.com", dto)).isInstanceOf(ServicioNoDisponibleException.class);
        verify(tx, never()).executeWithoutResult(any());
    }

    @Test
    void configAgenda_reglas() {
        assertThatThrownBy(() -> perfiles.actualizarConfigAgenda("lucia@mail.com", 0, null)).hasMessageContaining("mayor a 0");
        assertThatThrownBy(() -> perfiles.actualizarConfigAgenda("lucia@mail.com", 45, -1)).hasMessageContaining("negativo");
        assertThatThrownBy(() -> perfiles.actualizarPerfil("nadie@mail.com", dto)).isInstanceOf(NoEncontradoException.class);
    }
}
