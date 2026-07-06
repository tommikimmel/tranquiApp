package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private MercadoPagoService mercadoPagoService;

    @Test
    void whenCreatePreference_shouldReturnInitPoint() throws Exception {
        ReflectionTestUtils.setField(mercadoPagoService, "isEnabled", false);

        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos")
                .email("carlos@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();

        Turno turno = Turno.builder()
                .id(1L)
                .precio(BigDecimal.valueOf(15000.00))
                .build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("dummy-token");

        String initPoint = mercadoPagoService.crearPreferenciaPago(turno, medico);

        assertNotNull(initPoint);
        assertTrue(initPoint.contains("mercadopago"));
    }

    @Test
    void whenCreatePreferenceDocumento_shouldReturnInitPoint() throws Exception {
        ReflectionTestUtils.setField(mercadoPagoService, "isEnabled", false);

        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos")
                .email("carlos@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();

        com.tranqui.app.model.SolicitudDocumento solicitud = com.tranqui.app.model.SolicitudDocumento.builder()
                .id(1L)
                .medico(medico)
                .tipoConcepto(com.tranqui.app.model.TipoConcepto.CERTIFICADO)
                .precio(BigDecimal.valueOf(15000.00))
                .build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("dummy-token");

        String initPoint = mercadoPagoService.crearPreferenciaDocumento(solicitud);

        assertNotNull(initPoint);
        assertTrue(initPoint.contains("mercadopago"));
    }
}
