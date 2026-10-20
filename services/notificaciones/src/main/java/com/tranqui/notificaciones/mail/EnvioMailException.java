package com.tranqui.notificaciones.mail;

/** El mail no salió (Resend o SMTP fallaron). El evento se reintenta. */
public class EnvioMailException extends RuntimeException {

    public EnvioMailException(String mensaje) {
        super(mensaje);
    }

    public EnvioMailException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
