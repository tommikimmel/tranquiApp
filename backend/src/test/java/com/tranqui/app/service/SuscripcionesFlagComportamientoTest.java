package com.tranqui.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.resources.payment.Payment;
import com.tranqui.app.controller.AuthController;
import com.tranqui.app.controller.InternoPagosController;
import com.tranqui.app.controller.WebhookController;
import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RegisterRequestDto;
import com.tranqui.app.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuscripcionesFlagComportamientoTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPaymentRepository subscriptionPaymentRepository;
    @Mock private PlanRepository planRepository;
    @Mock private FeatureRepository featureRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OutboxPublisher outboxPublisher;
    @Mock private MercadoPagoService mercadoPagoService;
    @Mock private WebhookController webhookController;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private ResendEmailService resendEmailService;
    @Mock private AccountService accountService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("SubscriptionReconciliationScheduler con flag=true no ejecuta reconciliaciones ni alertas")
    void scheduler_conFlagTrue_noEjecuta() {
        SubscriptionReconciliationScheduler scheduler = new SubscriptionReconciliationScheduler();
        ReflectionTestUtils.setField(scheduler, "subscriptionRepository", subscriptionRepository);
        ReflectionTestUtils.setField(scheduler, "paymentRepository", subscriptionPaymentRepository);
        ReflectionTestUtils.setField(scheduler, "resendEmailService", resendEmailService);
        ReflectionTestUtils.setField(scheduler, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(scheduler, "suscripcionesExterno", true);

        scheduler.runDailyReconciliation();
        scheduler.runHourlyAlertsCheck();

        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(resendEmailService);
    }

    @Test
    @DisplayName("PlanService.initDefaultPlansAndFeatures con flag=true no inicializa planes en la base")
    void planService_conFlagTrue_noInicializa() {
        PlanService planService = new PlanService();
        ReflectionTestUtils.setField(planService, "planRepository", planRepository);
        ReflectionTestUtils.setField(planService, "featureRepository", featureRepository);
        ReflectionTestUtils.setField(planService, "planFeatureRepository", planFeatureRepository);
        ReflectionTestUtils.setField(planService, "suscripcionesExterno", true);

        planService.initDefaultPlansAndFeatures();

        verifyNoInteractions(planRepository);
        verifyNoInteractions(featureRepository);
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    @DisplayName("SubscriptionService.migrateExistingVerifiedProfessionalsOnStartup con flag=true no migra profesionales")
    void subscriptionService_conFlagTrue_noMigra() {
        SubscriptionService service = new SubscriptionService();
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(service, "subscriptionRepository", subscriptionRepository);
        ReflectionTestUtils.setField(service, "suscripcionesExterno", true);

        service.migrateExistingVerifiedProfessionalsOnStartup();

        verifyNoInteractions(usuarioRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    @DisplayName("InternoPagosController.notificacionSuscripcion con flag=true responde 409 y no procesa")
    void internoPagosController_conFlagTrue_responde409() {
        InternoPagosController controller = new InternoPagosController(
                usuarioRepository, mock(PagoRepository.class), mock(TurnoRepository.class),
                mock(SolicitudDocumentoRepository.class), webhookController, true
        );

        ResponseEntity<Void> res = controller.notificacionSuscripcion("123", "{}");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(webhookController);
    }

    @Test
    @DisplayName("WebhookController procesarNotificacion con flag=true lanza 409 en preapproval, authorized_payment y sub-")
    void webhookController_conFlagTrue_lanza409() throws Exception {
        WebhookController controller = new WebhookController();
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(controller, "suscripcionesExterno", true);
        ReflectionTestUtils.setField(controller, "mercadoPagoService", mercadoPagoService);

        // subscription_preapproval
        String bodyPreapproval = "{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"preapp-123\"}}";
        assertThatThrownBy(() -> controller.procesarNotificacion(bodyPreapproval, "preapp-123", false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // subscription_authorized_payment
        String bodyAuthPayment = "{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\"pay-123\"}}";
        assertThatThrownBy(() -> controller.procesarNotificacion(bodyAuthPayment, "pay-123", false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // payment con external_reference "sub-"
        Payment pagoMock = mock(Payment.class);
        when(pagoMock.getExternalReference()).thenReturn("sub-999");
        when(mercadoPagoService.obtenerPagoAdmin(9999L)).thenReturn(pagoMock);

        String bodyPayment = "{\"type\":\"payment\",\"data\":{\"id\":\"9999\"}}";
        assertThatThrownBy(() -> controller.procesarNotificacion(bodyPayment, "9999", false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("AuthController.register con profesional y flag=true no crea fila en subscriptions")
    void authController_conFlagTrue_noCreaSuscripcion() {
        ReflectionTestUtils.setField(authController, "suscripcionesExterno", true);

        when(usuarioRepository.findByEmail("doc@test.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-pass");

        Usuario savedUser = Usuario.builder()
                .id(50L)
                .email("doc@test.com")
                .nombre("Doc")
                .apellido("Test")
                .rol(Rol.PSIQUIATRA)
                .profession("psiquiatra")
                .version(1)
                .build();
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(savedUser);

        RegisterRequestDto req = new RegisterRequestDto();
        req.setEmail("doc@test.com");
        req.setPassword("Secret123!");
        req.setNombre("Doc");
        req.setApellido("Test");
        req.setRol(Rol.PSIQUIATRA);
        req.setProfession("psiquiatra");
        req.setAceptaTerminos(true);

        ResponseEntity<?> res = authController.register(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(usuarioRepository).save(any(Usuario.class));
        verify(outboxPublisher).publicar(eq("identidad.usuario.registrado.v1"), eq("50"), eq(2), any());
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(planRepository);
    }
}
