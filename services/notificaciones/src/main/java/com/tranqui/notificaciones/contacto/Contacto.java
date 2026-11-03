package com.tranqui.notificaciones.contacto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Copia mínima de un usuario (sección 6.6 del plan): lo que hace falta para mandarle un mail o una
 * notificación sin preguntarle a identidad. Se mantiene con los eventos de identidad y profesionales.
 */
@Entity
@Table(name = "contacto")
public class Contacto {

    /** Id del usuario en identidad. */
    @Id
    private Long id;

    @Column(nullable = false)
    private String email;

    private String nombre;

    private String apellido;

    private String rol;

    /** Mi Cuenta, "Notificaciones por email": si es false no recibe mails al paciente. */
    @Column(name = "email_habilitado", nullable = false)
    private boolean emailHabilitado = true;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected Contacto() {
    }

    public Contacto(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getRol() { return rol; }
    public boolean isEmailHabilitado() { return emailHabilitado; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }

    public void setEmail(String email) { this.email = email; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public void setRol(String rol) { this.rol = rol; }
    public void setEmailHabilitado(boolean emailHabilitado) { this.emailHabilitado = emailHabilitado; }
    public void tocar() { this.actualizadoEn = OffsetDateTime.now(); }
}
