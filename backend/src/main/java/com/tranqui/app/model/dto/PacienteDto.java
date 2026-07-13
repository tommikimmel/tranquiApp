package com.tranqui.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteDto {
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
    private String dni;
    private String direccion;
    private String obraSocial;
    private String numAfiliado;
    private String ultimaVisita;
    private String prioridadClinica;
    private boolean sinTurno;
    private int unreadMessagesCount;

    private String apellido;
    private String sexo;
    private String fechaNacimiento; // "YYYY-MM-DD"
    private Long cuil;
    private String mail;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String datosOfuscado;
    private CredencialInfoDto credencial;

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredencialInfoDto {
        private Integer codEntidad;
        private String pan;
        private String plan;
        private String token;
    }
}
