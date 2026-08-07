package com.tranqui.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "seguimiento_diario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeguimientoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "estado_animo", nullable = false, length = 50)
    private String estadoAnimo; // e.g. "Excelente", "Bueno", "Regular", "Malo"

    @Column(name = "sintomas", columnDefinition = "TEXT")
    private String sintomas;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    // Mismo régimen de inalterabilidad que InformeClinico (ver ese modelo y
    // .agent/Etapas/09_cumplimiento_legal_historia_clinica.md): nunca se borra la fila, solo se
    // anula (soft-delete) dejando motivo y hash de integridad.
    @Column(name = "estado", length = 30)
    @Builder.Default
    private String estado = "VIGENTE";

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "hash_integridad", length = 64)
    private String hashIntegridad;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        if (fecha == null) {
            fecha = LocalDate.now();
        }
    }

    @com.fasterxml.jackson.annotation.JsonProperty("nombreMedico")
    public String getNombreMedico() {
        return medico != null ? medico.getNombre() + (medico.getApellido() != null ? " " + medico.getApellido() : "") : null;
    }
}
