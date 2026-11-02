package com.tranqui.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mensaje", indexes = {
        @Index(name = "idx_mensaje_remitente_id", columnList = "remitente_id"),
        @Index(name = "idx_mensaje_destinatario_id", columnList = "destinatario_id"),
        @Index(name = "idx_mensaje_leido", columnList = "leido")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mensaje {

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
    @JoinColumn(name = "remitente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Usuario remitente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinatario_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Usuario destinatario;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Builder.Default
    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    @Builder.Default
    @Column(name = "leido", nullable = false)
    private Boolean leido = false;

    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 0;
        }
        if (fechaEnvio == null) {
            fechaEnvio = LocalDateTime.now();
        }
        if (leido == null) {
            leido = false;
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
