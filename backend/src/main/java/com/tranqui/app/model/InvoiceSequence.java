package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_sequences", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"punto_venta", "cbte_tipo"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    @Column(name = "cbte_tipo", nullable = false)
    private Integer cbteTipo;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
