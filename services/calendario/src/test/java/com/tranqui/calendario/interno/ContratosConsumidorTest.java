package com.tranqui.calendario.interno;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

/** Fase 10, T2, lado consumidor: calendario lee cada contrato de lo que le responden sin perder campos. */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/contactos.json", ClienteInterno.Contacto.class);
        Contratos.leer("internas/monolito/calendario-cuentas.json", ClienteInterno.Cuenta.class);
        Contratos.leer("internas/monolito/calendario-eventos-externos.json", ClienteInterno.EventoExterno.class);
        Contratos.leer("internas/monolito/turnos-confirmados.json", ClienteInterno.TurnoConfirmado.class);
    }
}
