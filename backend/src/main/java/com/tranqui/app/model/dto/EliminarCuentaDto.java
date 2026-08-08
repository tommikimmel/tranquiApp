package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EliminarCuentaDto {
    private String password; // opcional si la cuenta no tiene password (solo Google)
}
