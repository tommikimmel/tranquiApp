package com.tranqui.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "informe_clinico", indexes = {
        @Index(name = "idx_informe_clinico_paciente_id", columnList = "paciente_id"),
        @Index(name = "idx_informe_clinico_medico_id", columnList = "medico_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeClinico {

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

    @Column(name = "tipo_informe", nullable = false, length = 50)
    private String tipoInforme; // e.g. "Evaluativo", "Evolutivo", "General", "Final"

    @Column(name = "plan_trabajo", columnDefinition = "TEXT")
    private String planTrabajo;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo; // If uploaded/attached

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    // Ley 26.529/27.706: la historia clínica no puede perder asientos ni editarse en el lugar —
    // ver .agent/Etapas/09_cumplimiento_legal_historia_clinica.md. VIGENTE | VIGENTE_CORREGIDO
    // (tiene un anexo posterior) | ANULADO (soft-delete, la fila nunca se borra) |
    // ANEXO_CORRECCION (fila nueva que corrige a `informeOriginalId`, ver ClinicalService).
    @Column(name = "estado", length = 30)
    @Builder.Default
    private String estado = "VIGENTE";

    @Column(name = "informe_original_id")
    private Long informeOriginalId;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    // SHA-256 sobre el contenido + autor + timestamp al momento de creación de esta fila
    // (ClinicalService.calcularHash) — sostiene valor probatorio si se cuestiona la integridad.
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
