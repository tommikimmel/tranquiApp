package com.tranqui.chat.mensaje;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** Un mensaje entre dos usuarios. Mismos campos que la tabla {@code mensaje} del monolito. */
@Entity
@Table(name = "mensaje")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remitente_id", nullable = false)
    private Long remitenteId;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(nullable = false)
    private boolean leido = false;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected Mensaje() {
    }

    public Mensaje(Long remitenteId, Long destinatarioId, String contenido) {
        this.remitenteId = remitenteId;
        this.destinatarioId = destinatarioId;
        this.contenido = contenido;
    }

    public Long getId() { return id; }
    public Long getRemitenteId() { return remitenteId; }
    public Long getDestinatarioId() { return destinatarioId; }
    public String getContenido() { return contenido; }
    public boolean isLeido() { return leido; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }

    /** Versión del agregado para el evento (1 al crearse), como en el monolito. */
    public int getAggregateVersion() { return version + 1; }
}
