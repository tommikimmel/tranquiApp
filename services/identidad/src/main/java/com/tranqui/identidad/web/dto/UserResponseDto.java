package com.tranqui.identidad.web.dto;

import java.time.LocalDateTime;

public class UserResponseDto {
    private Long id;
    private String nombre;
    private String email;
    private String rol;
    private String telefono;
    private Boolean perfilCompleto;
    private Boolean requiereAceptarTerminos;
    private Boolean mustChangePassword;
    private String profession;
    private Boolean canPrescribe;
    private Boolean verificadoAdmin;
    private LocalDateTime licenseVerifiedAt;

    public UserResponseDto() {}

    public UserResponseDto(Long id, String nombre, String email, String rol, String telefono,
                           Boolean perfilCompleto, Boolean requiereAceptarTerminos, Boolean mustChangePassword) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
        this.telefono = telefono;
        this.perfilCompleto = perfilCompleto;
        this.requiereAceptarTerminos = requiereAceptarTerminos;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public Boolean getPerfilCompleto() { return perfilCompleto; }
    public void setPerfilCompleto(Boolean perfilCompleto) { this.perfilCompleto = perfilCompleto; }

    public Boolean getRequiereAceptarTerminos() { return requiereAceptarTerminos; }
    public void setRequiereAceptarTerminos(Boolean requiereAceptarTerminos) { this.requiereAceptarTerminos = requiereAceptarTerminos; }

    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public Boolean getCanPrescribe() { return canPrescribe; }
    public void setCanPrescribe(Boolean canPrescribe) { this.canPrescribe = canPrescribe; }

    public Boolean getVerificadoAdmin() { return verificadoAdmin; }
    public void setVerificadoAdmin(Boolean verificadoAdmin) { this.verificadoAdmin = verificadoAdmin; }

    public LocalDateTime getLicenseVerifiedAt() { return licenseVerifiedAt; }
    public void setLicenseVerifiedAt(LocalDateTime licenseVerifiedAt) { this.licenseVerifiedAt = licenseVerifiedAt; }
}
