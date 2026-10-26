package com.tranqui.app.controller;

import com.tranqui.app.service.ResumenChat;
import com.tranqui.app.service.ResumenChatCliente;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 2, vuelta atrás: la misma ruta interna que el servicio de chat. Cuando el gateway vuelve a
 * mandar el chat al monolito, {@code /internal/chat/**} llega acá y se responde con las tablas del
 * monolito. Protegida por {@link com.tranqui.app.config.FiltroServicioInterno}.
 */
@RestController
@RequestMapping("/internal/chat")
public class InternoChatController {

    private final ResumenChatCliente resumenChat;

    public InternoChatController(ResumenChatCliente resumenChat) {
        this.resumenChat = resumenChat;
    }

    @GetMapping("/profesionales/{id}/resumen")
    public ResumenChat resumen(@PathVariable Long id) {
        return resumenChat.calcularLocal(id);
    }
}
