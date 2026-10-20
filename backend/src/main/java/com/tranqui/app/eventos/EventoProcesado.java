package com.tranqui.app.eventos;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que representa la tabla eventos_procesados (sección 6.2 de docs/arquitectura/microservicios.md).
 * Clave primaria compuesta por (event_id, consumidor).
 */
@Entity
@Table(name = "eventos_procesados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(EventoProcesado.EventoProcesadoId.class)
public class EventoProcesado {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumidor", nullable = false, length = 80)
    private String consumidor;

    @Column(name = "procesado_en", nullable = false)
    private OffsetDateTime procesadoEn;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventoProcesadoId implements Serializable {
        private UUID eventId;
        private String consumidor;
    }
}
