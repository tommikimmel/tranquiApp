package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase 10, T2, lado productor de /internal/monolito/pagos/*: cada DTO, serializado con el ObjectMapper de la
 * aplicación (con su configuración de fechas), tiene la forma del contrato que lee el servicio de pagos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "site.access.password=test-pass-123",
        "app.public-url=https://tranqui.test"
})
class InternoPagosContratoTest {

    @Autowired private ObjectMapper mapper;

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 3, 10, 30);

    @Test
    void cadaDtoCumpleSuContrato() throws Exception {
        Contratos.mismaForma("internas/monolito/pagos-cuentas.json", mapper.writeValueAsString(List.of(
                new InternoPagosController.CuentaMpDto(7L, "acceso-cifrado", "refresco-cifrado", FECHA, "mp-user-77"))));
        Contratos.mismaForma("internas/monolito/pagos-pagos.json", mapper.writeValueAsString(List.of(
                new InternoPagosController.PagoDto(3L, 11L, 12L, 7L, 8L, "MP-123", "APROBADO", new BigDecimal("45000.00"), FECHA))));
        Contratos.mismaForma("internas/monolito/pagos-solicitudes.json", mapper.writeValueAsString(List.of(
                new InternoPagosController.SolicitudDto(12L, 7L, 8L, "CERTIFICADO", new BigDecimal("15000.00"), "PAGADO", "MP-456",
                        "Certificado médico"))));
        Contratos.mismaForma("internas/monolito/pagos-cobros-pendientes.json", mapper.writeValueAsString(List.of(
                new InternoPagosController.CobroPendienteDto(11L, 7L, 8L, new BigDecimal("45000.00"), "Consulta con Dra. Paz",
                        "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=1", FECHA))));
    }
}
