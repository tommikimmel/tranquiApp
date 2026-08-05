package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "receta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @Column(name = "medicamentos", nullable = false, columnDefinition = "TEXT")
    private String medicamentos; // JSON or text list of medicines

    @Column(name = "diagnostico", length = 150)
    private String diagnostico; // CIE-10

    @Column(name = "indicaciones", columnDefinition = "TEXT")
    private String indicaciones;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl; // Official S3 PDF link returned by QBI2/Innovamed — never a locally-generated document

    @Column(name = "qbi2_id_receta", length = 100)
    private String qbi2IdReceta; // Recetario/authorization number from QBI2's RecetaResult.idReceta

    @Column(name = "qbi2_verificador", length = 255)
    private String qbi2Verificador; // Full verification URL, e.g. https://qa.verumrp.com.ar/&lt;hash&gt;

    @Column(name = "qbi2_nro_cuir", length = 500)
    private String qbi2NroCuir;

    @Column(name = "qbi2_fecha_vencimiento", length = 50)
    private String qbi2FechaVencimiento;

    @Column(name = "qbi2_id_transaccion", length = 100)
    private String qbi2IdTransaccion;

    @Builder.Default
    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (fechaEmision == null) {
            fechaEmision = LocalDateTime.now();
        }
    }
}
