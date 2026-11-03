package com.tranqui.identidad.novedades;

import com.tranqui.identidad.cuenta.BorradorContactoNovedades;
import org.springframework.stereotype.Component;

/** Al eliminar una cuenta, borra su contacto de novedades en Resend (Ley 25.326), como el monolito. */
@Component
public class BorradorContactoNovedadesResend implements BorradorContactoNovedades {

    private final NovedadesService novedadesService;

    public BorradorContactoNovedadesResend(NovedadesService novedadesService) {
        this.novedadesService = novedadesService;
    }

    @Override
    public void eliminarContacto(String email) {
        novedadesService.eliminarContacto(email);
    }
}
