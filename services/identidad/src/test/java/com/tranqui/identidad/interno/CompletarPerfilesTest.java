package com.tranqui.identidad.interno;

import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.identidad.datos.PerfilesRegistro;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompletarPerfilesTest {

    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final PerfilesRegistro perfiles = mock(PerfilesRegistro.class);
    private final CompletarPerfiles tarea = new CompletarPerfiles(interno, perfiles);

    @Test
    void completaSoloLosQueFaltan_ySigueSiUnoFalla() {
        PerfilProfesionalData perfil = mock(PerfilProfesionalData.class);
        when(interno.perfilesProfesionales()).thenReturn(List.of(new ClienteInterno.PerfilProfesional(1L, perfil),
                new ClienteInterno.PerfilProfesional(2L, perfil), new ClienteInterno.PerfilProfesional(3L, perfil)));
        when(perfiles.buscarPorUsuarioId(anyLong())).thenReturn(Optional.empty());
        when(perfiles.buscarPorUsuarioId(2L)).thenReturn(Optional.of(perfil));
        doThrow(new IllegalStateException("sin usuario")).when(perfiles).guardar(1L, perfil);

        tarea.completar();

        verify(perfiles, never()).guardar(2L, perfil);
        verify(perfiles).guardar(3L, perfil);
    }

    @Test
    void conProfesionalesCaido_noRompe() {
        when(interno.perfilesProfesionales()).thenThrow(new IllegalStateException("caído"));

        tarea.completar();

        verify(perfiles, never()).guardar(anyLong(), any());
    }
}
