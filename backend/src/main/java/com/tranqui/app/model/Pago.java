package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id")
    private Turno turno;

    @Column(name = "solicitud_documento_id")
    private Long solicitudDocumentoId;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 0;
        }
        if (fechaPago == null) {
            fechaPago = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (version == null) {
            version = 0;
        }
        version++;
    }
}
