package com.tranqui.eventos.consumo;

/**
 * Algo que tiene que terminar antes de que el servicio empiece a consumir eventos, por ejemplo la
 * carga inicial de sus copias en el primer arranque. Si hay beans de este tipo, el consumidor declara
 * la topología (la cola sigue juntando eventos) pero no consume hasta que todos estén listos.
 *
 * <p>Evita que un evento viejo que quedó en la cola (por ejemplo, de cuando el servicio estuvo apagado
 * en una vuelta atrás) se procese antes de copiar el estado que lo vuelve inofensivo.
 */
public interface EsperaAntesDeConsumir {

    /** {@code true} cuando el consumo ya puede arrancar. */
    boolean listo();
}
