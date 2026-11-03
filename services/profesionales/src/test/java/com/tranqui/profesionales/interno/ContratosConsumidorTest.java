package com.tranqui.profesionales.interno;

import com.tranqui.eventos.contratos.Contratos;
import com.tranqui.eventos.contratos.ServidorCaptura;
import com.tranqui.profesionales.clinica.Dtos;
import org.junit.jupiter.api.Test;

/**
 * Fase 10, T2, lado consumidor: profesionales lee cada contrato de lo que le responden sin perder campos y manda sus
 * pedidos con la forma que espera el que los recibe. El lado productor está en los tests de cada dueño.
 */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/profesionales.json", ClienteInterno.ProfesionalMonolito.class);
        Contratos.leer("internas/fuente-identidad/profesional.json", ClienteInterno.ProfesionalMonolito.class);
        Contratos.leer("internas/fuente-identidad/profesionales-estado.json", ClienteInterno.ProfesionalMonolito.class);
        Contratos.leer("internas/monolito/pacientes.json", ClienteInterno.PacienteMonolito.class);
        Contratos.leer("internas/fuente-identidad/paciente.json", ClienteInterno.PacienteMonolito.class);
        Contratos.leer("internas/monolito/turnos.json", ClienteInterno.TurnoMonolito.class);
        Contratos.leer("internas/turnos/turnos.json", ClienteInterno.TurnoServicio.class);
        Contratos.leer("internas/pagos/cuentas-mp.json", ClienteInterno.CuentaMp.class);
        Contratos.leer("internas/suscripciones/estado.json", ClienteInterno.EstadoSuscripcion.class);
        Contratos.leer("internas/monolito/clinica-seguimientos.json", ClienteInterno.SeguimientoMonolito.class);
        Contratos.leer("internas/monolito/clinica-informes.json", ClienteInterno.InformeMonolito.class);
        Contratos.leer("internas/monolito/clinica-recetas.json", ClienteInterno.RecetaMonolito.class);
        Contratos.leer("internas/monolito/clinica-solicitudes.json", ClienteInterno.SolicitudMonolito.class);
        Contratos.leer("internas/chat/resumen.json", ClienteChat.Resumen.class);
    }

    @Test
    void pedidos() throws Exception {
        try (ServidorCaptura servidor = new ServidorCaptura("{\"id\":1}")) {
            ClienteInterno cliente = new ClienteInterno(servidor.url(), "clave-servicio-test-de-32-bytes-ok", "monolito");

            cliente.guardarIdentidad(5L, Contratos.leer("internas/fuente-identidad/profesional-identidad.request.json", ClienteInterno.Identidad.class));
            Contratos.mismaForma("internas/fuente-identidad/profesional-identidad.request.json",
                    servidor.cuerpo("PUT /internal/monolito/profesionales/5/identidad"));

            cliente.actualizarPaciente(6L, Contratos.leer("internas/fuente-identidad/paciente-actualizar.request.json", Dtos.PacienteDto.class));
            Contratos.mismaForma("internas/fuente-identidad/paciente-actualizar.request.json", servidor.cuerpo("PUT /internal/monolito/pacientes/6"));

            cliente.enviarMailReceta(Contratos.leer("internas/notificaciones/recetas.request.json", ClienteInterno.NotificacionReceta.class));
            Contratos.mismaForma("internas/notificaciones/recetas.request.json", servidor.cuerpo("POST /internal/notificaciones/recetas"));
        }
    }
}
