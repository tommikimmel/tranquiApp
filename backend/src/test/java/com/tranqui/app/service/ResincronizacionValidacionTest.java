package com.tranqui.app.service;

import com.sun.net.httpserver.HttpServer;
import com.tranqui.eventos.data.TurnoActualizadoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Fase 10: la resincronización periódica no confía en lo que devuelven turnos y profesionales. Las filas inválidas
 * se saltean (las demás se aplican), un error del servicio deja la copia como está y la pasada siguiente pide desde
 * la última que salió bien. Contra un servidor HTTP de prueba, sin Spring.
 */
class ResincronizacionValidacionTest {

    private static final String CLAVE = "clave-servicio-test-de-32-bytes-ok";

    private HttpServer servidor;
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String respuesta = "[]";

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            pedidos.add(ex.getRequestURI().toString());
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void turnos_saltealasFilasInvalidasYAplicaLasDemas() {
        CopiaTurnos copia = mock(CopiaTurnos.class);
        respuesta = "[{\"version\":3,\"turno\":{\"turnoId\":10,\"pacienteId\":1,\"profesionalId\":2,\"estado\":\"CONFIRMADO\"}},"
                + "{\"version\":1,\"turno\":{\"turnoId\":-5}},"
                + "{\"version\":1,\"turno\":{\"turnoId\":11,\"estado\":\"INVENTADO\"}},"
                + "{\"version\":-1,\"turno\":{\"turnoId\":12}},"
                + "{\"version\":1}]";
        new ResincronizacionTurnos(copia, true, url(), CLAVE).resincronizar();
        verify(copia).aplicar(any(TurnoActualizadoData.class), eq(3));
        verify(copia, org.mockito.Mockito.times(1)).aplicar(any(), anyInt());
    }

    @Test
    void turnos_errorDelServicioOAlAplicar_noCortaYLaSiguientePideDesde() {
        CopiaTurnos copia = mock(CopiaTurnos.class);
        ResincronizacionTurnos r = new ResincronizacionTurnos(copia, true, url(), CLAVE);
        status = 500;
        r.resincronizar();
        assertThat(pedidos).containsExactly("/internal/turnos/turnos");

        status = 200;
        respuesta = "[{\"version\":1,\"turno\":{\"turnoId\":10}},{\"version\":1,\"turno\":{\"turnoId\":11}}]";
        doThrow(new IllegalStateException("versión vieja")).when(copia).aplicar(any(), eq(1));
        r.resincronizar();
        respuesta = "null";
        r.resincronizar();
        assertThat(pedidos.get(1)).isEqualTo("/internal/turnos/turnos");
        assertThat(pedidos.get(2)).startsWith("/internal/turnos/turnos?desde=");
    }

    @Test
    void desactivada_noLlama() {
        CopiaTurnos turnos = mock(CopiaTurnos.class);
        CopiaProfesionales profesionales = mock(CopiaProfesionales.class);
        new ResincronizacionTurnos(turnos, false, url(), CLAVE).resincronizar();
        new ResincronizacionTurnos(turnos, true, " ", CLAVE).resincronizar();
        new ResincronizacionProfesionales(profesionales, true, url(), "").resincronizar();
        new ResincronizacionProfesionales(profesionales, false, url(), CLAVE).resincronizar();
        assertThat(pedidos).isEmpty();
        verifyNoInteractions(turnos, profesionales);
    }

    @Test
    void profesionales_saltealasFilasInvalidasYAplicaCadaParte() {
        CopiaProfesionales copia = mock(CopiaProfesionales.class);
        respuesta = "[{\"profesionalId\":7,\"version\":2,\"perfil\":{\"emailContacto\":\"lucia@mail.com\"},"
                + "\"disponibilidad\":[{\"diaSemana\":1,\"horaInicio\":\"09:00\",\"horaFin\":\"12:00\",\"modalidad\":\"ONLINE\"}],"
                + "\"tarifas\":[{\"servicioId\":\"consulta\",\"precio\":1000}]},"
                + "{\"profesionalId\":8,\"version\":1},"
                + "{\"profesionalId\":null,\"version\":1,\"perfil\":{}},"
                + "{\"profesionalId\":9,\"version\":1,\"perfil\":{\"emailContacto\":\"no-es-mail\"}},"
                + "{\"profesionalId\":10,\"version\":1,\"tarifas\":[{\"servicioId\":\"x\",\"precio\":-1}]}]";
        new ResincronizacionProfesionales(copia, true, url(), CLAVE).resincronizar();
        verify(copia).aplicarPerfil(eq(7L), eq(2), any());
        verify(copia).aplicarDisponibilidad(any());
        verify(copia).aplicarTarifas(any());
        verify(copia, never()).aplicarPerfil(eq(9L), anyInt(), any());
        verify(copia, never()).aplicarPerfil(eq(null), anyInt(), any());
    }

    @Test
    void profesionales_errorDelServicioOAlAplicar_noCorta() {
        CopiaProfesionales copia = mock(CopiaProfesionales.class);
        ResincronizacionProfesionales r = new ResincronizacionProfesionales(copia, true, url(), CLAVE);
        status = 503;
        r.resincronizar();
        status = 200;
        respuesta = "[{\"profesionalId\":7,\"version\":2,\"perfil\":{}},{\"profesionalId\":8,\"version\":2,\"perfil\":{}}]";
        doThrow(new IllegalStateException("x")).when(copia).aplicarPerfil(eq(7L), anyInt(), any());
        r.resincronizar();
        verify(copia).aplicarPerfil(eq(8L), eq(2), any());
        r.resincronizar();
        assertThat(pedidos.get(2)).startsWith("/internal/profesionales/profesionales?desde=");
    }

    @Test
    void resumenDeChat_invalidoSeTrataComoChatCaido() {
        com.tranqui.app.repository.MensajeRepository mensajes = mock(com.tranqui.app.repository.MensajeRepository.class);
        ResumenChatCliente cliente = new ResumenChatCliente(mensajes, url(), CLAVE);
        respuesta = "{\"pacientesConChat\":[3,4],\"noLeidosPorPaciente\":{\"3\":2}}";
        ResumenChat bueno = cliente.resumen(7L);
        assertThat(bueno.pacientesConChat()).containsExactly(3L, 4L);
        assertThat(bueno.noLeidosPorPaciente()).containsEntry(3L, 2);

        respuesta = "{\"pacientesConChat\":[-1],\"noLeidosPorPaciente\":{\"3\":-2}}";
        assertThat(cliente.resumen(7L)).isEqualTo(ResumenChat.vacio());
        respuesta = "null";
        assertThat(cliente.resumen(7L)).isEqualTo(ResumenChat.vacio());
        assertThat(pedidos).allMatch(p -> p.equals("/internal/chat/profesionales/7/resumen"));
        verifyNoInteractions(mensajes);
    }

    @Test
    void horariosOcupados_salteanLosIntervalosInvalidos() {
        HorariosOcupadosCliente cliente = new HorariosOcupadosCliente(mock(GoogleCalendarService.class), true, url(), CLAVE);
        com.tranqui.app.model.Usuario medico = new com.tranqui.app.model.Usuario();
        medico.setId(7L);
        respuesta = "[{\"inicio\":\"09:00\",\"fin\":\"10:00\",\"todoElDia\":false},{\"inicio\":null,\"fin\":\"11:00\"},"
                + "{\"inicio\":\"00:00\",\"fin\":\"23:59:59\",\"todoElDia\":true}]";
        List<HorariosOcupadosCliente.Intervalo> ocupados = cliente.ocupados(medico, java.time.LocalDate.of(2026, 11, 5));
        assertThat(ocupados).hasSize(2);
        assertThat(ocupados.get(0).inicio()).isEqualTo(java.time.LocalTime.of(9, 0));
        assertThat(pedidos).containsExactly("/internal/calendario/profesionales/7/ocupado?fecha=2026-11-05");

        status = 500;
        assertThat(cliente.ocupados(medico, java.time.LocalDate.of(2026, 11, 5))).isEmpty();
    }
}
