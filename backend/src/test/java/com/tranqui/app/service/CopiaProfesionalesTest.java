package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 5: la copia del monolito del perfil, la disponibilidad y las tarifas del servicio de profesionales
 * (lo que aplican los eventos y la resincronización), y las rutas del monolito con el flag prendido.
 */
@SpringBootTest
class CopiaProfesionalesTest {

    @Autowired private CopiaProfesionales copia;
    @Autowired private ResincronizacionProfesionales resincronizacion;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private DisponibilidadRepository disponibilidadRepository;
    @Autowired private TarifaMedicoRepository tarifaRepository;
    @Autowired private MedicoService medicoService;
    @Autowired private DisponibilidadService disponibilidadService;

    private Usuario profesional() {
        return usuarioRepository.save(Usuario.builder().nombre("Ana").apellido("Paz")
                .email("copia." + System.nanoTime() + "@mail.com").rol(Rol.PSIQUIATRA)
                .specialty("Psicología").ofreceOnline(true).ofrecePresencial(false).build());
    }

    private static PerfilProfesionalData perfil(String especialidad, int duracion) {
        return new PerfilProfesionalData("contacto@mail.com", "Av. Corrientes 1234, CABA", -34.6, -58.4,
                null, "3", "B", null, "1234", "MN", "CABA", 1234, null, null, null, null,
                "Médica", especialidad, "20-12345678-9", new BigDecimal("60000"), "#E8F5EE", true, true,
                "[{\"lugar\":\"Hospital\"}]", null, null, null, null, null, "123456789012", "Dra. Ana Paz",
                null, null, "Ansiedad", "Descripción", "UBA", 10, "Adultos", duracion, 10);
    }

    @Test
    void aplicarPerfil_copiaLosCamposYDejaLaVersionPorEncimaDeLaDelEvento() {
        Usuario u = profesional();

        copia.aplicarPerfil(u.getId(), 40, perfil("Psiquiatría", 30));

        Usuario guardado = usuarioRepository.findById(u.getId()).orElseThrow();
        assertThat(guardado.getSpecialty()).isEqualTo("Psiquiatría");
        assertThat(guardado.getDuracionTurnoMinutos()).isEqualTo(30);
        assertThat(guardado.getIntervaloEntreTurnosMinutos()).isEqualTo(10);
        assertThat(guardado.isOfrecePresencial()).isTrue();
        assertThat(guardado.getDomicilioAtencion()).isEqualTo("Av. Corrientes 1234, CABA");
        assertThat(guardado.getMatriculaNumero()).isEqualTo(1234);
        assertThat(guardado.getNombre()).isEqualTo("Ana");
        assertThat(guardado.getAggregateVersion()).isGreaterThan(40);
    }

    @Test
    void aplicarDisponibilidad_reemplazaTodasLasFranjas() {
        Usuario u = profesional();
        disponibilidadRepository.save(Disponibilidad.builder().medico(u).diaSemana(5)
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(9, 0)).build());

        copia.aplicarDisponibilidad(new DisponibilidadProfesionalData(u.getId(), List.of(
                new DisponibilidadProfesionalData.Franja(1, "09:00", "12:00", "ONLINE"),
                new DisponibilidadProfesionalData.Franja(2, "14:00", "17:30", "PRESENCIAL"))));

        List<Disponibilidad> franjas = disponibilidadRepository.findByMedicoId(u.getId());
        assertThat(franjas).hasSize(2);
        assertThat(franjas).anyMatch(d -> d.getDiaSemana() == 1 && d.getModalidad() == Modalidad.ONLINE
                && d.getHoraInicio().equals(LocalTime.of(9, 0)));
        assertThat(franjas).anyMatch(d -> d.getDiaSemana() == 2 && d.getHoraFin().equals(LocalTime.of(17, 30)));
        assertThat(franjas).noneMatch(d -> d.getDiaSemana() == 5);
    }

    @Test
    void aplicarTarifas_actualizaPorServicioConservaElIdYBorraLasQueNoVienen() {
        Usuario u = profesional();
        TarifaMedico particular = tarifaRepository.save(TarifaMedico.builder().medico(u).servicioId("particular")
                .label("Consulta").precio(new BigDecimal("50000")).habilitado(true).build());
        tarifaRepository.save(TarifaMedico.builder().medico(u).servicioId("certificado")
                .label("Certificado").precio(new BigDecimal("1000")).habilitado(true).requiereAgenda(false).build());

        copia.aplicarTarifas(new TarifasProfesionalData(u.getId(), List.of(
                new TarifasProfesionalData.Tarifa("particular", "Consulta particular", new BigDecimal("65000"), true,
                        false, null, null, new BigDecimal("70000"), true, false),
                new TarifasProfesionalData.Tarifa("receta-fuera", "Receta fuera de turno", new BigDecimal("45000"), true,
                        false, null, null, null, false, true))));

        List<TarifaMedico> tarifas = tarifaRepository.findByMedicoId(u.getId());
        assertThat(tarifas).extracting(TarifaMedico::getServicioId).containsExactlyInAnyOrder("particular", "receta-fuera");
        TarifaMedico actualizada = tarifaRepository.findByMedicoIdAndServicioId(u.getId(), "particular").orElseThrow();
        assertThat(actualizada.getId()).isEqualTo(particular.getId());
        assertThat(actualizada.getPrecio()).isEqualByComparingTo("65000");
        assertThat(actualizada.getPrecioPresencial()).isEqualByComparingTo("70000");
        assertThat(tarifaRepository.findByMedicoIdAndServicioId(u.getId(), "receta-fuera").orElseThrow().isEsReceta()).isTrue();
    }

    @Test
    void resincronizacion_aplicaPerfilDisponibilidadYTarifasDeCadaProfesional() {
        Usuario u = profesional();

        int aplicados = resincronizacion.aplicar(List.of(
                new ResincronizacionProfesionales.ProfesionalServicio(u.getId(), 5, perfil("Psiquiatría", 60),
                        List.of(new DisponibilidadProfesionalData.Franja(3, "10:00", "11:00", null)),
                        List.of(new TarifasProfesionalData.Tarifa("particular", "Consulta", new BigDecimal("1"), true,
                                false, null, null, null, true, false))),
                new ResincronizacionProfesionales.ProfesionalServicio(-1L, 1, perfil("X", 45), List.of(), List.of())));

        assertThat(aplicados).isEqualTo(2);
        assertThat(usuarioRepository.findById(u.getId()).orElseThrow().getDuracionTurnoMinutos()).isEqualTo(60);
        assertThat(disponibilidadRepository.findByMedicoId(u.getId())).hasSize(1);
        assertThat(tarifaRepository.findByMedicoId(u.getId())).hasSize(1);
    }

    @Test
    void conElFlag_elMonolitoNoAtiendeNiEscribeElPerfil() {
        Usuario u = profesional();
        ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(medicoService), "profesionalesExterno", true);
        ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(disponibilidadService), "profesionalesExterno", true);
        try {
            assertThatThrownBy(() -> medicoService.obtenerPerfil(u.getEmail())).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> medicoService.obtenerMedicosActivos()).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> medicoService.actualizarConfigAgenda(u.getEmail(), 30, 0)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> disponibilidadService.guardarDisponibilidades(u.getEmail(), Modalidad.ONLINE, List.of()))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(medicoService), "profesionalesExterno", false);
            ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(disponibilidadService), "profesionalesExterno", false);
        }
        assertThat(usuarioRepository.findById(u.getId()).orElseThrow().getDuracionTurnoMinutos()).isNull();
    }
}
