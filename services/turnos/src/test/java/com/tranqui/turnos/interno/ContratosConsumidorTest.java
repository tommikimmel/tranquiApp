package com.tranqui.turnos.interno;

import com.tranqui.eventos.contratos.Contratos;
import com.tranqui.eventos.contratos.ServidorCaptura;
import org.junit.jupiter.api.Test;

/**
 * Fase 10, T2, lado consumidor: turnos lee cada contrato de lo que le responden sin perder campos y manda sus pedidos
 * con la forma que espera el que los recibe. El lado productor está en los tests de cada dueño.
 */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/pacientes.json", ClienteInterno.PacienteMonolito.class);
        Contratos.leer("internas/fuente-identidad/paciente.json", ClienteInterno.PacienteMonolito.class);
        Contratos.leer("internas/monolito/turnos.json", ClienteInterno.TurnoMonolito.class);
        Contratos.leer("internas/monolito/profesionales.json", ClienteInterno.ProfesionalMonolito.class);
        Contratos.leer("internas/profesionales/profesionales.json", ClienteInterno.ProfesionalServicio.class);
        Contratos.leer("internas/pagos/cuentas-mp.json", ClienteInterno.CuentaMp.class);
        Contratos.leer("internas/calendario/ocupado.json", ClienteInterno.Intervalo.class);
    }

    @Test
    void pedidos() throws Exception {
        try (ServidorCaptura servidor = new ServidorCaptura("{\"id\":1}")) {
            ClienteInterno cliente = new ClienteInterno(servidor.url(), "clave-servicio-test-de-32-bytes-ok", "monolito");
            cliente.buscarOCrearPaciente(Contratos.leer("internas/fuente-identidad/pacientes-buscar-o-crear.request.json",
                    ClienteInterno.BuscarOCrearPaciente.class));
            Contratos.mismaForma("internas/fuente-identidad/pacientes-buscar-o-crear.request.json",
                    servidor.cuerpo("POST /internal/monolito/pacientes"));

            cliente.enviarDocumento(Contratos.leer("internas/notificaciones/documentos.request.json", ClienteInterno.DocumentoAdjunto.class));
            Contratos.mismaForma("internas/notificaciones/documentos.request.json", servidor.cuerpo("POST /internal/notificaciones/documentos"));
        }
    }
}
