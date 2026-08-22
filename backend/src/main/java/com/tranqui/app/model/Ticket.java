package com.tranqui.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// A support ticket opened by a paciente or profesional, resolved by an admin through an
// in-app chat thread (TicketMensaje). Replaces the old "Quejas y Soporte" fire-and-forget email
// (ComplaintModal/SoporteController#enviarQueja) — this one persists, is consultable by the
// creator afterwards, and notifies them by email when an admin replies.
@Entity
@Table(name = "ticket", indexes = {
        @Index(name = "idx_ticket_creador_id", columnList = "creador_id"),
        @Index(name = "idx_ticket_estado", columnList = "estado")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creador_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Usuario creador;

    @Column(name = "asunto", nullable = false, length = 200)
    private String asunto;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private TicketEstado estado = TicketEstado.PENDIENTE;

    @Builder.Default
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Builder.Default
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        if (fechaActualizacion == null) fechaActualizacion = LocalDateTime.now();
    }
}
