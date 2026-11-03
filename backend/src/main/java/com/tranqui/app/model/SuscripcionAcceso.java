package com.tranqui.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "suscripcion_acceso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionAcceso {

    @Id
    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "estado", length = 50)
    private String estado;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(name = "acceso", nullable = false)
    private boolean acceso;

    @Column(name = "puede_recetar", nullable = false)
    private boolean puedeRecetar;

    @Column(name = "periodo_hasta")
    private LocalDateTime periodoHasta;

    @Column(name = "gracia_hasta")
    private LocalDateTime graciaHasta;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}