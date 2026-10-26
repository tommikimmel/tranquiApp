package com.tranqui.profesionales.recetas;

import java.util.List;

public class RecetaDto {
    private Long pacienteId;
    private List<MedicamentoDto> medications;
    private String diagnosis;
    private String notes;

    public RecetaDto() {}

    public RecetaDto(Long pacienteId, List<MedicamentoDto> medications, String diagnosis, String notes) {
        this.pacienteId = pacienteId;
        this.medications = medications;
        this.diagnosis = diagnosis;
        this.notes = notes;
    }

    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }
    public List<MedicamentoDto> getMedications() { return medications; }
    public void setMedications(List<MedicamentoDto> medications) { this.medications = medications; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long pacienteId;
        private List<MedicamentoDto> medications;
        private String diagnosis;
        private String notes;

        public Builder pacienteId(Long v) { this.pacienteId = v; return this; }
        public Builder medications(List<MedicamentoDto> v) { this.medications = v; return this; }
        public Builder diagnosis(String v) { this.diagnosis = v; return this; }
        public Builder notes(String v) { this.notes = v; return this; }
        public RecetaDto build() { return new RecetaDto(pacienteId, medications, diagnosis, notes); }
    }

    public static class MedicamentoDto {
        private String name;
        private String dosage;
        private String frequency;
        private String duration;
        private String regNo;
        private String nombreDroga;
        private Boolean noSustituible;
        private String laboratorio;

        public MedicamentoDto() {}

        public MedicamentoDto(String name, String dosage, String frequency, String duration, String regNo,
                              String nombreDroga, Boolean noSustituible, String laboratorio) {
            this.name = name;
            this.dosage = dosage;
            this.frequency = frequency;
            this.duration = duration;
            this.regNo = regNo;
            this.nombreDroga = nombreDroga;
            this.noSustituible = noSustituible;
            this.laboratorio = laboratorio;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDosage() { return dosage; }
        public void setDosage(String dosage) { this.dosage = dosage; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
        public String getRegNo() { return regNo; }
        public void setRegNo(String regNo) { this.regNo = regNo; }
        public String getNombreDroga() { return nombreDroga; }
        public void setNombreDroga(String nombreDroga) { this.nombreDroga = nombreDroga; }
        public Boolean getNoSustituible() { return noSustituible; }
        public void setNoSustituible(Boolean noSustituible) { this.noSustituible = noSustituible; }
        public String getLaboratorio() { return laboratorio; }
        public void setLaboratorio(String laboratorio) { this.laboratorio = laboratorio; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final MedicamentoDto m = new MedicamentoDto();
            public Builder name(String v) { m.name = v; return this; }
            public Builder dosage(String v) { m.dosage = v; return this; }
            public Builder frequency(String v) { m.frequency = v; return this; }
            public Builder duration(String v) { m.duration = v; return this; }
            public Builder regNo(String v) { m.regNo = v; return this; }
            public Builder nombreDroga(String v) { m.nombreDroga = v; return this; }
            public Builder noSustituible(Boolean v) { m.noSustituible = v; return this; }
            public Builder laboratorio(String v) { m.laboratorio = v; return this; }
            public MedicamentoDto build() { return m; }
        }
    }
}
