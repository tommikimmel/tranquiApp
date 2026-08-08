package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "disponibilidad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana; // 1 = Lunes, 7 = Domingo

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // Nullable on purpose: Hibernate's ddl-auto=update can't backfill a NOT NULL default onto
    // existing rows, so legacy rows (created before per-modalidad agendas existed) keep this
    // null. A null modalidad is treated as valid for BOTH modalidades until the médico explicitly
    // saves either grid, at which point that save adopts (and replaces) the legacy rows — see
    // DisponibilidadRepository/DisponibilidadService.
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", length = 20)
    private Modalidad modalidad;
}
