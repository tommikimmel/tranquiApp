package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Registro de cada envío de novedades (mail masivo al terminar un mantenimiento). El id lo define
// quien dispara el envío y hace idempotente la operación: un mismo id nunca se envía dos veces.
@Entity
@Table(name = "novedades_envio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovedadesEnvio {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    // Resumen legible del resultado (destinatarios por segmento, ids de broadcast o error).
    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    private LocalDateTime finalizadoEn;

    // Qué segmentos ya se enviaron: si un envío falla a mitad, reintentarlo no repite estos.
    @Builder.Default
    @Column(nullable = false)
    private boolean enviadoPacientes = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean enviadoProfesionales = false;

    public enum Estado { ENVIANDO, ENVIADO, ERROR }
}
