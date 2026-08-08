package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambiarPasswordDto {
    private String currentPassword; // opcional si la cuenta todavía no tiene password (solo Google)
    private String newPassword;
}
