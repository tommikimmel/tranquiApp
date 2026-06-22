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
}
