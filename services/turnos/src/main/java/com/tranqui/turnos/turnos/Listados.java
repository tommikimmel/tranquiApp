package com.tranqui.turnos.turnos;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.web.Dtos;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** "Mis turnos" del profesional (hoy y todos) y del paciente, con el mismo JSON que el monolito. */
@Service
public class Listados {

    private final Turnos turnos;
    private final Personas personas;
    private final Profesionales profesionales;

    public Listados(Turnos turnos, Personas personas, Profesionales profesionales) {
        this.turnos = turnos;
        this.personas = personas;
        this.profesionales = profesionales;
    }

    private Personas.Persona persona(String email, String siNoEsta) {
        return personas.porEmail(email).orElseThrow(() -> new NoEncontradoException(siNoEsta));
    }

    public List<Dtos.TurnoMedicoDto> deHoy(String emailMedico) {
        Personas.Persona medico = persona(emailMedico, "Médico no encontrado");
        LocalDate hoy = LocalDate.now();
        List<Turno> lista = turnos.delDia(medico.id(), hoy).stream()
                .filter(t -> Turno.CONFIRMADO.equals(t.estado) || Turno.PENDIENTE_VALIDACION.equals(t.estado))
                .sorted(Comparator.comparing(t -> t.horaInicio))
                .toList();
        return paraProfesional(lista, t -> t.ocupaAgenda && Turno.CONFIRMADO.equals(t.estado) && t.horaFin.isBefore(LocalTime.now()));
    }

    public List<Dtos.TurnoMedicoDto> todos(String emailMedico) {
        Personas.Persona medico = persona(emailMedico, "Médico no encontrado");
        List<Turno> lista = turnos.deProfesional(medico.id()).stream()
                .filter(t -> Turno.CONFIRMADO.equals(t.estado) || Turno.PENDIENTE_VALIDACION.equals(t.estado))
                .sorted(Comparator.comparing((Turno t) -> t.fecha).thenComparing(t -> t.horaInicio))
                .toList();
        return paraProfesional(lista, Listados::completado);
    }

    public List<Dtos.TurnoMedicoDto> dePaciente(String emailPaciente) {
        Personas.Persona paciente = persona(emailPaciente, "Paciente no encontrado");
        List<Turno> lista = turnos.dePaciente(paciente.id()).stream()
                .sorted(Comparator.comparing((Turno t) -> t.fecha).thenComparing(t -> t.horaInicio))
                .toList();
        Map<Long, Personas.Persona> medicos = personas.porIds(lista.stream().map(t -> t.medicoId).collect(Collectors.toSet()));
        return lista.stream().map(t -> {
            Personas.Persona medico = medicos.get(t.medicoId);
            Profesionales.Agenda agenda = profesionales.porId(t.medicoId).orElse(null);
            return new Dtos.TurnoMedicoDto(t.id, medico != null ? medico.nombre() : null, hora(t), horaMinutos(t), "hs", Etiquetas.tipo(t),
                    t.modalidad, estado(t, completado(t)), asistencia(t), t.telemedicinaUrl != null ? t.telemedicinaUrl : "",
                    t.fecha.toString(), t.checkoutUrl != null ? t.checkoutUrl : "", false, null,
                    agenda != null ? agenda.domicilioAtencion() : null, agenda != null ? agenda.domicilioLat() : null,
                    agenda != null ? agenda.domicilioLng() : null, agenda != null ? agenda.domicilioAtencionTorre() : null,
                    agenda != null ? agenda.domicilioAtencionPiso() : null, agenda != null ? agenda.domicilioAtencionDepto() : null,
                    agenda != null ? agenda.domicilioAtencionBarrio() : null, null, t.ocupaAgenda, t.documentoEnviado, t.esDeReceta());
        }).toList();
    }

    /**
     * Un documento (no ocupa agenda) tiene fecha y hora de la compra: nunca pasa a "completed", queda
     * "confirmed" hasta que se manda. Los turnos de agenda confirmados pasan a "completed" cuando ya pasaron.
     */
    private static boolean completado(Turno t) {
        if (!t.ocupaAgenda || !Turno.CONFIRMADO.equals(t.estado)) {
            return false;
        }
        LocalDate hoy = LocalDate.now();
        return (t.fecha.isEqual(hoy) && t.horaFin.isBefore(LocalTime.now())) || t.fecha.isBefore(hoy);
    }

    private List<Dtos.TurnoMedicoDto> paraProfesional(List<Turno> lista, java.util.function.Predicate<Turno> completado) {
        Map<Long, Personas.Persona> pacientes = personas.porIds(lista.stream().map(t -> t.pacienteId).collect(Collectors.toSet()));
        // "Primera consulta": el mail del paciente no tiene ningún turno no cancelado (como esPrimeraConsulta).
        Set<String> emails = lista.stream().map(t -> pacientes.get(t.pacienteId)).filter(p -> p != null && p.email() != null)
                .map(Personas.Persona::email).collect(Collectors.toSet());
        Set<String> conTurno = new HashSet<>(turnos.emailsConTurnoNoCancelado(emails));
        return lista.stream().map(t -> {
            Personas.Persona p = pacientes.get(t.pacienteId);
            return new Dtos.TurnoMedicoDto(t.id, p != null ? p.nombre() : null, hora(t), horaMinutos(t), "hs", Etiquetas.tipo(t),
                    t.modalidad, estado(t, completado.test(t)), asistencia(t), t.telemedicinaUrl != null ? t.telemedicinaUrl : "",
                    t.fecha.toString(), null, p == null || !conTurno.contains(p.email()), paciente(p), null, null, null, null, null, null,
                    null, t.metadataAfiliado, t.ocupaAgenda, t.documentoEnviado, t.esDeReceta());
        }).toList();
    }

    private static String estado(Turno t, boolean completado) {
        if (completado) {
            return "completed";
        }
        return Turno.CONFIRMADO.equals(t.estado) ? "confirmed" : "pending";
    }

    private static String asistencia(Turno t) {
        return t.asistencia != null ? t.asistencia : "ESPERANDO";
    }

    private static String hora(Turno t) {
        return String.format("%02d", t.horaInicio.getHour());
    }

    private static String horaMinutos(Turno t) {
        return String.format("%02d:%02d", t.horaInicio.getHour(), t.horaInicio.getMinute());
    }

    /** {@code TurnoService#construirPacienteDto}. */
    static Dtos.PacienteDto paciente(Personas.Persona p) {
        if (p == null) {
            return null;
        }
        Dtos.CredencialInfoDto credencial = p.credencialCodEntidad() != null || p.credencialPan() != null
                ? new Dtos.CredencialInfoDto(p.credencialCodEntidad(), p.credencialPan(), p.credencialPlan(), p.credencialToken())
                : null;
        return new Dtos.PacienteDto(p.id(), p.nombre(), p.email(), p.telefono(), p.dni(), p.direccion(), p.obraSocial(),
                p.numAfiliado(), null, null, false, 0, p.apellido(), p.sexo(),
                p.fechaNacimiento() != null ? p.fechaNacimiento().toString() : null, p.cuil(), p.email(), p.tipoDocumento(),
                p.numeroDocumento(), p.datosOfuscado() != null ? p.datosOfuscado() : "N", credencial, null);
    }
}
