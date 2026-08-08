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
    private Long id;
    private String nombre;
    private String email;
    private Rol rol;
    private String telefono;
    private Boolean perfilCompleto;
    private Boolean requiereAceptarTerminos; // true = terminosAceptadosEn == null, ver AuthController

    public UserResponseDto(Long id, String nombre, String email, Rol rol) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
    }
}
