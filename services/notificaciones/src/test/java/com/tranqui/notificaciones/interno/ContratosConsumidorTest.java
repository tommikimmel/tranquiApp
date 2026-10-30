package com.tranqui.notificaciones.interno;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

/** Fase 10, T2, lado consumidor: notificaciones lee cada contrato de lo que le responden sin perder campos. */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/monolito/contactos.json", ClienteInterno.ContactoInterno.class);
        Contratos.leer("internas/monolito/notificaciones-vigentes.json", ClienteInterno.NotificacionInterna.class);
        Contratos.leer("internas/turnos/enlaces-asistencia.json", ClienteInterno.EnlacesAsistencia.class);
    }
}
