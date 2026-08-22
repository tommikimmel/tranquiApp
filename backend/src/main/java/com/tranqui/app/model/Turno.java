package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "turno", indexes = {
        @Index(name = "idx_turno_medico_id", columnList = "medico_id"),
        @Index(name = "idx_turno_paciente_id", columnList = "paciente_id"),
        @Index(name = "idx_turno_fecha", columnList = "fecha"),
        @Index(name = "idx_turno_estado", columnList = "estado")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoTurno tipo;

    // Nullable: turnos reservados antes de que existieran agendas separadas por modalidad
    // quedan null. Turnos nuevos siempre la traen (ver TurnoService.reservarTurno).
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", length = 20)
    private Modalidad modalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoTurno estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "asistencia", nullable = false, length = 30)
    @Builder.Default
    private EstadoAsistencia asistencia = EstadoAsistencia.ESPERANDO;

    @Builder.Default
    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio = BigDecimal.ZERO;

    @Column(name = "metadata_afiliado", length = 100)
    private String metadataAfiliado;

    @Column(name = "servicio_id", length = 50)
    private String servicioId; // id de la tarifa elegida (Honorarios y Servicios), para servicios custom del médico

    // Copied from TarifaMedico.requiereAgenda at booking time (see TurnoService.reservarTurno) —
    // false for pure document services (recetas, certificados, informes): no consultorio, no
    // videollamada, so fecha/horaInicio/horaFin are just the booking timestamp, not a real
    // scheduled slot, and this turno must never block obtenerHorariosDisponibles for anyone else.
    // Denormalized rather than re-joined against tarifa_medico on every availability lookup, and
    // stays historically accurate even if the médico later flips the tarifa's flag.
    // columnDefinition (not just nullable=false): same backfill reasoning as
    // TarifaMedico.requiereAgenda — this table already has production rows, and a bare
    // `nullable=false` ALTER TABLE with no DEFAULT fails against them on Postgres.
    @Builder.Default
    @Column(name = "ocupa_agenda", columnDefinition = "boolean not null default true")
    private boolean ocupaAgenda = true;

    @Column(name = "id_financiador", length = 50)
    private String idFinanciador; // id del financiador QBI2 elegido, cuando el servicio requiere obra social

    @Column(name = "telemedicina_url", length = 500)
    private String telemedicinaUrl;

    @Column(name = "google_event_id", length = 255)
    private String googleEventId;

    // Mercado Pago preference URL, generated once when the turno is created and reused from
    // here on — crearPreferenciaPago() creates a brand new preference on Mercado Pago's side
    // on every call, so re-calling it on every "Mis Turnos" read (as obtenerTurnosPaciente used
    // to) was both slow (a synchronous external HTTP call per pending turno) and wasteful
    // (orphaned duplicate preferences left on Mercado Pago).
    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @OneToOne(mappedBy = "turno", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Pago pago;

    @Builder.Default
    @Column(name = "recordatorio_enviado", nullable = false)
    private Boolean recordatorioEnviado = false;

    // Only meaningful for document-only turnos (ocupaAgenda == false): whether the médico has
    // marked the receta/certificado/informe as sent to the patient (via email or WhatsApp,
    // through their own client — see TurnoController#marcarDocumentoEnviado). Drives the
    // "Documento pendiente" / "Documento enviado" state shown to the patient in "Mis Turnos".
    // Same safe-backfill reasoning as ocupaAgenda: this table already has production rows.
    @Builder.Default
    @Column(name = "documento_enviado", columnDefinition = "boolean not null default false")
    private boolean documentoEnviado = false;

    // Copied from TarifaMedico.esReceta at booking time (see TurnoService.reservarTurno), same
    // denormalization rationale as ocupaAgenda above. Only meaningful when ocupaAgenda is false —
    // drives whether this document turno shows "Generar receta →" or the "Enviar por mail" flow
    // (see TurnoService#esReceta, DocumentActions.tsx).
    @Builder.Default
    @Column(name = "es_receta", columnDefinition = "boolean not null default false")
    private boolean esReceta = false;

    @Builder.Default
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
