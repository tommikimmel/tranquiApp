package com.tranqui.app.service;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

/**
 * Fase 10, T2, lado consumidor: el monolito lee cada contrato de lo que le responden los servicios sin perder campos.
 * El lado productor está en los tests de cada servicio.
 */
class ContratosConsumidorTest {

    @Test
    void respuestas() {
        Contratos.leer("internas/calendario/ocupado.json", HorariosOcupadosCliente.Intervalo.class);
        Contratos.leer("internas/profesionales/profesionales.json", ResincronizacionProfesionales.ProfesionalServicio.class);
        Contratos.leer("internas/turnos/turnos.json", ResincronizacionTurnos.TurnoServicio.class);
        Contratos.leer("internas/suscripciones/estado.json", ResincronizacionSuscripciones.EstadoSuscripcionDto.class);
        Contratos.leer("internas/chat/resumen.json", ResumenChat.class);
    }
}
