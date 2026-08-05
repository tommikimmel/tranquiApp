package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifa_medico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarifaMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "servicio_id", nullable = false, length = 50)
    private String servicioId; // e.g. "particular", "primera", "osde"

    @Column(name = "label", nullable = false, length = 100)
    private String label; // e.g. "Consulta particular"

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    @Column(name = "requiere_obra_social", nullable = false)
    private boolean requiereObraSocial;
}
