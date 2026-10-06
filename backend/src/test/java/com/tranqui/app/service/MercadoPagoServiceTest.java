package com.tranqui.app.service;

import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preapproval.Preapproval;
import com.mercadopago.resources.preference.Preference;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private MercadoPagoOAuthService oauthService;

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

        // isEnabled=false short-circuits before ever decrypting the token, so no stub needed here.
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

        // isEnabled=false short-circuits before ever decrypting the token, so no stub needed here.
        String initPoint = mercadoPagoService.crearPreferenciaDocumento(solicitud);

        assertNotNull(initPoint);
        assertTrue(initPoint.contains("mercadopago"));
    }

    @Test
    void whenEnabledAndMedicoNotLinked_crearPreferenciaPago_shouldThrow() throws Exception {
        ReflectionTestUtils.setField(mercadoPagoService, "isEnabled", true);

        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();
        Turno turno = Turno.builder().id(1L).precio(BigDecimal.valueOf(15000.00)).build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> mercadoPagoService.crearPreferenciaPago(turno, medico));
    }

    @Test
    void whenEnabledAndMedicoLinked_crearPreferenciaPago_shouldUsePerRequestTokenAndReturnInitPoint() throws Exception {
        ReflectionTestUtils.setField(mercadoPagoService, "isEnabled", true);
        ReflectionTestUtils.setField(mercadoPagoService, "appPublicUrl", "https://backend.example.com");

        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();
        Turno turno = Turno.builder().id(1L).precio(BigDecimal.valueOf(15000.00)).build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("real-access-token");

        PreferenceClient fakeClient = mock(PreferenceClient.class);
        Preference fakePreference = mock(Preference.class);
        when(fakePreference.getInitPoint()).thenReturn("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-pref");
        when(fakeClient.create(any(PreferenceRequest.class), any(MPRequestOptions.class))).thenReturn(fakePreference);

        MercadoPagoService spyService = new MercadoPagoService() {
            @Override
            protected PreferenceClient buildPreferenceClient() {
                return fakeClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(spyService, "oauthService", oauthService);
        ReflectionTestUtils.setField(spyService, "isEnabled", true);
        ReflectionTestUtils.setField(spyService, "appPublicUrl", "https://backend.example.com");

        String initPoint = spyService.crearPreferenciaPago(turno, medico);

        assertEquals("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-pref", initPoint);
    }

    @Test
    void whenEnabledAndMedicoNotLinked_crearPreferenciaDocumento_shouldThrow() throws Exception {
        ReflectionTestUtils.setField(mercadoPagoService, "isEnabled", true);

        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();
        com.tranqui.app.model.SolicitudDocumento solicitud = com.tranqui.app.model.SolicitudDocumento.builder()
                .id(1L).medico(medico).tipoConcepto(com.tranqui.app.model.TipoConcepto.CERTIFICADO)
                .precio(BigDecimal.valueOf(15000.00))
                .build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> mercadoPagoService.crearPreferenciaDocumento(solicitud));
    }

    @Test
    void whenEnabledAndMedicoLinked_crearPreferenciaDocumento_shouldReturnInitPoint() throws Exception {
        Usuario medico = Usuario.builder()
                .nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA)
                .mpAccessTokenEncrypted("encrypted-token")
                .build();
        com.tranqui.app.model.SolicitudDocumento solicitud = com.tranqui.app.model.SolicitudDocumento.builder()
                .id(1L).medico(medico).tipoConcepto(com.tranqui.app.model.TipoConcepto.CERTIFICADO)
                .precio(BigDecimal.valueOf(15000.00))
                .build();

        when(encryptionUtil.decrypt("encrypted-token")).thenReturn("real-looking-token");
        when(oauthService.obtenerAccessTokenValido(medico)).thenReturn("real-access-token");

        PreferenceClient fakeClient = mock(PreferenceClient.class);
        Preference fakePreference = mock(Preference.class);
        when(fakePreference.getInitPoint()).thenReturn("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-doc-pref");
        when(fakeClient.create(any(PreferenceRequest.class), any(MPRequestOptions.class))).thenReturn(fakePreference);

        MercadoPagoService spyService = new MercadoPagoService() {
            @Override
            protected PreferenceClient buildPreferenceClient() {
                return fakeClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(spyService, "oauthService", oauthService);
        ReflectionTestUtils.setField(spyService, "isEnabled", true);
        ReflectionTestUtils.setField(spyService, "appPublicUrl", "https://backend.example.com");

        String initPoint = spyService.crearPreferenciaDocumento(solicitud);

        assertEquals("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=real-doc-pref", initPoint);
    }

    // ── crearSuscripcionPreapproval / checkoutUrlFor (paywall Etapa 2, §9) ──────────

    @Test
    void whenAdminAccessTokenNotConfigured_crearSuscripcionPreapproval_shouldThrow() {
        ReflectionTestUtils.setField(mercadoPagoService, "adminAccessToken", "");

        Usuario profesional = Usuario.builder().nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA).build();
        Plan plan = Plan.builder().id(1L).code("CLINICO").name("Clínico").priceArs(BigDecimal.valueOf(20000)).build();

        assertThrows(IllegalStateException.class,
                () -> mercadoPagoService.crearSuscripcionPreapproval(profesional, plan, 5L));
    }

    @Test
    void whenAdminAccessTokenConfigured_crearSuscripcionPreapproval_shouldUsePreapprovalClientWithExpectedRequest() throws Exception {
        Usuario profesional = Usuario.builder().nombre("Dr. Carlos").email("carlos@gmail.com").rol(Rol.PSIQUIATRA).build();
        Plan plan = Plan.builder().id(1L).code("CLINICO").name("Clínico").priceArs(BigDecimal.valueOf(20000)).build();

        PreapprovalClient fakeClient = mock(PreapprovalClient.class);
        Preapproval fakePreapproval = mock(Preapproval.class);
        when(fakePreapproval.getId()).thenReturn("preap-123");
        when(fakeClient.create(any(PreapprovalCreateRequest.class), any(MPRequestOptions.class))).thenReturn(fakePreapproval);

        MercadoPagoService spyService = new MercadoPagoService() {
            @Override
            protected PreapprovalClient buildPreapprovalClient() {
                return fakeClient;
            }
        };
        ReflectionTestUtils.setField(spyService, "adminAccessToken", "admin-token-real");
        ReflectionTestUtils.setField(spyService, "frontendUrl", "https://app.example.com");

        Preapproval result = spyService.crearSuscripcionPreapproval(profesional, plan, 5L);

        assertEquals("preap-123", result.getId());
        ArgumentCaptor<PreapprovalCreateRequest> captor = ArgumentCaptor.forClass(PreapprovalCreateRequest.class);
        verify(fakeClient).create(captor.capture(), any(MPRequestOptions.class));
        assertEquals("sub-5", captor.getValue().getExternalReference());
        assertEquals(profesional.getEmail(), captor.getValue().getPayerEmail());
    }

    @Test
    void checkoutUrlFor_returnsRealInitPoint() {
        Preapproval preapproval = mock(Preapproval.class);
        when(preapproval.getInitPoint()).thenReturn("https://www.mercadopago.com.ar/init");

        String url = mercadoPagoService.checkoutUrlFor(preapproval);

        assertEquals("https://www.mercadopago.com.ar/init", url);
    }
}
