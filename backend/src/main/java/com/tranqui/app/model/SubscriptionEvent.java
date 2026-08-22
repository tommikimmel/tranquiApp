package com.tranqui.app.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_events", indexes = {
        @Index(name = "idx_sub_event_sub_id", columnList = "subscription_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // Nombre del enum SubscriptionStatus antes/después de la transición, o null si el evento
    // no representa un cambio de estado (ej. MP_CHECKOUT_CREATED). El panel de admin las usa
    // para mostrar la transición "Estado A ➔ Estado B" en el historial de auditoría.
    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "new_status", length = 50)
    private String newStatus;

    // system | admin | professional | mp_webhook
    @Column(name = "actor_type", nullable = false, length = 50)
    private String actorType;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    // Expuesto al frontend como "details" (nombre más claro que el detalle interno de
    // implementación "payloadJson", que hoy en día es siempre texto plano, no JSON).
    @JsonProperty("details")
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
