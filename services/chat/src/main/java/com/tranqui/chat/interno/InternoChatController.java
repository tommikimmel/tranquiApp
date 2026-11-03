package com.tranqui.chat.interno;

import com.tranqui.chat.mensaje.MensajeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas internas que el chat responde a otros servicios (sección 4.2), a través del gateway y
 * con JWT de servicio ({@link FiltroServicioInterno}). Hoy la usa el monolito para la lista de
 * pacientes del profesional.
 */
@RestController
@RequestMapping("/internal/chat")
public class InternoChatController {

    private final MensajeService mensajes;

    public InternoChatController(MensajeService mensajes) {
        this.mensajes = mensajes;
    }

    @GetMapping("/profesionales/{id}/resumen")
    public MensajeService.Resumen resumen(@PathVariable("id") Long id) {
        return mensajes.resumen(id);
    }
}
