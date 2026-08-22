package com.tranqui.app.model.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteRequestDto {
    private String reason; // Motivo de anulación o rectificación
}
