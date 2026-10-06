package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.InvoiceRepository;
import com.tranqui.app.repository.SubscriptionPaymentRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionReconciliationSchedulerUnitTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private com.tranqui.app.repository.UsuarioRepository usuarioRepository;

    @InjectMocks
    private SubscriptionReconciliationScheduler scheduler;

    private Usuario profesional;
    private Plan plan;
    private Subscription sub;

    @BeforeEach
    void setUp() {
        profesional = Usuario.builder()
                .id(1L)
                .nombre("Dr. Carlos")
                .email("carlos@example.com")
                .build();

        plan = Plan.builder()
                .name("Consultorio")
                .build();

        sub = Subscription.builder()
                .id(10L)
                .professional(profesional)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .amountArs(new BigDecimal("10000.00"))
                .billingCycle("monthly")
                .currentPeriodEnd(LocalDateTime.now().plusDays(3))
                .build();
    }

    @Test
    void runDailyReconciliation_executesAllChecks() {
        when(subscriptionRepository.findUpcomingRenewalsNeedingNotice(any(), any())).thenReturn(List.of(sub));
        when(subscriptionRepository.findExpiredCancelledSubscriptions(any())).thenReturn(List.of(sub));
        when(paymentRepository.findApprovedPaymentsWithoutInvoiceOlderThan(any())).thenReturn(List.of(
                SubscriptionPayment.builder().id(99L).build()
        ));
        when(invoiceRepository.findFailedInvoices()).thenReturn(List.of(new Invoice()));
        when(subscriptionRepository.findExpiredActiveSubscriptions(any())).thenReturn(List.of(sub));
        when(subscriptionRepository.findExpiringManualSubscriptions(any(), any())).thenReturn(List.of(sub));
        when(paymentRepository.sumApprovedPaymentsSince(any())).thenReturn(new BigDecimal("15000000.00"));
        when(usuarioRepository.findByRol(com.tranqui.app.model.Rol.ADMIN)).thenReturn(List.of(
                Usuario.builder().id(1L).email("admin@tranqui.com").rol(com.tranqui.app.model.Rol.ADMIN).build()));

        scheduler.runDailyReconciliation();

        verify(resendEmailService).enviarPreavisoRenovacionSuscripcion(any(), any(), any(), any(), any(), any());
        verify(resendEmailService, atLeastOnce()).enviarAvisoSuspensionSuscripcion(any(), any(), any());
        verify(invoiceService).generateInvoiceForPayment(99L);
        verify(resendEmailService).enviarAvisoVencimientoManualSuscripcion(any(), any(), any(), any(), anyInt());
        // Facturas FAILED y tope de monotributo superado: alerta por mail al admin
        verify(resendEmailService).enviarAlertaAdmin(eq("admin@tranqui.com"), contains("facturas con error"), anyString());
        verify(resendEmailService).enviarAlertaAdmin(eq("admin@tranqui.com"), contains("monotributo"), anyString());
    }

    @Test
    void errorHandlingInChecks() {
        when(subscriptionRepository.findUpcomingRenewalsNeedingNotice(any(), any())).thenReturn(List.of(sub));
        doThrow(new RuntimeException("Mail error")).when(resendEmailService)
                .enviarPreavisoRenovacionSuscripcion(any(), any(), any(), any(), any(), any());

        scheduler.checkUpcomingRenewals();

        // Check 1 error
        when(paymentRepository.findApprovedPaymentsWithoutInvoiceOlderThan(any())).thenReturn(List.of(
                SubscriptionPayment.builder().id(99L).build()
        ));
        doThrow(new RuntimeException("Invoice error")).when(invoiceService).generateInvoiceForPayment(99L);
        scheduler.checkPaymentsWithoutInvoice();
    }
}
