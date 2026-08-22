package com.tranqui.app.model.dto;

import com.tranqui.app.model.InvoiceStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDto {
    private Long id;
    private Long paymentId;
    private Integer cbteTipo; // 11 = Factura C, 13 = Nota de Crédito C
    private String cbteTipoNombre;
    private Integer puntoVenta;
    private Long cbteNumero;
    private String cae;
    private LocalDate caeVencimiento;
    private String receptorNombre;
    private Long receptorDocNro;
    private Integer receptorCondicionIva;
    private BigDecimal importeTotal;
    private LocalDate fechaEmision;
    private String pdfUrl;
    private InvoiceStatus status;
    private String lastError;
    private Long comprobanteAsociadoId;
    private LocalDateTime issuedAt;
}
