package com.tranqui.app.model.dto;

import lombok.*;

// A médico's personal Google Calendar event, read-only, shown merged with their turnos in the
// dashboard calendar and "Próximos Eventos" panel. Shaped like TurnoMedicoDto's fecha/hour
// fields so the frontend can sort/merge both lists the same way.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoExternoDto {
    private String id; // Google event id
    private String title;
    private String fecha; // yyyy-MM-dd
    private String hour;
    private String endHour;
    private boolean allDay;
}
