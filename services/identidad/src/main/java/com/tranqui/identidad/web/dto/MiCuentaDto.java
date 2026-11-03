package com.tranqui.identidad.web.dto;

import java.time.LocalDate;

public class MiCuentaDto {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String sexo;
    private LocalDate fechaNacimiento;
    private String tipoDocumento;
    private Integer numeroDocumento;
    private String dni;
    private Long cuil;
    private String obraSocial;
    private String numAfiliado;
    private String fotoUrl;

    private String domicilioCalle;
    private String domicilioNumero;
    private String domicilioPiso;
    private String domicilioDpto;
    private String domicilioCodigoPostal;
    private String domicilioLocalidad;
    private String domicilioProvincia;
    private String domicilioPais;

    private boolean tienePassword;
    private boolean notificacionesEmailHabilitadas;
    private boolean recibirNovedades;

    public MiCuentaDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public Integer getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(Integer numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public Long getCuil() { return cuil; }
    public void setCuil(Long cuil) { this.cuil = cuil; }

    public String getObraSocial() { return obraSocial; }
    public void setObraSocial(String obraSocial) { this.obraSocial = obraSocial; }

    public String getNumAfiliado() { return numAfiliado; }
    public void setNumAfiliado(String numAfiliado) { this.numAfiliado = numAfiliado; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getDomicilioCalle() { return domicilioCalle; }
    public void setDomicilioCalle(String domicilioCalle) { this.domicilioCalle = domicilioCalle; }

    public String getDomicilioNumero() { return domicilioNumero; }
    public void setDomicilioNumero(String domicilioNumero) { this.domicilioNumero = domicilioNumero; }

    public String getDomicilioPiso() { return domicilioPiso; }
    public void setDomicilioPiso(String domicilioPiso) { this.domicilioPiso = domicilioPiso; }

    public String getDomicilioDpto() { return domicilioDpto; }
    public void setDomicilioDpto(String domicilioDpto) { this.domicilioDpto = domicilioDpto; }

    public String getDomicilioCodigoPostal() { return domicilioCodigoPostal; }
    public void setDomicilioCodigoPostal(String domicilioCodigoPostal) { this.domicilioCodigoPostal = domicilioCodigoPostal; }

    public String getDomicilioLocalidad() { return domicilioLocalidad; }
    public void setDomicilioLocalidad(String domicilioLocalidad) { this.domicilioLocalidad = domicilioLocalidad; }

    public String getDomicilioProvincia() { return domicilioProvincia; }
    public void setDomicilioProvincia(String domicilioProvincia) { this.domicilioProvincia = domicilioProvincia; }

    public String getDomicilioPais() { return domicilioPais; }
    public void setDomicilioPais(String domicilioPais) { this.domicilioPais = domicilioPais; }

    public boolean isTienePassword() { return tienePassword; }
    public void setTienePassword(boolean tienePassword) { this.tienePassword = tienePassword; }

    public boolean isNotificacionesEmailHabilitadas() { return notificacionesEmailHabilitadas; }
    public void setNotificacionesEmailHabilitadas(boolean notificacionesEmailHabilitadas) { this.notificacionesEmailHabilitadas = notificacionesEmailHabilitadas; }

    public boolean isRecibirNovedades() { return recibirNovedades; }
    public void setRecibirNovedades(boolean recibirNovedades) { this.recibirNovedades = recibirNovedades; }
}
