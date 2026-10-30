package com.tranqui.chat.interno;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

/** Fase 10, T2, lado consumidor: chat lee cada contrato de lo que le responden sin perder campos. */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/contactos.json", ClienteInterno.Contacto.class);
        Contratos.leer("internas/monolito/mensajes.json", ClienteInterno.MensajeMonolito.class);
        Contratos.leer("internas/monolito/turnos-confirmados.json", ClienteInterno.TurnoConfirmado.class);
    }
}
