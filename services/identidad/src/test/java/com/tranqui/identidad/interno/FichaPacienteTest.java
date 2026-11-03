package com.tranqui.identidad.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.web.NoEncontradoException;
import com.tranqui.identidad.interno.FichaPaciente.Credencial;
import com.tranqui.identidad.interno.FichaPaciente.Domicilio;
import com.tranqui.identidad.interno.FichaPaciente.Ficha;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La ficha que edita profesionales: todas las validaciones juntas y cómo se guardan domicilio y credencial. */
class FichaPacienteTest {

    private final Usuarios usuarios = mock(Usuarios.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final FichaPaciente ficha = new FichaPaciente(usuarios, outbox);

    private static Ficha ficha(String nombre, String tipoDoc, Integer nroDoc, String nacimiento, String telefono,
                               Domicilio domicilio, Credencial credencial, String obraSocial) {
        return new Ficha(nombre, "Diaz", telefono, null, null, obraSocial, null, "F", nacimiento, null, tipoDoc, nroDoc,
                null, credencial, domicilio);
    }

    private static Domicilio domicilio(String calle, String numero, String localidad, String provincia) {
        return new Domicilio(calle, numero, "1", "A", "1414", localidad, provincia, "Argentina");
    }

    @Test
    void sinCuerpo_seRechaza() {
        assertThatThrownBy(() -> ficha.validar(null)).hasMessageContaining("falta el cuerpo");
    }

    @Test
    void juntaTodosLosErrores() {
        Ficha mala = ficha(" ", "CUIT", 123, "2999-01-01", "12ab", domicilio("Calle", "", "", ""),
                new Credencial(null, " ", null, null), null);
        assertThatThrownBy(() -> ficha.validar(mala)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("el nombre del paciente es obligatorio")
                .hasMessageContaining("tipo de documento")
                .hasMessageContaining("entre 6 y 9 dígitos")
                .hasMessageContaining("no puede ser futura")
                .hasMessageContaining("teléfono")
                .hasMessageContaining("domicilio requiere")
                .hasMessageContaining("falta seleccionar la obra social")
                .hasMessageContaining("número de afiliado es obligatorio");
    }

    @Test
    void fechasYAfiliadoFueraDeRango() {
        assertThatThrownBy(() -> ficha.validar(ficha("Lara", null, -1234567, "1850-01-01", null, null, null, null)))
                .hasMessageContaining("6 y 9 dígitos").hasMessageContaining("no es válida");
        assertThatThrownBy(() -> ficha.validar(ficha("Lara", null, null, "04/03/1995", null, null, null, null)))
                .hasMessageContaining("formato válido");
        assertThatThrownBy(() -> ficha.validar(ficha("Lara", null, null, null, null, null, new Credencial(1, "ab#", null, null), null)))
                .hasMessageContaining("entre 6 y 30 caracteres");
    }

    @Test
    void fichaValida_pasa() {
        ficha.validar(ficha("Lara", "DNI", 40111222, "1995-03-04", "1155556666",
                domicilio("Av. Siempre Viva", "742", "CABA", "Buenos Aires"), new Credencial(1, "ABC-123456", null, null), "OSDE"));
        ficha.validar(ficha("Lara", " ", null, " ", " ", domicilio(null, null, null, null), null, null));
    }

    private Usuario guardado() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).actualizar(captor.capture());
        return captor.getValue();
    }

    private void existePaciente() {
        when(usuarios.buscarPorId(5L)).thenReturn(Optional.of(Usuario.builder().id(5L).version(2).email("p@t.com").rol("PACIENTE")
                .credencialPan("viejo").obraSocial("Vieja").build()));
        when(usuarios.actualizar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void actualizar_conDomicilioYPlan_publicaElCambio() {
        existePaciente();
        ficha.actualizar(5L, ficha("Lara", "DNI", 40111222, "1995-03-04", "1155556666",
                domicilio("Av. Siempre Viva", "742", "CABA", "Buenos Aires"), new Credencial(7, "ABC-123456", "210", "tok"), "OSDE"));
        Usuario u = guardado();
        assertThat(u.fechaNacimiento()).isEqualTo(LocalDate.of(1995, 3, 4));
        assertThat(u.domicilioLocalidad()).isEqualTo("CABA");
        assertThat(u.credencialCodEntidad()).isEqualTo(7);
        assertThat(u.obraSocial()).isEqualTo("OSDE 210");
        verify(outbox).publicar(eq(Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1), eq("usuario"), eq("5"), anyInt(), any());
    }

    @Test
    void actualizar_planSinObraSocialYCredencialSinPlan() {
        existePaciente();
        ficha.actualizar(5L, ficha("Lara", null, null, null, null, null, new Credencial(7, "ABC-123456", "210", null), " "));
        assertThat(guardado().obraSocial()).isEqualTo("Obra Social 210");

        org.mockito.Mockito.reset(usuarios);
        existePaciente();
        ficha.actualizar(5L, ficha("Lara", null, null, null, null, null, new Credencial(7, "ABC-123456", " ", null), "OSDE"));
        assertThat(guardado().obraSocial()).isEqualTo("OSDE");
    }

    @Test
    void actualizar_sinCredencial_borraLaObraSocial() {
        existePaciente();
        ficha.actualizar(5L, ficha("Lara", null, null, null, null, null, null, "OSDE"));
        Usuario u = guardado();
        assertThat(u.credencialPan()).isNull();
        assertThat(u.obraSocial()).isNull();
    }

    @Test
    void actualizar_pacienteInexistente() {
        assertThatThrownBy(() -> ficha.actualizar(9L, ficha("Lara", null, null, null, null, null, null, null)))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void clienteInterno_pideCadaTablaDeLaCargaInicial() throws Exception {
        List<String> pedidos = new CopyOnWriteArrayList<>();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            pedidos.add(ex.getRequestURI().getPath() + " " + (ex.getRequestHeaders().getFirst("Authorization") != null));
            byte[] cuerpo = "[{\"id\":1}]".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
        try {
            String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
            ClienteInterno conClave = new ClienteInterno(url, "clave-servicio-test-de-32-bytes-ok");
            assertThat(conClave.usuarios(0, 10)).singleElement().satisfies(u -> assertThat(u.valores()).containsEntry("id", 1));
            assertThat(conClave.tickets(0, 10)).hasSize(1);
            assertThat(conClave.ticketMensajes(0, 10)).hasSize(1);
            assertThat(conClave.novedadesEnvios(0, 10)).hasSize(1);
            new ClienteInterno(url, " ").usuarios(0, 10);
            assertThat(pedidos).containsExactly("/internal/monolito/identidad/usuarios true", "/internal/monolito/identidad/tickets true",
                    "/internal/monolito/identidad/ticket-mensajes true", "/internal/monolito/identidad/novedades-envios true",
                    "/internal/monolito/identidad/usuarios false");
        } finally {
            servidor.stop(0);
        }
        assertThat(new ClienteInterno.UsuarioMonolito(null).valores()).isEmpty();
    }
}
