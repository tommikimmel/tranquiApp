package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_payment_id", columnList = "payment_id"),
        @Index(name = "idx_invoice_pto_cbte", columnList = "punto_venta, cbte_tipo, cbte_numero"),
        @Index(name = "idx_invoice_cae", columnList = "cae")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    // 11 = Factura C, 13 = Nota de Crédito C
    @Builder.Default
    @Column(name = "cbte_tipo", nullable = false)
    private Integer cbteTipo = 11;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    @Column(name = "cbte_numero")
    private Long cbteNumero;

    @Column(name = "cae", length = 30)
    private String cae;

    @Column(name = "cae_vencimiento")
    private LocalDate caeVencimiento;

    // 80 = CUIT, 96 = DNI, 99 = Consumidor Final
    @Column(name = "receptor_doc_tipo", nullable = false)
    private Integer receptorDocTipo;

    @Column(name = "receptor_doc_nro", nullable = false)
    private Long receptorDocNro;

    @Column(name = "receptor_nombre", nullable = false, length = 200)
    private String receptorNombre;

    // RG 5616/2024: 1=RI, 4=Exento, 5=Consumidor Final, 6=Monotributo
    @Column(name = "receptor_condicion_iva", nullable = false)
    private Integer receptorCondicionIva;

    @Column(name = "importe_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal importeTotal;

    @Builder.Default
    @Column(name = "moneda", nullable = false, length = 10)
    private String moneda = "PES";

    @Builder.Default
    @Column(name = "cotizacion", nullable = false, precision = 8, scale = 4)
    private BigDecimal cotizacion = BigDecimal.ONE;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "pdf_base64", columnDefinition = "TEXT")
    private String pdfBase64;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "arca_request_json", columnDefinition = "TEXT")
    private String arcaRequestJson;

    @Column(name = "arca_response_json", columnDefinition = "TEXT")
    private String arcaResponseJson;

    // Reference to original invoice when cbteTipo == 13 (Nota de Crédito)
    @Column(name = "comprobante_asociado_id")
    private Long comprobanteAsociadoId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fechaEmision == null) fechaEmision = LocalDate.now();
        if (status == null) status = InvoiceStatus.PENDING;
    }
}
