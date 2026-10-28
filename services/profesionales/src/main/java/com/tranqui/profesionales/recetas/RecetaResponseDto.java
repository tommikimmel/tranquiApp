package com.tranqui.profesionales.recetas;

import java.time.LocalDateTime;
import java.util.List;

public class RecetaResponseDto {
    private Long id;
    private MedicoSimpleDto medico;
    private PacienteSimpleDto paciente;
    private String medicamentos;
    private List<String> laboratorios;
    private String diagnostico;
    private String indicaciones;
    private String pdfUrl;
    private String qbi2IdReceta;
    private String qbi2Verificador;
    private String qbi2NroCuir;
    private String qbi2FechaVencimiento;
    private LocalDateTime fechaEmision;

    public RecetaResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MedicoSimpleDto getMedico() { return medico; }
    public void setMedico(MedicoSimpleDto medico) { this.medico = medico; }
    public PacienteSimpleDto getPaciente() { return paciente; }
    public void setPaciente(PacienteSimpleDto paciente) { this.paciente = paciente; }
    public String getMedicamentos() { return medicamentos; }
    public void setMedicamentos(String medicamentos) { this.medicamentos = medicamentos; }
    public List<String> getLaboratorios() { return laboratorios; }
    public void setLaboratorios(List<String> laboratorios) { this.laboratorios = laboratorios; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getIndicaciones() { return indicaciones; }
    public void setIndicaciones(String indicaciones) { this.indicaciones = indicaciones; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public String getQbi2IdReceta() { return qbi2IdReceta; }
    public void setQbi2IdReceta(String qbi2IdReceta) { this.qbi2IdReceta = qbi2IdReceta; }
    public String getQbi2Verificador() { return qbi2Verificador; }
    public void setQbi2Verificador(String qbi2Verificador) { this.qbi2Verificador = qbi2Verificador; }
    public String getQbi2NroCuir() { return qbi2NroCuir; }
    public void setQbi2NroCuir(String qbi2NroCuir) { this.qbi2NroCuir = qbi2NroCuir; }
    public String getQbi2FechaVencimiento() { return qbi2FechaVencimiento; }
    public void setQbi2FechaVencimiento(String qbi2FechaVencimiento) { this.qbi2FechaVencimiento = qbi2FechaVencimiento; }
    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RecetaResponseDto r = new RecetaResponseDto();
        public Builder id(Long v) { r.id = v; return this; }
        public Builder medico(MedicoSimpleDto v) { r.medico = v; return this; }
        public Builder paciente(PacienteSimpleDto v) { r.paciente = v; return this; }
        public Builder medicamentos(String v) { r.medicamentos = v; return this; }
        public Builder laboratorios(List<String> v) { r.laboratorios = v; return this; }
        public Builder diagnostico(String v) { r.diagnostico = v; return this; }
        public Builder indicaciones(String v) { r.indicaciones = v; return this; }
        public Builder pdfUrl(String v) { r.pdfUrl = v; return this; }
        public Builder qbi2IdReceta(String v) { r.qbi2IdReceta = v; return this; }
        public Builder qbi2Verificador(String v) { r.qbi2Verificador = v; return this; }
        public Builder qbi2NroCuir(String v) { r.qbi2NroCuir = v; return this; }
        public Builder qbi2FechaVencimiento(String v) { r.qbi2FechaVencimiento = v; return this; }
        public Builder fechaEmision(LocalDateTime v) { r.fechaEmision = v; return this; }
        public RecetaResponseDto build() { return r; }
    }

    public static class MedicoSimpleDto {
        private Long id;
        private String nombre;
        private String apellido;
        private String matricula;
        private String especialidad;
        private String email;

        public MedicoSimpleDto() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getApellido() { return apellido; }
        public void setApellido(String apellido) { this.apellido = apellido; }
        public String getMatricula() { return matricula; }
        public void setMatricula(String matricula) { this.matricula = matricula; }
        public String getEspecialidad() { return especialidad; }
        public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicoSimpleDto m = new MedicoSimpleDto();
            public Builder id(Long v) { m.id = v; return this; }
            public Builder nombre(String v) { m.nombre = v; return this; }
            public Builder apellido(String v) { m.apellido = v; return this; }
            public Builder matricula(String v) { m.matricula = v; return this; }
            public Builder especialidad(String v) { m.especialidad = v; return this; }
            public Builder email(String v) { m.email = v; return this; }
            public MedicoSimpleDto build() { return m; }
        }
    }

    public static class PacienteSimpleDto {
        private Long id;
        private String nombre;
        private String apellido;
        private String dni;
        private String email;
        private String telefono;

        public PacienteSimpleDto() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getApellido() { return apellido; }
        public void setApellido(String apellido) { this.apellido = apellido; }
        public String getDni() { return dni; }
        public void setDni(String dni) { this.dni = dni; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final PacienteSimpleDto p = new PacienteSimpleDto();
            public Builder id(Long v) { p.id = v; return this; }
            public Builder nombre(String v) { p.nombre = v; return this; }
            public Builder apellido(String v) { p.apellido = v; return this; }
            public Builder dni(String v) { p.dni = v; return this; }
            public Builder email(String v) { p.email = v; return this; }
            public Builder telefono(String v) { p.telefono = v; return this; }
            public PacienteSimpleDto build() { return p; }
        }
    }
}
