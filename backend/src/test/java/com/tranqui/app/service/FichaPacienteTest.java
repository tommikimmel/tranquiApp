package com.tranqui.app.service;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichaPacienteTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    @InjectMocks
    private FichaPaciente fichaPaciente;

    @Test
    void siSaveAndFlushTiraExcepcion_sePropagaYOutboxNoPublicaNada() {
        Usuario paciente = Usuario.builder().id(10L).nombre("Carlos").rol(Rol.PACIENTE).build();
        PacienteDto dto = PacienteDto.builder().nombre("Carlos Modificado").build();

        when(usuarioRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("error en base"));

        assertThrows(DataIntegrityViolationException.class, () -> fichaPaciente.aplicarCambiosYGuardar(paciente, dto));

        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void publicaIdentidadUsuarioActualizadoConVersionDelUsuarioGuardado() {
        Usuario paciente = Usuario.builder().id(10L).nombre("Carlos").email("carlos@mail.com").rol(Rol.PACIENTE).build();
        Usuario guardado = Usuario.builder()
                .id(10L)
                .nombre("Carlos Modificado")
                .apellido("Perez")
                .email("carlos@mail.com")
                .telefono("+54 11 4444 5555")
                .rol(Rol.PACIENTE)
                .version(2)
                .notificacionesEmailHabilitadas(true)
                .build();

        when(usuarioRepository.saveAndFlush(any())).thenReturn(guardado);

        PacienteDto dto = PacienteDto.builder()
                .nombre("Carlos Modificado")
                .apellido("Perez")
                .telefono("11 4444 5555")
                .build();

        Usuario resultado = fichaPaciente.aplicarCambiosYGuardar(paciente, dto);

        assertThat(resultado).isSameAs(guardado);
        assertThat(guardado.getAggregateVersion()).isEqualTo(3);

        ArgumentCaptor<UsuarioActualizadoData> captor = ArgumentCaptor.forClass(UsuarioActualizadoData.class);
        verify(outboxPublisher).publicar(
                eq(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1),
                eq("10"),
                eq(3),
                captor.capture()
        );

        UsuarioActualizadoData data = captor.getValue();
        assertThat(data.usuarioId()).isEqualTo(10L);
        assertThat(data.email()).isEqualTo("carlos@mail.com");
        assertThat(data.nombre()).isEqualTo("Carlos Modificado");
        assertThat(data.apellido()).isEqualTo("Perez");
        assertThat(data.telefono()).isEqualTo("+54 11 4444 5555");
        assertThat(data.rol()).isEqualTo("PACIENTE");
        assertThat(data.emailHabilitado()).isTrue();
    }

    @Test
    void actualizar_siPacienteNoExiste_lanzaEntityNotFoundException() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> fichaPaciente.actualizar(99L, PacienteDto.builder().nombre("Ana").build()));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void validar_siFaltaNombre_lanzaIllegalArgumentException() {
        Usuario paciente = Usuario.builder().id(10L).nombre("Carlos").rol(Rol.PACIENTE).build();
        PacienteDto dto = PacienteDto.builder().nombre("   ").build();

        assertThrows(IllegalArgumentException.class, () -> fichaPaciente.aplicarCambiosYGuardar(paciente, dto));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verifyNoInteractions(outboxPublisher);
    }
}
