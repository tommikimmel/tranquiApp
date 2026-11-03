package com.tranqui.app.eventos;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.PagoWebhookHandler;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.ConceptoAprobadoData;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 4: lo que el monolito (turnos, profesionales) hace con los eventos del servicio de pagos.
 * Confirma o no los turnos pagados con la misma lógica del webhook, guarda el link del checkout para
 * "Mis Turnos", marca los reembolsos y documentos, y sabe qué profesionales tienen Mercado Pago
 * vinculado (el buscador y la reserva lo exigen).
 *
 * <p>Fase 6: con {@code turnos.externo=true} los turnos (y la copia de su pago) los actualiza el servicio de
 * turnos y llegan a la copia del monolito con {@code turnos.turno.actualizado}; los handlers de turnos no
 * hacen nada. Los documentos de la API de conceptos y las cuentas siguen igual.
 *
 * <p>Todos son idempotentes: en la vuelta atrás el monolito vuelve a publicar estos tópicos él mismo y
 * también los recibe. Se prenden con {@code EVENTOS_CONSUMO_SERVICIO=monolito}, como los del calendario.
 */
@Configuration
public class ManejadoresPagos {

    private static final Logger log = LoggerFactory.getLogger(ManejadoresPagos.class);

    @Bean
    ManejadorEvento<CheckoutCreadoData> checkoutEnTurno(TurnoRepository turnoRepository, @org.springframework.beans.factory.annotation.Value("${turnos.externo:false}") boolean turnosExterno) {
        return ManejadorEvento.de("checkout-en-turno", Topicos.PAGOS_CHECKOUT_CREADO_V1, CheckoutCreadoData.class,
                e -> {
                    if (!turnosExterno) {
                        guardarCheckout(turnoRepository, e.data());
                    }
                });
    }

    static void guardarCheckout(TurnoRepository turnoRepository, CheckoutCreadoData d) {
        // Los documentos de la API de conceptos no guardan el link: se pide a /api/payments/checkout.
        if (d.turnoId() == null) {
            return;
        }
        turnoRepository.findById(d.turnoId()).ifPresentOrElse(turno -> {
            if (turno.getEstado() == EstadoTurno.PENDIENTE_PAGO && !d.checkoutUrl().equals(turno.getCheckoutUrl())) {
                turno.setCheckoutUrl(d.checkoutUrl());
                turnoRepository.save(turno);
            }
        }, () -> log.warn("pagos.checkout.creado de un turno que no existe en el monolito: {}", d.turnoId()));
    }

    @Bean
    ManejadorEvento<PagoAprobadoData> turnoPagado(PagoWebhookHandler pagos, @org.springframework.beans.factory.annotation.Value("${turnos.externo:false}") boolean turnosExterno) {
        return ManejadorEvento.de("turno-pagado", Topicos.PAGOS_PAGO_APROBADO_V1, PagoAprobadoData.class,
                e -> {
                    if (!turnosExterno) {
                        pagos.aplicarPagoAprobado(e.data());
                    }
                });
    }

    @Bean
    ManejadorEvento<PagoRechazadoData> turnoPagoRechazado(PagoWebhookHandler pagos, @org.springframework.beans.factory.annotation.Value("${turnos.externo:false}") boolean turnosExterno) {
        return ManejadorEvento.de("turno-pago-rechazado", Topicos.PAGOS_PAGO_RECHAZADO_V1, PagoRechazadoData.class,
                e -> {
                    if (!turnosExterno) {
                        pagos.aplicarPagoRechazado(e.data());
                    }
                });
    }

    @Bean
    ManejadorEvento<ReembolsoRealizadoData> turnoReembolsado(PagoWebhookHandler pagos, @org.springframework.beans.factory.annotation.Value("${turnos.externo:false}") boolean turnosExterno) {
        return ManejadorEvento.de("turno-reembolsado", Topicos.PAGOS_REEMBOLSO_REALIZADO_V1, ReembolsoRealizadoData.class,
                e -> {
                    if (!turnosExterno) {
                        pagos.aplicarReembolsoRealizado(e.data());
                    }
                });
    }

    @Bean
    ManejadorEvento<ConceptoAprobadoData> conceptoPagado(PagoWebhookHandler pagos, @org.springframework.beans.factory.annotation.Value("${clinica.externo:false}") boolean clinicaExterno) {
        return ManejadorEvento.de("concepto-pagado", Topicos.PAGOS_CONCEPTO_APROBADO_V1, ConceptoAprobadoData.class,
                e -> {
                    if (!clinicaExterno) {
                        pagos.aplicarConceptoAprobado(e.data());
                    }
                });
    }

    @Bean
    ManejadorEvento<CuentaMpVinculadaData> cuentaMpVinculada(UsuarioRepository usuarioRepository) {
        return ManejadorEvento.de("cuenta-mp-vinculada", Topicos.PAGOS_CUENTA_MP_VINCULADA_V1, CuentaMpVinculadaData.class,
                e -> usuarioRepository.findById(e.data().profesionalId()).ifPresent(u -> {
                    u.setMpVinculada(true);
                    u.setMpUserId(e.data().mpUserId());
                    usuarioRepository.save(u);
                }));
    }

    @Bean
    ManejadorEvento<CuentaMpDesvinculadaData> cuentaMpDesvinculada(UsuarioRepository usuarioRepository) {
        // Los tokens que quedaron en el monolito son una copia vieja (los vigentes viven en el servicio):
        // se borran para que el profesional no figure como vinculado.
        return ManejadorEvento.de("cuenta-mp-desvinculada", Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1,
                CuentaMpDesvinculadaData.class,
                e -> usuarioRepository.findById(e.data().profesionalId()).ifPresent(u -> {
                    u.setMpVinculada(false);
                    u.setMpUserId(null);
                    u.setMpAccessTokenEncrypted(null);
                    u.setMpRefreshTokenEncrypted(null);
                    u.setMpTokenExpiresAt(null);
                    usuarioRepository.save(u);
                }));
    }
}
