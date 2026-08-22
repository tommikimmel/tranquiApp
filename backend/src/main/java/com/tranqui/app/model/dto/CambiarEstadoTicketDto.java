package com.tranqui.app.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoTicketDto {
    private String estado; // PENDIENTE | ACTIVO | RESUELTO
}
