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
        private String regNo; // ANMAT registration number — from QBI2's real medicamento catalog (GetMedicamento), not invented
        private String nombreDroga; // generic/active-ingredient name, from the same catalog entry
        private Boolean noSustituible; // "No sustituible" per Decreto 987/03 Art.2° — maps to Qbi2 permiteSustitucion
        private String laboratorio; // manually entered by the médico — QBI2's catalog doesn't expose this field
    }
}
