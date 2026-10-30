package com.tranqui.turnos.turnos;

import com.tranqui.eventos.data.TarifasProfesionalData.Tarifa;
import com.tranqui.turnos.agenda.Horarios;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import com.tranqui.turnos.web.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las reglas de la reserva: tarifa, agenda, precio, afiliado y cuenta de Mercado Pago, antes de guardar nada. */
class ReservasTest {

    private static final LocalDate FECHA = LocalDate.now().plusDays(4);
    private static final LocalTime HORA = LocalTime.of(10, 0);

    private final Turnos turnos = mock(Turnos.class);
    private final Personas personas = mock(Personas.class);
    private final Profesionales profesionales = mock(Profesionales.class);
    private final Horarios horarios = mock(Horarios.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final Publicador publicador = mock(Publicador.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        agenda(true, true, new BigDecimal("70000"));
        when(personas.porId(2L)).thenReturn(Optional.of(persona(2L, true)));
        when(horarios.disponibles(eq(2L), eq(FECHA), anyString())).thenReturn(List.of(HORA));
        when(profesionales.tarifa(eq(2L), anyString())).thenReturn(Optional.empty());
        when(interno.buscarOCrearPaciente(any())).thenReturn(new ClienteInterno.PacienteMonolito(1L, 0, "PACIENTE", "ana@mail.com",
                "Ana", "Paz", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true));
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Turno>) inv.getArgument(0)).doInTransaction(null));
        when(turnos.crear(any())).thenAnswer(inv -> {
            Turno t = inv.getArgument(0);
            t.id = 50L;
            return t;
        });
    }

    private void agenda(boolean online, boolean presencial, BigDecimal precio) {
        when(profesionales.porId(2L)).thenReturn(Optional.of(new Profesionales.Agenda(2L, 45, 0, online, presencial, precio,
                "Consultorio 4", null, null, null, null, null, null)));
    }

    private static Personas.Persona persona(long id, boolean mp) {
        return new Personas.Persona(id, 0, "PSIQUIATRA", "lucia@mail.com", "Lucía", "Paz", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, true, mp);
    }

    private static Tarifa tarifa(String id, String precio, boolean habilitado, boolean obraSocial, String online, String presencial,
                                 boolean agenda, boolean receta) {
        return new Tarifa(id, id, new BigDecimal(precio), habilitado, obraSocial, null, online != null ? new BigDecimal(online) : null,
                presencial != null ? new BigDecimal(presencial) : null, agenda, receta);
    }

    private Reservas reservas(boolean mp, boolean simulado) {
        return new Reservas(turnos, personas, profesionales, horarios, interno, publicador, tx, mp, simulado);
    }

    private static Dtos.ReservaTurnoDto dto(String tipo, String modalidad, String servicioId, String obraSocial, String afiliado) {
        return new Dtos.ReservaTurnoDto(2L, FECHA, HORA, tipo, modalidad, servicioId, obraSocial, null, afiliado, "Ana",
                " Ana@Mail.com ", "1155556666");
    }

    private Turno creado() {
        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos).crear(captor.capture());
        return captor.getValue();
    }

    @Test
    void datosFueraDeDominio_seRechazan() {
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("VIP", "ONLINE", null, null, null)))
                .hasMessageContaining("Tipo de turno inválido");
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "TELEPATIA", null, null, null)))
                .hasMessageContaining("Modalidad inválida");
        when(turnos.tieneTurnoActivo(eq("ana@mail.com"), any())).thenReturn(true);
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null)))
                .hasMessageContaining("Ya tenés un turno activo");
    }

    @Test
    void medicoInexistente() {
        when(profesionales.porId(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null)))
                .isInstanceOf(NoEncontradoException.class);
        Dtos.ReservaTurnoDto sinMedico = new Dtos.ReservaTurnoDto(null, FECHA, HORA, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> reservas(false, false).reservar(sinMedico)).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void particular_conElPrecioDelPerfilYTodoPublicado() {
        Dtos.TurnoResponseDto r = reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null));
        assertThat(r.turnoId()).isEqualTo(50L);
        Turno t = creado();
        assertThat(t.precio).isEqualByComparingTo("70000");
        assertThat(t.horaFin).isEqualTo(HORA.plusMinutes(45));
        assertThat(t.estado).isEqualTo(Turno.PENDIENTE_PAGO);
        verify(personas).guardar(any());
        verify(publicador).reservado(any(), anyString());
        verify(publicador, never()).documentoSolicitado(any(), any(), any());
        verify(publicador).actualizado(any());
    }

    @Test
    void sinModalidad_seDeduceOSePide() {
        assertThatThrownBy(() -> reservas(false, false).reservar(dto(null, null, null, null, null)))
                .hasMessageContaining("presencial u online");
        agenda(false, true, null);
        reservas(false, false).reservar(dto(null, null, null, null, null));
        Turno t = creado();
        assertThat(t.modalidad).isEqualTo("PRESENCIAL");
        assertThat(t.precio).isEqualByComparingTo("60000");
    }

    @Test
    void horarioNoLibre_oSobreturnoOcupado() {
        when(horarios.disponibles(eq(2L), eq(FECHA), anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null)))
                .hasMessageContaining("ya no está disponible");

        Turno ocupado = new Turno();
        ocupado.horaInicio = HORA;
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of(ocupado));
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("SOBRETUNO", "ONLINE", null, null, null)))
                .hasMessageContaining("sobreturno");
    }

    @Test
    void sobreturnoLibre_conSuPrecio() {
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of());
        reservas(false, false).reservar(dto("SOBRETUNO", "ONLINE", null, null, null));
        Turno t = creado();
        assertThat(t.servicioId).isEqualTo("sobreturno");
        assertThat(t.precio).isEqualByComparingTo("90000");
    }

    @Test
    void preciosPorDefectoDeCadaTipo() {
        reservas(false, false).reservar(dto("RECETA", "ONLINE", null, null, null));
        reservas(false, false).reservar(dto("CERTIFICADO", "ONLINE", null, null, null));
        reservas(false, false).reservar(dto("OSDE", "ONLINE", null, "OSDE", "123"));
        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos, org.mockito.Mockito.times(3)).crear(captor.capture());
        assertThat(captor.getAllValues()).extracting(t -> t.precio.intValue()).containsExactly(45000, 55000, 10500);
        assertThat(captor.getAllValues()).extracting(t -> t.servicioId).containsExactly("receta-fuera", "certificado", "obra_social");
        verify(publicador).documentoSolicitado(any(), eq("receta-fuera"), eq("RECETA"));
    }

    @Test
    void obraSocial_exigeAfiliadoYArmaLaMetadata() {
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("OBRA_SOCIAL", "ONLINE", null, "OSDE", " ")))
                .hasMessageContaining("número de afiliado");

        when(profesionales.tarifa(2L, "osde")).thenReturn(Optional.of(tarifa("osde", "12000", true, true, null, "13000", true, false)));
        reservas(false, false).reservar(dto("OBRA_SOCIAL", "PRESENCIAL", null, "OSDE", "123"));
        Turno t = creado();
        assertThat(t.metadataAfiliado).isEqualTo("OSDE - 123");
        assertThat(t.precio).isEqualByComparingTo("13000");
    }

    @Test
    void metadataDelAfiliado_casos() {
        reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, "OSDE", "OSDE 210 - 123"));
        reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, "Swiss", null));
        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnos, org.mockito.Mockito.times(2)).crear(captor.capture());
        assertThat(captor.getAllValues()).extracting(t -> t.metadataAfiliado).containsExactly("OSDE 210 - 123", "Swiss");
    }

    @Test
    void tarifaPropia_documentoSinAgendaConPrecioOnline() {
        when(profesionales.tarifa(2L, "informe-x")).thenReturn(Optional.of(tarifa("informe-x", "30000", true, false, "25000", null, false, false)));
        reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", "informe-x", null, null));
        Turno t = creado();
        assertThat(t.servicioId).isEqualTo("informe-x");
        assertThat(t.ocupaAgenda).isFalse();
        assertThat(t.modalidad).isNull();
        assertThat(t.precio).isEqualByComparingTo("30000");
        verify(horarios, never()).disponibles(any(), any(), any());
        verify(publicador).documentoSolicitado(any(), eq("informe-x"), eq("PARTICULAR"));
    }

    @Test
    void tarifaPropia_conAgendaYPrecioPorModalidad_yRecetaPropia() {
        when(profesionales.tarifa(2L, "terapia")).thenReturn(Optional.of(tarifa("terapia", "30000", true, true, "25000", null, true, true)));
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", "terapia", null, null)))
                .hasMessageContaining("afiliado");
        reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", "terapia", null, "A-1"));
        Turno t = creado();
        assertThat(t.precio).isEqualByComparingTo("25000");
        assertThat(t.esReceta).isTrue();
    }

    @Test
    void tarifaPropiaDeshabilitada_usaLaParticular() {
        when(profesionales.tarifa(2L, "terapia")).thenReturn(Optional.of(tarifa("terapia", "30000", false, false, null, null, true, false)));
        when(profesionales.tarifa(2L, "particular")).thenReturn(Optional.of(tarifa("particular", "50000", true, false, null, null, true, false)));
        reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", "terapia", null, null));
        Turno t = creado();
        assertThat(t.servicioId).isEqualTo("particular");
        assertThat(t.precio).isEqualByComparingTo("50000");
    }

    @Test
    void mercadoPagoSinVincular_cortaAntesDeGuardar() {
        when(personas.porId(2L)).thenReturn(Optional.of(persona(2L, false)));
        assertThatThrownBy(() -> reservas(true, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null)))
                .hasMessageContaining("Mercado Pago");
        reservas(true, true).reservar(dto("PARTICULAR", "ONLINE", null, null, null));
        verify(turnos).crear(any());
    }

    @Test
    void identidadCaida_503() {
        when(interno.buscarOCrearPaciente(any())).thenThrow(new ResourceAccessException("caído"));
        assertThatThrownBy(() -> reservas(false, false).reservar(dto("PARTICULAR", "ONLINE", null, null, null)))
                .isInstanceOf(ServicioNoDisponibleException.class);
        verify(turnos, never()).crear(any());
    }
}
