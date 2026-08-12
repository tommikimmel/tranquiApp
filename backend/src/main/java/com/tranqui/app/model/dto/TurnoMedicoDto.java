package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoMedicoDto {
    private Long id;
    private String patientName;
    private String hour;
    private String horaInicio; // HH:mm exacto — turno.hour solo trae la hora entera
    private String ampm;
    private String type;
    private String modalidad; // ONLINE o PRESENCIAL
    private String status; // confirmed, pending, completed
    private String attendanceStatus; // ESPERANDO, LLEGO, AUSENTE, COMPLETADA
    private String meetLink;
    private String fecha;
    private String checkoutUrl;
    private boolean firstConsultation;
    private PacienteDto patientInfo;
    private String domicilioAtencion;
    private Double domicilioLat;
    private Double domicilioLng;
    private String domicilioAtencionTorre;
    private String domicilioAtencionPiso;
    private String domicilioAtencionDepto;
    private String domicilioAtencionBarrio;
    private String metadataAfiliado;
    // False for pure document services (recetas, certificados, informes) — see
    // Turno.ocupaAgenda. The frontend uses this to keep them out of hour-based/calendar views.
    private boolean ocupaAgenda;
    // Only meaningful when ocupaAgenda is false — see Turno.documentoEnviado.
    private boolean documentoEnviado;
}
