package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoResponseDto {
    private Long turnoId;
    private String estado; // CONFIRMADO, PENDIENTE_PAGO, etc.
    private String attendanceStatus; // ESPERANDO, LLEGO, AUSENTE, COMPLETADA
    private LocalDate fecha;
    private LocalTime horaInicio;
    private BigDecimal precio;
    private String checkoutUrl; // URL para pagar con Mercado Pago
    private String meetLink; // Google Meet URL si ya está confirmado
}
