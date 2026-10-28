package com.tranqui.app.eventos;

import com.tranqui.app.controller.AdminController;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.AccountService;
import com.tranqui.app.service.AvisoSuscripcionPublisher;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Eventos que el monolito publica para el servicio de notificaciones (Fase 1, N2).
 */
@ExtendWith(MockitoExtension.class)
class EventosNotificacionesPublicacionTest {

    @Mock private OutboxPublisher outboxPublisher;
    @Mock private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Cambiar la preferencia de mails publica usuario.actualizado con emailHabilitado")
    void preferenciaDeMails_publicaUsuarioActualizado() {
        AccountService service = new AccountService();
        ReflectionTestUtils.setField(service, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        Usuario user = Usuario.builder().id(7L).email("ana@mail.com").nombre("Ana").rol(Rol.PACIENTE).build();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user));

        service.actualizarPreferenciasNotificacion("ana@mail.com", false);

        ArgumentCaptor<UsuarioActualizadoData> captor = ArgumentCaptor.forClass(UsuarioActualizadoData.class);
        verify(outboxPublisher).publicar(eq(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1), eq("7"), anyInt(), captor.capture());
        assertThat(captor.getValue().emailHabilitado()).isFalse();
        assertThat(captor.getValue().rol()).isEqualTo("PACIENTE");
    }

    @Test
    @DisplayName("Cambiar el rol desde el panel publica usuario.actualizado con el rol nuevo")
    void cambioDeRol_publicaUsuarioActualizado() {
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "outboxPublisher", outboxPublisher);
        ReflectionTestUtils.setField(controller, "usuarioRepository", usuarioRepository);
        Usuario user = Usuario.builder().id(9L).email("x@mail.com").rol(Rol.PACIENTE).build();
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(user));

        controller.updateRol(9L, Map.of("rol", "ADMIN"));

        ArgumentCaptor<UsuarioActualizadoData> captor = ArgumentCaptor.forClass(UsuarioActualizadoData.class);
        verify(outboxPublisher).publicar(eq(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1), eq("9"), anyInt(), captor.capture());
        assertThat(captor.getValue().rol()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Los avisos de suscripción salen como suscripciones.aviso.programado con el plan y el monto")
    void avisoDeSuscripcion_armaElData() {
        AvisoSuscripcionPublisher publisher = new AvisoSuscripcionPublisher(outboxPublisher);
        Plan plan = new Plan();
        plan.setName("Profesional");
        Usuario prof = Usuario.builder().id(3L).build();
        Subscription sub = new Subscription();
        sub.setId(11L);
        sub.setPlan(plan);
        sub.setProfessional(prof);
        LocalDateTime cobro = LocalDateTime.of(2026, 11, 1, 0, 0);

        publisher.publicar(sub, SuscripcionAvisoData.PREAVISO_RENOVACION, new BigDecimal("9999"), cobro,
                null, "monthly", null, null);

        ArgumentCaptor<SuscripcionAvisoData> captor = ArgumentCaptor.forClass(SuscripcionAvisoData.class);
        verify(outboxPublisher).publicar(eq(Topicos.SUSCRIPCIONES_AVISO_PROGRAMADO_V1), eq("suscripcion"), eq("11"),
                anyInt(), captor.capture());
        SuscripcionAvisoData data = captor.getValue();
        assertThat(data.profesionalId()).isEqualTo(3L);
        assertThat(data.nombrePlan()).isEqualTo("Profesional");
        assertThat(data.monto()).isEqualByComparingTo("9999");
        assertThat(data.fecha()).isEqualTo(cobro);
        assertThat(data.ciclo()).isEqualTo("monthly");
    }
}
