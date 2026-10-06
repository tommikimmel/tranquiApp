package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceUnitTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private SubscriptionEventRepository eventRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanFeatureRepository planFeatureRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private com.tranqui.app.service.arca.ArcaConfig arcaConfig;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Usuario profesional;
    private Plan plan;
    private Subscription sub;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "frontendUrl", "http://localhost:5173");

        profesional = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .apellido("Gomez")
                .email("carlos@example.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        plan = Plan.builder()
                .id(10L)
                .name("Consultorio")
                .code("consultorio")
                .priceArs(new BigDecimal("10000.00"))
                .priceArsAnual(new BigDecimal("100000.00"))
                .build();

        sub = Subscription.builder()
                .id(100L)
                .professional(profesional)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .billingSource(BillingSource.MERCADOPAGO)
                .billingCycle("monthly")
                .amountArs(new BigDecimal("10000.00"))
                .currentPeriodStart(LocalDateTime.now().minusDays(10))
                .currentPeriodEnd(LocalDateTime.now().plusDays(20))
                .mpPreapprovalId("PRE-123")
                .build();

        lenient().when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(100L);
            }
            return s;
        });
    }

    @Test
    void iniciarCheckout_success_monthlyAndAnnual() throws Exception {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        com.mercadopago.resources.preapproval.Preapproval preapproval = mock(com.mercadopago.resources.preapproval.Preapproval.class);
        when(preapproval.getId()).thenReturn("NEW-PRE-99");
        when(mercadoPagoService.crearSuscripcionPreapproval(eq(profesional), eq(plan), eq(100L), anyString()))
                .thenReturn(preapproval);
        when(mercadoPagoService.checkoutUrlFor(preapproval)).thenReturn("http://mp.checkout/url");

        String url = subscriptionService.iniciarCheckout(1L, 10L, "monthly");
        assertEquals("http://mp.checkout/url", url);
        verify(mercadoPagoService).cancelarSuscripcionPreapproval("PRE-123");

        // Annual
        String urlAnnual = subscriptionService.iniciarCheckout(1L, 10L, "annual");
        assertEquals("http://mp.checkout/url", urlAnnual);
    }

    @Test
    void iniciarCheckout_errors() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> subscriptionService.iniciarCheckout(1L, 10L, "monthly"));

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(planRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> subscriptionService.iniciarCheckout(1L, 10L, "monthly"));

        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> subscriptionService.iniciarCheckout(1L, 10L, "monthly"));

        plan.setPriceArsAnual(null);
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        assertThrows(IllegalStateException.class, () -> subscriptionService.iniciarCheckout(1L, 10L, "annual"));
    }

    @Test
    void hasFeature_checks() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        when(planFeatureRepository.existsByPlanIdAndFeatureKey(10L, "RECETAS")).thenReturn(true);
        when(planFeatureRepository.existsByPlanIdAndFeatureKey(10L, "WHATSAPP")).thenReturn(false);

        assertTrue(subscriptionService.hasFeature(1L, "RECETAS"));
        assertFalse(subscriptionService.hasFeature(1L, "WHATSAPP"));

        // Admin has all features
        Usuario admin = Usuario.builder().id(2L).rol(Rol.ADMIN).build();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(admin));
        assertTrue(subscriptionService.hasFeature(2L, "ANY"));

        // Inactive sub
        sub.setStatus(SubscriptionStatus.SUSPENDED);
        assertFalse(subscriptionService.hasFeature(1L, "RECETAS"));
    }

    @Test
    void isAccessAllowed_checks() {
        // null or not found
        assertFalse(subscriptionService.isAccessAllowed(null));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
        assertFalse(subscriptionService.isAccessAllowed(99L));

        // Admin and Paciente always allowed
        Usuario admin = Usuario.builder().id(2L).rol(Rol.ADMIN).build();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(admin));
        assertTrue(subscriptionService.isAccessAllowed(2L));

        Usuario paciente = Usuario.builder().id(3L).rol(Rol.PACIENTE).build();
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(paciente));
        assertTrue(subscriptionService.isAccessAllowed(3L));

        // Profesional with ACTIVE
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        assertTrue(subscriptionService.isAccessAllowed(1L));

        // Cancelled with currentPeriodEnd in the future -> allowed
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCurrentPeriodEnd(LocalDateTime.now().plusDays(5));
        assertTrue(subscriptionService.isAccessAllowed(1L));

        // Cancelled with currentPeriodEnd in the past -> blocked
        sub.setCurrentPeriodEnd(LocalDateTime.now().minusDays(1));
        assertFalse(subscriptionService.isAccessAllowed(1L));

        // Past due -> blocked
        sub.setStatus(SubscriptionStatus.PAST_DUE);
        assertFalse(subscriptionService.isAccessAllowed(1L));

        // Suspended -> blocked
        sub.setStatus(SubscriptionStatus.SUSPENDED);
        assertFalse(subscriptionService.isAccessAllowed(1L));
    }

    @Test
    void validarAccesoRecetas_tests() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        when(planFeatureRepository.existsByPlanIdAndFeatureKey(10L, "recetas_electronicas")).thenReturn(true);

        assertDoesNotThrow(() -> subscriptionService.validarAccesoRecetas(profesional));

        when(planFeatureRepository.existsByPlanIdAndFeatureKey(10L, "recetas_electronicas")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> subscriptionService.validarAccesoRecetas(profesional));
    }

    @Test
    void registerManualPayment_success() {
        profesional.setCuit("20-12345678-9");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(profesional));
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        when(paymentRepository.save(any(SubscriptionPayment.class))).thenAnswer(i -> {
            SubscriptionPayment p = i.getArgument(0);
            p.setId(500L);
            return p;
        });

        SubscriptionPayment payment = subscriptionService.registerManualPayment(
                999L, 1L, 10L, new BigDecimal("10000.00"), "MANUAL_TRANSFER",
                LocalDateTime.now(), LocalDateTime.now().plusMonths(1),
                "REF-123", "Notas", true, "IDEM-123"
        );

        assertNotNull(payment);
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        verify(invoiceService).generateInvoiceForPayment(500L);
        verify(resendEmailService).enviarConfirmacionPagoSuscripcion(
                eq(profesional.getEmail()), eq(profesional.getNombre()), eq(plan.getName()),
                any(), any(), any(), eq("REF-123")
        );
    }

    @Test
    void processMercadoPagoPaymentWebhook_approved() {
        when(subscriptionRepository.findByMpPreapprovalId("PRE-123")).thenReturn(Optional.of(sub));
        when(paymentRepository.save(any(SubscriptionPayment.class))).thenAnswer(i -> {
            SubscriptionPayment p = i.getArgument(0);
            p.setId(501L);
            return p;
        });

        SubscriptionPayment p = subscriptionService.processMercadoPagoPaymentWebhook(
                "PAY-999", "PRE-123", new BigDecimal("10000.00"), "approved"
        );

        assertNotNull(p);
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        verify(invoiceService).generateInvoiceForPayment(501L);
        verify(resendEmailService).enviarConfirmacionPagoSuscripcion(
                eq(profesional.getEmail()), eq(profesional.getNombre()), anyString(),
                any(), any(), any(), anyString()
        );
    }

    @Test
    void processMercadoPagoPaymentWebhook_rejected() {
        when(subscriptionRepository.findByMpPreapprovalId("PRE-123")).thenReturn(Optional.of(sub));
        when(paymentRepository.save(any(SubscriptionPayment.class))).thenAnswer(i -> i.getArgument(0));

        SubscriptionPayment p = subscriptionService.processMercadoPagoPaymentWebhook(
                "PAY-888", "PRE-123", new BigDecimal("10000.00"), "rejected"
        );

        assertNotNull(p);
        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());
        verify(resendEmailService).enviarAvisoPagoFallidoSuscripcion(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void processMercadoPagoPreapprovalWebhook_cancelledAndAuthorized() {
        when(subscriptionRepository.findByMpPreapprovalId("PRE-123")).thenReturn(Optional.of(sub));

        // cancelled
        subscriptionService.processMercadoPagoPreapprovalWebhook("PRE-123", "cancelled");
        assertTrue(sub.getCancelAtPeriodEnd());
        verify(resendEmailService).enviarConfirmacionCancelacionSuscripcion(anyString(), anyString(), anyString(), any());

        // authorized
        sub.setStatus(SubscriptionStatus.SUBSCRIPTION_PENDING);
        subscriptionService.processMercadoPagoPreapprovalWebhook("PRE-123", "authorized");
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    @Test
    void cancelarSuscripcionPorProfesional_success() throws Exception {
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));

        Subscription cancelled = subscriptionService.cancelarSuscripcionPorProfesional(1L);
        assertTrue(cancelled.getCancelAtPeriodEnd());
        assertNotNull(cancelled.getCancelledAt());
        verify(mercadoPagoService).cancelarSuscripcionPreapproval("PRE-123");
        verify(resendEmailService).enviarConfirmacionCancelacionSuscripcion(anyString(), anyString(), anyString(), any());
    }

    @Test
    void cancelarSuscripcionPorProfesional_siMercadoPagoFalla_noMarcaCancelada() throws Exception {
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        doThrow(new RuntimeException("MP caído")).when(mercadoPagoService).cancelarSuscripcionPreapproval("PRE-123");

        assertThrows(IllegalStateException.class, () -> subscriptionService.cancelarSuscripcionPorProfesional(1L));
        assertNotEquals(Boolean.TRUE, sub.getCancelAtPeriodEnd());
        verify(subscriptionRepository, never()).save(sub);
        verify(resendEmailService, never()).enviarConfirmacionCancelacionSuscripcion(anyString(), anyString(), anyString(), any());
    }

    @Test
    void cancelarSuscripcionPorProfesional_esIdempotente() throws Exception {
        sub.setCancelAtPeriodEnd(true);
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));

        subscriptionService.cancelarSuscripcionPorProfesional(1L);
        verify(mercadoPagoService, never()).cancelarSuscripcionPreapproval(anyString());
    }

    @Test
    void statsAndQueries() {
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.of(sub));
        assertTrue(subscriptionService.getSubscriptionForProfessional(1L).isPresent());

        when(subscriptionRepository.findAll()).thenReturn(List.of(sub));
        assertEquals(1, subscriptionService.getAllSubscriptions().size());

        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(Collections.emptyList());
        when(paymentRepository.sumApprovedPaymentsSince(any())).thenReturn(BigDecimal.ZERO);

        Map<String, Object> stats = subscriptionService.getAdminOverviewStats();
        assertNotNull(stats);
        assertEquals(1L, stats.get("suscriptoresActivos"));
    }

    @Test
    void migrateExistingVerifiedProfessionalsOnStartup_tests() {
        when(planRepository.findByCode("consultorio")).thenReturn(Optional.of(plan));
        profesional.setVerificadoAdmin(true);
        profesional.setProfession("psicologo");
        when(usuarioRepository.findByRol(Rol.PSIQUIATRA)).thenReturn(List.of(profesional));
        when(subscriptionRepository.findByProfessionalId(1L)).thenReturn(Optional.empty());

        subscriptionService.migrateExistingVerifiedProfessionalsOnStartup();
        verify(subscriptionRepository).save(any(Subscription.class));
    }
}
