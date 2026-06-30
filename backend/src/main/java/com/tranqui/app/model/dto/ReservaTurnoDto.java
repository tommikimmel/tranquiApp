package com.tranqui.app.model.dto;

import com.tranqui.app.model.TipoTurno;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaTurnoDto {
    private Long medicoId;
    private LocalDate fecha;
    private LocalTime hora;
    private TipoTurno tipo; // PARTICULAR o OSDE
    private String metadataAfiliado; // Número de afiliado OSDE
    private String nombrePaciente;
    private String emailPaciente;
    private String telefonoPaciente;
}
