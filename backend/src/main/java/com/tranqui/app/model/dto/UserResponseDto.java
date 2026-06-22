package com.tranqui.app.model.dto;

import com.tranqui.app.model.Rol;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private String nombre;
    private String email;
    private Rol rol;
}
