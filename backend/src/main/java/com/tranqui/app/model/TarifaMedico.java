package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifa_medico", indexes = {
        @Index(name = "idx_tarifa_medico_medico_id", columnList = "medico_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarifaMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "servicio_id", nullable = false, length = 50)
    private String servicioId; // e.g. "particular", "primera", "osde"

    @Column(name = "label", nullable = false, length = 100)
    private String label; // e.g. "Consulta particular"

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    // columnDefinition (not just nullable=false) is required here: this table already had
    // production rows when this column was added, and a plain "not null" ALTER COLUMN ADD
    // with no DEFAULT fails against Postgres ("column ... contains null values") because it
    // can't backfill existing rows — see 2026-08-05 incident where this broke /api/medicos/perfil.
    @Column(name = "requiere_obra_social", columnDefinition = "boolean not null default false")
    private boolean requiereObraSocial;

    // The specific obra social this service is for (e.g. "OSDE", "Swiss Medical") — a médico who
    // works with several obras sociales creates one service per obra social rather than one
    // generic "Obra Social" service. Nullable: legacy services (the "obra_social"/"osde" default
    // ids) predate this column and keep requiereObraSocial as their only signal.
    @Column(name = "obra_social", length = 100)
    private String obraSocial;

    // Per-modalidad price overrides — null means "use precio for this modalidad too" (the
    // common case: most médicos charge the same regardless of modalidad). Only médicos who
    // opt into differentiated pricing for this specific service set one or both of these.
    @Column(name = "precio_online", precision = 12, scale = 2)
    private java.math.BigDecimal precioOnline;

    @Column(name = "precio_presencial", precision = 12, scale = 2)
    private java.math.BigDecimal precioPresencial;

    // Whether booking this service reserves a slot on the médico's agenda. True (default) for
    // normal consultations. False for pure document services (recetas, certificados, informes) —
    // no consultorio, no videollamada, so a booking shouldn't block anyone else's availability.
    // columnDefinition (not just nullable=false): same backfill reasoning as requiereObraSocial
    // above — this table already has production rows.
    @Builder.Default
    @Column(name = "requiere_agenda", columnDefinition = "boolean not null default true")
    private boolean requiereAgenda = true;

    // Only meaningful when requiereAgenda is false (a pure document service): whether it's
    // specifically a receta — those route to "Generar receta →" (Recetas Electrónicas / QBI2)
    // instead of the "Enviar por mail" flow every other document service uses (see
    // TurnoService#marcarDocumentoEnviado / DocumentActions.tsx). A médico can have several
    // custom document services (e.g. "Receta de control" vs "Certificado laboral"), so this
    // can't be inferred from servicioId alone the way the built-in "receta-fuera" tarifa is.
    @Builder.Default
    @Column(name = "es_receta", columnDefinition = "boolean not null default false")
    private boolean esReceta = false;
}
