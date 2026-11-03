package com.tranqui.identidad.interno;

import com.tranqui.identidad.datos.NovedadesEnvios;
import com.tranqui.identidad.datos.TicketMensajes;
import com.tranqui.identidad.datos.Tickets;
import com.tranqui.identidad.datos.Usuarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La carga inicial no confía en lo que manda el monolito: cada fila con el contrato roto, un id repetido, una
 * referencia colgada o un valor fuera de dominio corta la carga entera antes de insertar nada.
 */
class CargaInicialIdentidadTest {

    private final Usuarios usuarios = mock(Usuarios.class);
    private final Tickets tickets = mock(Tickets.class);
    private final TicketMensajes mensajes = mock(TicketMensajes.class);
    private final NovedadesEnvios novedades = mock(NovedadesEnvios.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private CargaInicialIdentidad carga;

    @BeforeEach
    void setUp() {
        carga = new CargaInicialIdentidad(usuarios, tickets, mensajes, novedades, interno, tx, true);
        doAnswer(inv -> {
            Consumer<TransactionStatus> accion = inv.getArgument(0);
            accion.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    static Map<String, Object> usuario(long id, String email) {
        Map<String, Object> m = new HashMap<>();
        CargaInicialIdentidad.CLAVES_USUARIO.forEach(k -> m.put(k, null));
        m.put("id", id);
        m.put("version", 0);
        m.put("email", email);
        m.put("rol", "PSIQUIATRA");
        m.put("fecha_registro", "2026-01-01T10:00:00");
        m.put("fecha_nacimiento", "1990-05-01");
        m.put("numero_documento", "30123456");
        m.put("cuil", 20301234561L);
        m.put("email_verificado", "true");
        m.put("verificado_admin", true);
        m.put("iva_condition_id", 6);
        return m;
    }

    static Map<String, Object> ticket(long id, long creador) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(id));
        m.put("version", 1);
        m.put("creador_id", creador);
        m.put("asunto", "No puedo entrar");
        m.put("estado", "PENDIENTE");
        m.put("fecha_creacion", "2026-01-02T10:00:00");
        m.put("fecha_actualizacion", "2026-01-02T11:00:00");
        return m;
    }

    static Map<String, Object> mensaje(long id, long ticket, long autor) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("ticket_id", ticket);
        m.put("autor_id", autor);
        m.put("contenido", "Hola");
        m.put("fecha_envio", "2026-01-02T10:05:00");
        return m;
    }

    static Map<String, Object> novedad(String id) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("titulo", "Novedades de enero");
        m.put("estado", "ENVIADO");
        m.put("detalle", null);
        m.put("creado_en", "2026-01-03T10:00:00");
        m.put("finalizado_en", "");
        m.put("enviado_pacientes", true);
        m.put("enviado_profesionales", null);
        return m;
    }

    private void monolito(List<Map<String, Object>> us, List<Map<String, Object>> ts, List<Map<String, Object>> ms,
                          List<Map<String, Object>> ns) {
        when(interno.usuarios(eq(0), anyInt())).thenReturn(us.stream().map(ClienteInterno.UsuarioMonolito::new).toList());
        when(interno.tickets(eq(0), anyInt())).thenReturn(ts.stream().map(ClienteInterno.TicketMonolito::new).toList());
        when(interno.ticketMensajes(eq(0), anyInt())).thenReturn(ms.stream().map(ClienteInterno.TicketMensajeMonolito::new).toList());
        when(interno.novedadesEnvios(eq(0), anyInt())).thenReturn(ns.stream().map(ClienteInterno.NovedadesEnvioMonolito::new).toList());
    }

    @Test
    void datosValidos_seInsertanTodosYAjustaLasSecuencias() {
        monolito(List.of(usuario(1, " Ana@Test.com "), usuario(2, "b@test.com")), List.of(ticket(10, 1)),
                List.of(mensaje(100, 10, 2)), List.of(novedad("n-1")));
        assertThat(carga.cargar()).isEqualTo(5);
        verify(usuarios, times(2)).insertar(any());
        verify(usuarios).ajustarSecuencia();
        verify(tickets).insertar(any());
        verify(mensajes).insertar(any());
        verify(novedades).insertar(any());
    }

    @Test
    void paginaCompleta_pideLaSiguiente() {
        List<ClienteInterno.UsuarioMonolito> lote = new ArrayList<>();
        for (int i = 1; i <= CargaInicialIdentidad.PAGINA; i++) lote.add(new ClienteInterno.UsuarioMonolito(usuario(i, "u" + i + "@t.com")));
        when(interno.usuarios(0, CargaInicialIdentidad.PAGINA)).thenReturn(lote);
        when(interno.usuarios(1, CargaInicialIdentidad.PAGINA)).thenReturn(List.of());
        when(interno.tickets(0, CargaInicialIdentidad.PAGINA)).thenReturn(null);
        assertThat(carga.cargar()).isEqualTo(CargaInicialIdentidad.PAGINA);
        verify(interno).usuarios(1, CargaInicialIdentidad.PAGINA);
    }

    static Stream<Arguments> datosInvalidos() {
        return Stream.of(
                // usuarios
                Arguments.of("usuario sin una clave", mut(u -> u.remove("dni")), "claves incorrectas"),
                Arguments.of("usuario con clave de más", mut(u -> u.put("extra", 1)), "claves incorrectas"),
                Arguments.of("usuario sin id", mut(u -> u.put("id", null)), "id inválido"),
                Arguments.of("usuario con id cero", mut(u -> u.put("id", 0)), "id inválido"),
                Arguments.of("usuario con versión negativa", mut(u -> u.put("version", -1)), "versión inválida"),
                Arguments.of("usuario sin email", mut(u -> u.put("email", " ")), "email vacío"),
                Arguments.of("usuario con email no texto", mut(u -> u.put("email", 5)), "email vacío"),
                Arguments.of("usuario con rol fuera de dominio", mut(u -> u.put("rol", "ROOT")), "rol inválido"),
                Arguments.of("usuario sin fecha de registro", mut(u -> u.put("fecha_registro", null)), "fecha_registro nula"),
                Arguments.of("usuario con fecha ilegible", mut(u -> u.put("fecha_nacimiento", "ayer")), "fecha inválida"),
                Arguments.of("usuario con fecha de otro tipo", mut(u -> u.put("license_verified_at", 5)), "fecha inválida")
        );
    }

    private static Consumer<Map<String, Object>> mut(Consumer<Map<String, Object>> c) {
        return c;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datosInvalidos")
    void usuarioInvalido_cortaLaCarga(String caso, Consumer<Map<String, Object>> cambio, String mensaje) {
        Map<String, Object> u = usuario(1, "a@test.com");
        cambio.accept(u);
        monolito(List.of(u), List.of(), List.of(), List.of());
        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalStateException.class).hasMessageContaining(mensaje);
        verify(usuarios, never()).insertar(any());
    }

    @Test
    void usuariosRepetidos_cortanLaCarga() {
        monolito(List.of(usuario(1, "a@test.com"), usuario(1, "b@test.com")), List.of(), List.of(), List.of());
        assertThatThrownBy(carga::cargar).hasMessageContaining("id duplicado");
        monolito(List.of(usuario(1, "a@test.com"), usuario(2, "A@TEST.com")), List.of(), List.of(), List.of());
        assertThatThrownBy(carga::cargar).hasMessageContaining("Email duplicado");
        verify(usuarios, never()).insertar(any());
    }

    static Stream<Arguments> ticketsInvalidos() {
        return Stream.of(
                Arguments.of("sin una clave", mut(t -> t.remove("asunto")), "claves incorrectas"),
                Arguments.of("id cero", mut(t -> t.put("id", 0)), "id inválido"),
                Arguments.of("sin versión", mut(t -> t.put("version", null)), "versión inválida"),
                Arguments.of("creador inexistente", mut(t -> t.put("creador_id", 99)), "creador_id inexistente"),
                Arguments.of("asunto vacío", mut(t -> t.put("asunto", " ")), "asunto vacío"),
                Arguments.of("estado fuera de dominio", mut(t -> t.put("estado", "CERRADO")), "estado inválido"),
                Arguments.of("sin fecha de actualización", mut(t -> t.put("fecha_actualizacion", null)), "fecha inválida")
        );
    }

    @ParameterizedTest(name = "ticket {0}")
    @MethodSource("ticketsInvalidos")
    void ticketInvalido_cortaLaCarga(String caso, Consumer<Map<String, Object>> cambio, String mensaje) {
        Map<String, Object> t = ticket(10, 1);
        cambio.accept(t);
        monolito(List.of(usuario(1, "a@test.com")), List.of(t), List.of(), List.of());
        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalStateException.class).hasMessageContaining(mensaje);
        verify(usuarios, never()).insertar(any());
    }

    static Stream<Arguments> mensajesInvalidos() {
        return Stream.of(
                Arguments.of("sin una clave", mut(m -> m.remove("contenido")), "claves incorrectas"),
                Arguments.of("id negativo", mut(m -> m.put("id", -1)), "id inválido"),
                Arguments.of("ticket inexistente", mut(m -> m.put("ticket_id", 99)), "ticket_id inexistente"),
                Arguments.of("autor inexistente", mut(m -> m.put("autor_id", 99)), "autor_id inexistente"),
                Arguments.of("contenido nulo", mut(m -> m.put("contenido", null)), "contenido nulo"),
                Arguments.of("fecha ilegible", mut(m -> m.put("fecha_envio", "hoy")), "fecha inválida")
        );
    }

    @ParameterizedTest(name = "mensaje {0}")
    @MethodSource("mensajesInvalidos")
    void mensajeInvalido_cortaLaCarga(String caso, Consumer<Map<String, Object>> cambio, String mensaje) {
        Map<String, Object> m = mensaje(100, 10, 1);
        cambio.accept(m);
        monolito(List.of(usuario(1, "a@test.com")), List.of(ticket(10, 1)), List.of(m), List.of());
        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalStateException.class).hasMessageContaining(mensaje);
    }

    static Stream<Arguments> novedadesInvalidas() {
        return Stream.of(
                Arguments.of("sin una clave", mut(n -> n.remove("detalle")), "claves incorrectas"),
                Arguments.of("id vacío", mut(n -> n.put("id", " ")), "id vacío"),
                Arguments.of("título vacío", mut(n -> n.put("titulo", null)), "título vacío"),
                Arguments.of("estado fuera de dominio", mut(n -> n.put("estado", "PAUSADO")), "estado inválido"),
                Arguments.of("sin fecha de creación", mut(n -> n.put("creado_en", null)), "fecha inválida")
        );
    }

    @ParameterizedTest(name = "novedad {0}")
    @MethodSource("novedadesInvalidas")
    void novedadInvalida_cortaLaCarga(String caso, Consumer<Map<String, Object>> cambio, String mensaje) {
        Map<String, Object> n = novedad("n-1");
        cambio.accept(n);
        monolito(List.of(), List.of(), List.of(), List.of(n));
        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalStateException.class).hasMessageContaining(mensaje);
    }

    @Test
    void idsRepetidosEnTicketsMensajesYNovedades() {
        monolito(List.of(usuario(1, "a@test.com")), List.of(ticket(10, 1), ticket(10, 1)), List.of(), List.of());
        assertThatThrownBy(carga::cargar).hasMessageContaining("Ticket con id duplicado");
        monolito(List.of(usuario(1, "a@test.com")), List.of(ticket(10, 1)), List.of(mensaje(5, 10, 1), mensaje(5, 10, 1)), List.of());
        assertThatThrownBy(carga::cargar).hasMessageContaining("TicketMensaje con id duplicado");
        monolito(List.of(), List.of(), List.of(), List.of(novedad("n"), novedad("n")));
        assertThatThrownBy(carga::cargar).hasMessageContaining("NovedadesEnvio con id duplicado");
    }

    @Test
    void tipoNoSoportado_cortaLaCarga() {
        Map<String, Object> u = usuario(1, "a@test.com");
        u.put("id", true);
        monolito(List.of(u), List.of(), List.of(), List.of());
        assertThatThrownBy(carga::cargar).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conDatosPrevios_noCargaYQuedaLista() {
        when(usuarios.cantidad()).thenReturn(3L);
        assertThat(carga.listo()).isFalse();
        carga.cargarConReintentos();
        assertThat(carga.listo()).isTrue();
        verify(tx, never()).executeWithoutResult(any());
    }

    @Test
    void siFallaReintentaHastaCargar() {
        monolito(List.of(), List.of(), List.of(), List.of());
        when(interno.usuarios(eq(0), anyInt())).thenThrow(new IllegalStateException("monolito caído")).thenReturn(List.of());
        carga.cargarConReintentos();
        assertThat(carga.listo()).isTrue();
        verify(interno, times(2)).usuarios(eq(0), anyInt());
    }

    @Test
    void interrumpido_dejaDeReintentar() {
        when(interno.usuarios(eq(0), anyInt())).thenThrow(new IllegalStateException("monolito caído"));
        Thread.currentThread().interrupt();
        try {
            carga.cargarConReintentos();
            assertThat(carga.listo()).isFalse();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void deshabilitada_quedaListaSinHacerNada() {
        CargaInicialIdentidad apagada = new CargaInicialIdentidad(usuarios, tickets, mensajes, novedades, interno, tx, false);
        apagada.alArrancar();
        assertThat(apagada.listo()).isTrue();
        verify(usuarios, never()).cantidad();
    }
}
