package com.tranqui.app.model.dto;

import java.time.LocalDateTime;

public interface CanalPrioritarioDto {
    Long getId();
    String getNombre();
    String getEmail();
    String getPrioridadClinica();
    LocalDateTime getUltimoMensaje();
}
