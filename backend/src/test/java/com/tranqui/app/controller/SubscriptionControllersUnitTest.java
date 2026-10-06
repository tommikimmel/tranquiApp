package com.tranqui.app.controller;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.*;
import com.tranqui.app.repository.SubscriptionEventRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllersUnitTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PlanService planService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionEventRepository subscriptionEventRepository;

    @Mock
    private SubscriptionReconciliationScheduler reconciliationScheduler;

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @InjectMocks
    private AdminSubscriptionController adminSubscriptionController;

    private Usuario profesional;
    private Plan plan;
    private Subscription sub;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        profesional = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .apellido("Gomez")
                .email("carlos@example.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        plan = Plan.builder()
                .id(10L)
                .code("consultorio")
                .name("Consultorio")
                .priceArs(new BigDecimal("10000.00"))
                .priceArsAnual(new BigDecimal("100000.00"))
                .isActive(true)
                .build();

        sub = Subscription.builder()
                .id(100L)
                .professional(profesional)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .amountArs(new BigDecimal("10000.00"))
                .billingCycle("monthly")
                .billingSource(BillingSource.MERCADOPAGO)
                .currentPeriodEnd(LocalDateTime.now().plusDays(20))
                .mpPreapprovalId("PRE-123")
                .build();

        invoice = Invoice.builder()
                .id(200L)
                .cbteTipo(11)
                .puntoVenta(1)
                .cbteNumero(12345L)
                .pdfBase64(Base64.getEncoder().encodeToString("PDF_DUMMY_BYTES".getBytes()))
                .status(InvoiceStatus.ISSUED)
                .build();
    }

    // --- SubscriptionController tests ---

    @Test
    void getActivePlans_returnsPlans() {
        when(planService.getAllActivePlans()).thenReturn(List.of(plan));
        when(planService.getFeatureKeysForPlan(10L)).thenReturn(List.of("RECETAS"));

        ResponseEntity<List<PlanResponseDto>> response = subscriptionController.getActivePlans();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("consultorio", response.getBody().get(0).getCode());
    }

    @Test
    void getMySubscription_foundAndNull() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(profesional));
        when(subscriptionService.getSubscriptionForProfessional(1L)).thenReturn(Optional.of(sub));
        when(planService.getFeatureKeysForPlan(10L)).thenReturn(List.of("RECETAS"));

        ResponseEntity<SubscriptionResponseDto> response = subscriptionController.getMySubscription(userDetails);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // sub null
        when(subscriptionService.getSubscriptionForProfessional(1L)).thenReturn(Optional.empty());
        ResponseEntity<SubscriptionResponseDto> nullResponse = subscriptionController.getMySubscription(userDetails);
        assertNull(nullResponse.getBody());
    }

    @Test
    void iniciarCheckout_successAndErrors() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(profesional));
        when(subscriptionService.iniciarCheckout(1L, 10L, "monthly")).thenReturn("http://mp.url");

        CheckoutRequestDto dto = CheckoutRequestDto.builder().planId(10L).billingCycle("monthly").build();
        ResponseEntity<?> resp = subscriptionController.iniciarCheckout(userDetails, dto);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        // missing planId
        dto.setPlanId(null);
        ResponseEntity<?> badResp = subscriptionController.iniciarCheckout(userDetails, dto);
        assertEquals(HttpStatus.BAD_REQUEST, badResp.getStatusCode());

        // IllegalStateException
        dto.setPlanId(10L);
        when(subscriptionService.iniciarCheckout(1L, 10L, "monthly")).thenThrow(new IllegalStateException("Sin precio"));
        ResponseEntity<?> errorResp = subscriptionController.iniciarCheckout(userDetails, dto);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, errorResp.getStatusCode());
    }

    @Test
    void cancelarMiSuscripcion_successAndError() {
        when(userDetails.getUsername()).thenReturn("carlos@example.com");
        when(usuarioRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(profesional));
        when(subscriptionService.cancelarSuscripcionPorProfesional(1L)).thenReturn(sub);

        ResponseEntity<?> resp = subscriptionController.cancelarMiSuscripcion(userDetails);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        when(subscriptionService.cancelarSuscripcionPorProfesional(1L)).thenThrow(new RuntimeException("Error MP"));
        ResponseEntity<?> errResp = subscriptionController.cancelarMiSuscripcion(userDetails);
        assertEquals(HttpStatus.BAD_REQUEST, errResp.getStatusCode());
    }

    // --- AdminSubscriptionController tests ---

    @Test
    void adminOverviewAndList() {
        when(subscriptionService.getAdminOverviewStats()).thenReturn(Map.of("total", 1L));
        ResponseEntity<Map<String, Object>> ov = adminSubscriptionController.getOverview();
        assertEquals(HttpStatus.OK, ov.getStatusCode());

        when(subscriptionService.getAllSubscriptions()).thenReturn(List.of(sub));
        when(planService.getFeatureKeysForPlan(10L)).thenReturn(List.of("RECETAS"));
        ResponseEntity<List<SubscriptionResponseDto>> list = adminSubscriptionController.getAllSubscriptions();
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(1, list.getBody().size());
    }

    @Test
    void adminManualPaymentAndStatusChange() throws Exception {
        when(userDetails.getUsername()).thenReturn("admin@tranqui.com");
        when(usuarioRepository.findByEmail("admin@tranqui.com")).thenReturn(Optional.of(profesional));
        SubscriptionPayment payment = SubscriptionPayment.builder().id(999L).build();
        when(subscriptionService.registerManualPayment(
                eq(1L), eq(100L), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(payment);

        ManualPaymentRequestDto mDto = ManualPaymentRequestDto.builder()
                .professionalId(100L)
                .amountArs(new BigDecimal("10000.00"))
                .receiptReference("TRANSF-1")
                .emitInvoice(true)
                .idempotencyKey("IDEM-1")
                .build();

        ResponseEntity<?> resp = adminSubscriptionController.registerManualPayment(userDetails, mDto);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        // Change status
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));
        ResponseEntity<?> statusResp = adminSubscriptionController.updateSubscriptionStatus(
                100L, userDetails, Map.of("status", "CANCELLED")
        );
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        verify(mercadoPagoService).cancelarSuscripcionPreapproval("PRE-123");
    }

    @Test
    void adminInvoicesAndCreditNotes() {
        when(invoiceService.getAllInvoices()).thenReturn(List.of(invoice));
        ResponseEntity<?> invList = adminSubscriptionController.getAllInvoices();
        assertEquals(HttpStatus.OK, invList.getStatusCode());

        // credit note
        when(userDetails.getUsername()).thenReturn("admin@tranqui.com");
        when(usuarioRepository.findByEmail("admin@tranqui.com")).thenReturn(Optional.of(profesional));
        when(invoiceService.emitirNotaDeCredito(eq(200L), any(), eq(1L))).thenReturn(invoice);
        CreditNoteRequestDto cnDto = CreditNoteRequestDto.builder().reason("Error").build();
        ResponseEntity<?> cnResp = adminSubscriptionController.emitirNotaDeCredito(200L, userDetails, cnDto);
        assertEquals(HttpStatus.OK, cnResp.getStatusCode());

        // reconciliation trigger
        ResponseEntity<?> recResp = adminSubscriptionController.triggerReconciliation();
        assertEquals(HttpStatus.OK, recResp.getStatusCode());
        verify(reconciliationScheduler).runDailyReconciliation();
    }

    @Test
    void adminPlansEventsAndStatusBranches() {
        // Plans
        when(planService.getAllPlans()).thenReturn(List.of(plan));
        when(planService.getFeatureKeysForPlan(10L)).thenReturn(List.of("RECETAS"));
        ResponseEntity<List<PlanResponseDto>> plansResp = adminSubscriptionController.getAllPlans();
        assertEquals(HttpStatus.OK, plansResp.getStatusCode());
        assertEquals(1, plansResp.getBody().size());

        when(planService.updatePlan(eq(10L), any(), any(), any(), any(), any(), any())).thenReturn(plan);
        Map<String, Object> updatePlanBody = Map.of(
                "priceArs", "12000.00",
                "priceUsdRef", "12.00",
                "priceArsAnual", "120000.00",
                "name", "Consultorio Plus",
                "description", "Desc",
                "isActive", true
        );
        ResponseEntity<?> updatedPlanResp = adminSubscriptionController.updatePlan(10L, updatePlanBody);
        assertEquals(HttpStatus.OK, updatedPlanResp.getStatusCode());

        // Events
        SubscriptionEvent evt = SubscriptionEvent.builder().id(1L).subscriptionId(100L).build();
        when(subscriptionEventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(evt));
        when(subscriptionEventRepository.findBySubscriptionIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(evt));
        assertEquals(1, adminSubscriptionController.getAllSubscriptionEvents().getBody().size());
        assertEquals(1, adminSubscriptionController.getSubscriptionEvents(100L).getBody().size());

        // Update status: bad request
        assertEquals(HttpStatus.BAD_REQUEST, adminSubscriptionController.updateSubscriptionStatus(100L, userDetails, Map.of()).getStatusCode());

        // Update status: not found
        when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () ->
                adminSubscriptionController.updateSubscriptionStatus(999L, userDetails, Map.of("status", "ACTIVE")));

        // Update status: SUSPENDED
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));
        ResponseEntity<?> suspendedResp = adminSubscriptionController.updateSubscriptionStatus(
                100L, userDetails, Map.of("status", "SUSPENDED")
        );
        assertEquals(HttpStatus.OK, suspendedResp.getStatusCode());
        verify(resendEmailService).enviarAvisoSuspensionSuscripcion(anyString(), anyString(), anyString());
    }
}
