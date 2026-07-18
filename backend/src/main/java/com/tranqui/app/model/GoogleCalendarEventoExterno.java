package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Cache of events read from a médico's Google Calendar that the app did NOT create itself
// (i.e. not a Turno). Populated by GoogleCalendarSyncService and kept in sync via syncToken
// deltas, so the calendar UI and "Próximos Eventos" panel can read it without a live call to
// Google on every page load.
@Entity
@Table(name = "google_calendar_evento_externo", indexes = {
        @Index(name = "idx_gcee_medico_id", columnList = "medico_id"),
        @Index(name = "idx_gcee_fecha_inicio", columnList = "fecha_inicio")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleCalendarEventoExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "google_event_id", nullable = false, unique = true, length = 255)
    private String googleEventId;

    @Column(name = "titulo", length = 500)
    private String titulo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Builder.Default
    @Column(name = "todo_el_dia", nullable = false)
    private Boolean todoElDia = false;

    // Mirrors Google's event status ("confirmed" | "tentative"). Cancelled events are deleted
    // from this cache outright rather than stored with a cancelled status — see
    // GoogleCalendarSyncService.
    @Column(name = "estado", length = 20)
    private String estado;

    @Column(name = "ultima_actualizacion", nullable = false)
    private LocalDateTime ultimaActualizacion;
}
