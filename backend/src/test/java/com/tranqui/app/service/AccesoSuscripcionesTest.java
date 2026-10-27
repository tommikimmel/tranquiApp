package com.tranqui.app.service;

import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.SubscriptionStatus;
import com.tranqui.app.model.SuscripcionAcceso;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.SuscripcionAccesoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoSuscripcionesTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SuscripcionAccesoRepository suscripcionAccesoRepository;

    @Nested
    @DisplayName("Con suscripciones.externo = false (monolito dueño)")
    class ConFlagDesactivado {

        private AccesoSuscripciones build() {
            return new AccesoSuscripciones(false, subscriptionService, subscriptionRepository, suscripcionAccesoRepository);
        }

        @Test
        void acceso_delegaEnSubscriptionService() {
            AccesoSuscripciones accesos = build();
            when(subscriptionService.isAccessAllowed(42L)).thenReturn(true);

            assertThat(accesos.acceso(42L)).isTrue();
            verify(subscriptionService).isAccessAllowed(42L);
            verifyNoInteractions(suscripcionAccesoRepository);
        }

        @Test
        void puedeRecetar_delegaEnSubscriptionServiceHasFeature() {
            AccesoSuscripciones accesos = build();
            when(subscriptionService.hasFeature(42L, "recetas_electronicas")).thenReturn(true);

            assertThat(accesos.puedeRecetar(42L)).isTrue();
            verify(subscriptionService).hasFeature(42L, "recetas_electronicas");
            verifyNoInteractions(suscripcionAccesoRepository);
        }

        @Test
        void estado_delegaEnSubscriptionRepository() {
            AccesoSuscripciones accesos = build();
            Subscription sub = Subscription.builder().status(SubscriptionStatus.ACTIVE).build();
            when(subscriptionRepository.findByProfessionalId(42L)).thenReturn(Optional.of(sub));

            assertThat(accesos.estado(42L)).isEqualTo("ACTIVE");
            verify(subscriptionRepository).findByProfessionalId(42L);
            verifyNoInteractions(suscripcionAccesoRepository);
        }

        @Test
        void periodoHasta_delegaEnSubscriptionRepository() {
            AccesoSuscripciones accesos = build();
            LocalDateTime end = LocalDateTime.of(2026, 12, 31, 23, 59);
            Subscription sub = Subscription.builder().currentPeriodEnd(end).build();
            when(subscriptionRepository.findByProfessionalId(42L)).thenReturn(Optional.of(sub));

            assertThat(accesos.periodoHasta(42L)).isEqualTo(end);
            verify(subscriptionRepository).findByProfessionalId(42L);
            verifyNoInteractions(suscripcionAccesoRepository);
        }
    }

    @Nested
    @DisplayName("Con suscripciones.externo = true (copia local)")
    class ConFlagActivado {

        private AccesoSuscripciones build() {
            return new AccesoSuscripciones(true, subscriptionService, subscriptionRepository, suscripcionAccesoRepository);
        }

        @Test
        void acceso_leeDeCopiaLocal() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .periodoHasta(LocalDateTime.now().plusDays(10))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isTrue();
            verify(suscripcionAccesoRepository).findById(42L);
            verifyNoInteractions(subscriptionService);
        }

        @Test
        void acceso_siNoExisteFila_retornaFalse() {
            AccesoSuscripciones accesos = build();
            when(suscripcionAccesoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThat(accesos.acceso(99L)).isFalse();
            verify(suscripcionAccesoRepository).findById(99L);
        }

        @Test
        void acceso_cancelledConPeriodoEnFuturo_tieneAcceso() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("CANCELLED")
                    .periodoHasta(LocalDateTime.now().plusDays(5))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isTrue();
        }

        @Test
        void acceso_cancelledConPeriodoEnPasado_noTieneAcceso() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("CANCELLED")
                    .periodoHasta(LocalDateTime.now().minusSeconds(5))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isFalse();
        }

        @Test
        void acceso_activeConPeriodoEnFuturo_tieneAcceso() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .periodoHasta(LocalDateTime.now().plusDays(1))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isTrue();
        }

        @Test
        void acceso_activeConPeriodoEnPasado_noTieneAcceso() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .periodoHasta(LocalDateTime.now().minusSeconds(5))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isFalse();
        }

        @Test
        void acceso_graciaNoOtorgaAccesoSiPeriodoVencio() {
            // isAccessAllowed solo evalúa que el período pago esté vigente; graceUntil no otorga acceso por sí mismo
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .periodoHasta(LocalDateTime.now().minusDays(1))
                    .graciaHasta(LocalDateTime.now().plusDays(5))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isFalse();
        }

        @Test
        void acceso_siAccesoGuardadoEsFalse_noTieneAccesoAunquePeriodoEsteVigente() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .periodoHasta(LocalDateTime.now().plusDays(10))
                    .acceso(false)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isFalse();
        }

        @Test
        void acceso_estadoNoPermitido_noTieneAcceso() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("SUSPENDED")
                    .periodoHasta(LocalDateTime.now().plusDays(10))
                    .acceso(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.acceso(42L)).isFalse();
        }

        @Test
        void puedeRecetar_leeDeCopiaLocal() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .puedeRecetar(true)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.puedeRecetar(42L)).isTrue();
            verify(suscripcionAccesoRepository).findById(42L);
            verifyNoInteractions(subscriptionService);
        }

        @Test
        void estado_leeDeCopiaLocal() {
            AccesoSuscripciones accesos = build();
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .estado("ACTIVE")
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.estado(42L)).isEqualTo("ACTIVE");
            verify(suscripcionAccesoRepository).findById(42L);
            verifyNoInteractions(subscriptionRepository);
        }

        @Test
        void periodoHasta_leeLocalDateTimeDeCopiaLocal() {
            AccesoSuscripciones accesos = build();
            LocalDateTime fechaHora = LocalDateTime.of(2026, 12, 31, 23, 59, 59);
            SuscripcionAcceso sa = SuscripcionAcceso.builder()
                    .professionalId(42L)
                    .periodoHasta(fechaHora)
                    .build();
            when(suscripcionAccesoRepository.findById(42L)).thenReturn(Optional.of(sa));

            assertThat(accesos.periodoHasta(42L)).isEqualTo(fechaHora);
            verify(suscripcionAccesoRepository).findById(42L);
            verifyNoInteractions(subscriptionRepository);
        }
    }

    @Test
    void valoresNulos_retornanDefaultSinLlamarRepositorios() {
        AccesoSuscripciones conFlag = new AccesoSuscripciones(true, subscriptionService, subscriptionRepository, suscripcionAccesoRepository);
        AccesoSuscripciones sinFlag = new AccesoSuscripciones(false, subscriptionService, subscriptionRepository, suscripcionAccesoRepository);

        assertThat(conFlag.acceso(null)).isFalse();
        assertThat(conFlag.puedeRecetar(null)).isFalse();
        assertThat(conFlag.estado(null)).isNull();
        assertThat(conFlag.periodoHasta(null)).isNull();

        assertThat(sinFlag.acceso(null)).isFalse();
        assertThat(sinFlag.puedeRecetar(null)).isFalse();
        assertThat(sinFlag.estado(null)).isNull();
        assertThat(sinFlag.periodoHasta(null)).isNull();

        verifyNoInteractions(subscriptionService, subscriptionRepository, suscripcionAccesoRepository);
    }
}