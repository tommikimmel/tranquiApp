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
    private String ampm;
    private String type;
    private String status; // confirmed, pending, completed
    private String meetLink;
}
