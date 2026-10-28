package com.tranqui.identidad.cuenta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = BorradorContactoNovedades.class, ignored = BorradorContactoNovedadesNoOp.class)
public class BorradorContactoNovedadesNoOp implements BorradorContactoNovedades {
    @Override
    public void eliminarContacto(String email) {
        // No-op en I2b. I2c implementará el borrado en Resend.
    }
}
