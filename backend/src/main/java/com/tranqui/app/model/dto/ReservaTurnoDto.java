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
    private TipoTurno tipo; // PARTICULAR, OBRA_SOCIAL, OSDE
    private String servicioId; // id de la tarifa elegida en Honorarios y Servicios (para servicios custom del médico)
    private String obraSocial; // Nombre de la Obra Social (e.g. OSDE, Swiss Medical)
    private String idFinanciador; // id del financiador QBI2 elegido en el combo (cuando la tarifa requiere obra social)
    private String metadataAfiliado; // Número de afiliado
    private String nombrePaciente;
    private String emailPaciente;
    private String telefonoPaciente;
}
