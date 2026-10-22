package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.HistoriaClinicaActualizadaData;
import com.tranqui.notificaciones.contacto.Contacto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Avisos en la app de la historia clínica (ClinicalService del monolito). El evento no trae
 * contenido clínico: solo qué se cargó y quién.
 */
@Configuration
public class ManejadoresClinicos {

    @Bean
    ManejadorEvento<HistoriaClinicaActualizadaData> notifHistoriaClinica(Avisos avisos) {
        return ManejadorEvento.de("notif-historia-clinica", Topicos.PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1,
                HistoriaClinicaActualizadaData.class, e -> {
                    HistoriaClinicaActualizadaData d = e.data();
                    Contacto medico = avisos.contacto(d.profesionalId());
                    Contacto paciente = avisos.contacto(d.pacienteId());
                    if ("INFORME".equals(d.tipo())) {
                        avisos.notificar(medico, "Informe clínico creado", "Se creó un nuevo informe de tipo "
                                + d.tipoInforme() + " para " + paciente.getNombre(), "INFORME");
                        avisos.notificar(paciente, "Nuevo Informe Clínico disponible", "El profesional "
                                + medico.getNombre() + " ha emitido un informe de tipo " + d.tipoInforme() + ".", "INFORME");
                    } else if ("PACIENTE".equals(d.cargadoPor())) {
                        avisos.notificar(medico, "Seguimiento cargado por paciente",
                                paciente.getNombre() + " registró su seguimiento clínico diario.", "SEGUIMIENTO");
                    } else {
                        avisos.notificar(medico, "Seguimiento Diario cargado",
                                "Se registró un nuevo seguimiento diario para " + paciente.getNombre(), "SEGUIMIENTO");
                    }
                });
    }
}
