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

    @Builder.Default
    @Column(name = "version")
    private Integer version = 0;

    /**
     * Versión del agregado para publicación de eventos de dominio (sección 5.3 y 6.4 del plan).
     * En la creación (version == 0), retorna 1.
     * Tras cada actualización persistida (donde @PreUpdate incrementa el campo version),
     * retorna version + 1, garantizando que el aggregateVersion publicado sea estrictamente creciente
     * (1 en creación, 2 en primera actualización, 3 en segunda, etc.).
     * Para que el evento lleve la versión actualizada, el guardado de la entidad debe realizarse
     * mediante saveAndFlush(...) antes de publicar el evento, asegurando la ejecución de @PreUpdate.
     */
    public int getAggregateVersion() {
        return (version != null ? version : 0) + 1;
    }

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
        if (version == null) version = 0;
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        if (fechaActualizacion == null) fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (version == null) {
            version = 0;
        }
        version++;
        fechaActualizacion = LocalDateTime.now();
    }
}
