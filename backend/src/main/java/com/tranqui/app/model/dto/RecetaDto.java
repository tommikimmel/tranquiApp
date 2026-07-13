package com.tranqui.app.model.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetaDto {
    private Long pacienteId;
    private List<MedicamentoDto> medications;
    private String diagnosis;
    private String notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicamentoDto {
        private String name;
        private String dosage;
        private String frequency;
        private String duration;
    }
}
