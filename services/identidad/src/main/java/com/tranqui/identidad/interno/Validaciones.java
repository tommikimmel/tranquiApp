package com.tranqui.identidad.interno;

import java.util.regex.Pattern;

/** Reglas comunes para lo que llega por las rutas internas: nunca se confía en lo que validó el que llama. */
final class Validaciones {

    private static final Pattern MAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validaciones() {
    }

    static boolean mailValido(String mail) {
        return mail != null && mail.length() <= 150 && MAIL.matcher(mail.trim()).matches();
    }

    /** Entre 6 y 15 dígitos, sin contar el +54 (como la ficha del paciente del monolito). */
    static boolean telefonoValido(String telefono) {
        String digitos = telefono.trim().replaceFirst("^\\+54\\s*", "").replaceAll("\\D", "");
        return digitos.matches("\\d{6,15}");
    }

    /** El teléfono con +54 adelante, como lo guarda hoy el monolito. */
    static String conPrefijo(String telefono) {
        if (telefono != null && !telefono.trim().isEmpty() && !telefono.trim().startsWith("+54")) {
            return "+54 " + telefono.trim();
        }
        return telefono;
    }
}
