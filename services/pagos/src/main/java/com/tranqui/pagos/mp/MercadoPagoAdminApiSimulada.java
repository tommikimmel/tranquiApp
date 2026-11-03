package com.tranqui.pagos.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Implementación simulada de {@link MercadoPagoAdminApi} para entornos de desarrollo y tests
 * cuando {@code payment.simulation.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "payment.simulation.enabled", havingValue = "true")
public class MercadoPagoAdminApiSimulada implements MercadoPagoAdminApi {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoAdminApiSimulada.class);

    private final String frontendUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public MercadoPagoAdminApiSimulada(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public PreapprovalMp crearPreapproval(String email, String planName, BigDecimal monto, boolean esAnual, Long subscriptionId) {
        log.info("Mercado Pago Preapproval en modo simulación para sub {}", subscriptionId);
        String mockUrl = (frontendUrl != null ? frontendUrl : "http://localhost:3000") + "/panel?sub_mock=success&sub_id=" + subscriptionId;
        return new PreapprovalMp("MOCK-PRE-" + subscriptionId, mockUrl, "authorized");
    }

    @Override
    public void cancelarPreapproval(String preapprovalId) {
        log.info("Cancelación simulada de preapproval {}", preapprovalId);
    }

    @Override
    public PreapprovalMp obtenerPreapproval(String preapprovalId) {
        log.info("Consulta simulada de preapproval {}", preapprovalId);
        return new PreapprovalMp(preapprovalId, null, "authorized");
    }

    @Override
    public PagoAdminMp obtenerPagoAdmin(Long paymentId) {
        log.info("Consulta simulada de pago admin {}", paymentId);
        return new PagoAdminMp(paymentId, "approved", "sub-" + paymentId, BigDecimal.valueOf(149500));
    }

    @Override
    public JsonNode obtenerDetalleAuthorizedPayment(String authorizedPaymentId) {
        log.info("Consulta simulada de authorized payment {}", authorizedPaymentId);
        try {
            return mapper.readTree("""
                    {
                      "id": "%s",
                      "status": "approved",
                      "preapproval_id": "MOCK-PRE-1",
                      "transaction_amount": 149500.00,
                      "payment": {
                        "id": "%s"
                      }
                    }
                    """.formatted(authorizedPaymentId, authorizedPaymentId));
        } catch (Exception e) {
            return null;
        }
    }
}
