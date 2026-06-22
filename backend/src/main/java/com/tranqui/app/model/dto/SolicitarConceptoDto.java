package com.tranqui.app.model.dto;

import com.tranqui.app.model.TipoConcepto;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitarConceptoDto {
    private Long medicoId;
    private TipoConcepto tipoConcepto;
    private BigDecimal precio;
}
