package com.tranqui.profesionales.clinica;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.HistoriaClinicaActualizadaData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import com.tranqui.profesionales.datos.TurnosAtendidos.TurnoAtendido;
import com.tranqui.profesionales.interno.ClienteChat;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.perfil.NoEncontradoException;
import com.tranqui.profesionales.perfil.ServicioNoDisponibleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lógica de negocio de la historia clínica, ficha y lista de pacientes (Fase 7, H2b).
 * Inalterabilidad bajo Leyes 26.529 y 27.706: hash SHA-256 por asiento, nunca se borra (soft-delete),
 * y las ediciones crean un nuevo anexo vinculado a informeOriginalId.
 */
@Service
public class HistoriaClinicaService {

    private static final String ESTADO_VIGENTE = "VIGENTE";
    private static final String ESTADO_VIGENTE_CORREGIDO = "VIGENTE_CORREGIDO";
    private static final String ESTADO_ANULADO = "ANULADO";
    private static final String ESTADO_ANEXO_CORRECCION = "ANEXO_CORRECCION";

    private final Personas personas;
    private final TurnosAtendidos turnosAtendidos;
    private final Seguimientos seguimientos;
    private final Informes informes;
    private final ClienteChat clienteChat;
    private final ClienteInterno clienteInterno;
    private final AccesoPacientes accesoPacientes;
    private final OutboxPublisher outbox;

    public HistoriaClinicaService(Personas personas,
                                  TurnosAtendidos turnosAtendidos,
                                  Seguimientos seguimientos,
                                  Informes informes,
                                  ClienteChat clienteChat,
                                  ClienteInterno clienteInterno,
                                  AccesoPacientes accesoPacientes,
                                  OutboxPublisher outbox) {
        this.personas = personas;
        this.turnosAtendidos = turnosAtendidos;
        this.seguimientos = seguimientos;
        this.informes = informes;
        this.clienteChat = clienteChat;
        this.clienteInterno = clienteInterno;
        this.accesoPacientes = accesoPacientes;
        this.outbox = outbox;
    }

    private void publicarHistoriaClinica(String tipoAgregado, Long asientoId, Long profesionalId, Long pacienteId,
                                         String tipo, String cargadoPor, String tipoInforme) {
        outbox.publicar(
                Topicos.PROFESIONALES_HISTORIA_CLINICA_ACTUALIZADA_V1,
                tipoAgregado,
                String.valueOf(asientoId),
                1,
                new HistoriaClinicaActualizadaData(profesionalId, pacienteId, tipo, cargadoPor, tipoInforme)
        );
    }

    private String calcularHash(String... partes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String contenido = String.join("|", Arrays.stream(partes)
                    .map(p -> p == null ? "" : p)
                    .toArray(String[]::new));
            byte[] hash = digest.digest(contenido.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String nombreMedico(Persona medico) {
        if (medico == null) {
            return null;
        }
        return medico.nombre() + (medico.apellido() != null ? " " + medico.apellido() : "");
    }

    private Dtos.PacienteDto construirPacienteDto(Persona p, String ultimaVisita, boolean sinTurno, boolean highPriority, int unreadMessagesCount) {
        Dtos.CredencialInfoDto cred = null;
        if (p.credencialCodEntidad() != null || p.credencialPan() != null) {
            cred = new Dtos.CredencialInfoDto(
                    p.credencialCodEntidad(),
                    p.credencialPan(),
                    p.credencialPlan(),
                    p.credencialToken()
            );
        }

        return new Dtos.PacienteDto(
                p.id(),
                p.nombre(),
                p.email(),
                p.telefono(),
                p.dni(),
                p.direccion(),
                p.obraSocial(),
                p.numAfiliado(),
                ultimaVisita,
                highPriority ? "PRIORIDAD_ALTA" : "PRIORIDAD_BAJA",
                sinTurno,
                unreadMessagesCount,
                p.apellido(),
                p.sexo(),
                p.fechaNacimiento() != null ? p.fechaNacimiento().toString() : null,
                p.cuil(),
                p.email(),
                p.tipoDocumento(),
                p.numeroDocumento(),
                p.datosOfuscado() != null ? p.datosOfuscado() : "N",
                cred,
                new Dtos.DomicilioDto(
                        p.domicilioCalle(),
                        p.domicilioNumero(),
                        p.domicilioPiso(),
                        p.domicilioDpto(),
                        p.domicilioCodigoPostal(),
                        p.domicilioLocalidad(),
                        p.domicilioProvincia(),
                        p.domicilioPais()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<Dtos.PacienteDto> obtenerPacientesAtendidos(String medicoEmail) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        List<Long> pacientesConTurnoIds = turnosAtendidos.pacientesDistintos(medico.id());
        ClienteChat.Resumen resumenChat = clienteChat.resumen(medico.id());
        List<Long> pacientesConChatIds = resumenChat.pacientesConChat();

        Map<Long, Persona> personasTurno = personas.porIds(pacientesConTurnoIds);
        Map<Long, Persona> personasChat = personas.porIds(pacientesConChatIds);

        Map<Long, Persona> pacientesMap = new LinkedHashMap<>();
        for (Long id : pacientesConTurnoIds) {
            Persona p = personasTurno.get(id);
            if (p != null) {
                pacientesMap.put(p.id(), p);
            }
        }
        for (Long id : pacientesConChatIds) {
            Persona p = personasChat.get(id);
            if (p != null && "PACIENTE".equals(p.rol())) {
                pacientesMap.put(p.id(), p);
            }
        }

        List<Persona> todosLosPacientes = new ArrayList<>(pacientesMap.values());
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(3);

        List<TurnoAtendido> todosLosTurnosDelMedico = turnosAtendidos.turnosNoCancelados(medico.id());
        Map<Long, List<TurnoAtendido>> turnosPorPaciente = todosLosTurnosDelMedico.stream()
                .collect(Collectors.groupingBy(TurnoAtendido::pacienteId));

        Map<Long, Integer> mensajesSinLeerPorPaciente = resumenChat.noLeidosPorPaciente();

        List<Dtos.PacienteDto> dtos = new ArrayList<>();
        for (Persona p : todosLosPacientes) {
            List<TurnoAtendido> patientTurnos = turnosPorPaciente.getOrDefault(p.id(), Collections.emptyList());

            boolean highPriority = patientTurnos.stream()
                    .anyMatch(t -> "CONFIRMADO".equals(t.estado())
                            && t.fecha() != null
                            && !t.fecha().isBefore(hoy)
                            && !t.fecha().isAfter(limite));

            String ultimaVisita = patientTurnos.stream()
                    .filter(t -> t.fecha() != null && (t.fecha().isBefore(hoy) || t.fecha().isEqual(hoy)))
                    .map(t -> t.fecha().toString())
                    .max(String::compareTo)
                    .orElse("Ninguna");

            int unreadMessagesCount = mensajesSinLeerPorPaciente.getOrDefault(p.id(), 0);
            boolean sinTurno = patientTurnos.isEmpty();
            dtos.add(construirPacienteDto(p, sinTurno ? "Sin turnos registrados" : ultimaVisita, sinTurno, highPriority, unreadMessagesCount));
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<Dtos.SeguimientoResponse> obtenerSeguimientos(Long pacienteId, String medicoEmail) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        String nombreMed = nombreMedico(medico);
        return seguimientos.porPacienteYMedico(pacienteId, medico.id()).stream()
                .map(s -> new Dtos.SeguimientoResponse(s.id(), s.fecha(), s.estadoAnimo(), s.sintomas(), s.notas(),
                        s.fechaCreacion(), s.estado(), s.motivo(), s.hashIntegridad(), nombreMed))
                .toList();
    }

    @Transactional
    public Dtos.SeguimientoResponse guardarSeguimiento(Long pacienteId, String medicoEmail, Dtos.SeguimientoRequest entry) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        Persona paciente = personas.porId(pacienteId)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        accesoPacientes.exigirAtendido(medico.id(), paciente.id());

        LocalDate fecha = entry.fecha() != null ? entry.fecha() : LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();
        String estado = ESTADO_VIGENTE;
        String hash = calcularHash(medico.email(), String.valueOf(paciente.id()),
                entry.estadoAnimo(), entry.sintomas(), entry.notas(), ahora.toString());

        Long id = seguimientos.insertarNuevo(new Seguimientos.Seguimiento(
                null, paciente.id(), medico.id(), fecha, entry.estadoAnimo(), entry.sintomas(), entry.notas(),
                ahora, estado, null, hash));

        publicarHistoriaClinica("seguimiento", id, medico.id(), paciente.id(), "SEGUIMIENTO", "PROFESIONAL", null);

        return new Dtos.SeguimientoResponse(id, fecha, entry.estadoAnimo(), entry.sintomas(), entry.notas(), ahora, estado, null, hash, nombreMedico(medico));
    }

    @Transactional(readOnly = true)
    public List<Dtos.InformeClinicoResponse> obtenerInformes(Long pacienteId, String medicoEmail) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        String nombreMed = nombreMedico(medico);
        return informes.porPacienteYMedico(pacienteId, medico.id()).stream()
                .map(inf -> new Dtos.InformeClinicoResponse(inf.id(), inf.fecha(), inf.tipoInforme(), inf.planTrabajo(),
                        inf.contenido(), inf.nombreArchivo(), inf.fechaCreacion(), inf.estado(), inf.informeOriginalId(),
                        inf.motivo(), inf.hashIntegridad(), nombreMed))
                .toList();
    }

    @Transactional
    public Dtos.InformeClinicoResponse guardarInforme(String medicoEmail, Long pacienteId, Dtos.InformeClinicoRequest dto) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        Persona paciente = personas.porId(pacienteId)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        accesoPacientes.exigirAtendido(medico.id(), paciente.id());

        LocalDate fecha = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();
        String estado = ESTADO_VIGENTE;
        String hash = calcularHash(medico.email(), String.valueOf(paciente.id()),
                dto.tipoInforme(), dto.planTrabajo(), dto.contenido(), ahora.toString());

        Long id = informes.insertarNuevo(new Informes.Informe(
                null, paciente.id(), medico.id(), fecha, dto.tipoInforme(), dto.planTrabajo(), dto.contenido(),
                dto.nombreArchivo(), ahora, estado, null, null, hash));

        publicarHistoriaClinica("informe", id, medico.id(), paciente.id(), "INFORME", "PROFESIONAL", dto.tipoInforme());

        return new Dtos.InformeClinicoResponse(id, fecha, dto.tipoInforme(), dto.planTrabajo(), dto.contenido(),
                dto.nombreArchivo(), ahora, estado, null, null, hash, nombreMedico(medico));
    }

    @Transactional(readOnly = true)
    public List<Dtos.SeguimientoResponse> obtenerSeguimientosPaciente(String email) {
        Persona paciente = personas.porEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        List<Seguimientos.Seguimiento> lista = seguimientos.porPaciente(paciente.id());
        Map<Long, Persona> medicosMap = personas.porIds(lista.stream().map(Seguimientos.Seguimiento::medicoId).toList());
        return lista.stream()
                .map(s -> new Dtos.SeguimientoResponse(s.id(), s.fecha(), s.estadoAnimo(), s.sintomas(), s.notas(),
                        s.fechaCreacion(), s.estado(), s.motivo(), s.hashIntegridad(), nombreMedico(medicosMap.get(s.medicoId()))))
                .toList();
    }

    @Transactional
    public Dtos.SeguimientoResponse guardarSeguimientoPaciente(String email, Dtos.SeguimientoRequest entry) {
        personas.porEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));

        // En el monolito el profesional nunca llega en el JSON (@JsonIgnore en SeguimientoDiario.medico);
        // la decisión 2 de la spec rechaza en vez de asignarlo a uno fijo.
        throw new IllegalArgumentException("El seguimiento tiene que ser para un profesional que te atiende.");
    }

    @Transactional(readOnly = true)
    public List<Dtos.InformeClinicoResponse> obtenerInformesPaciente(String email) {
        Persona paciente = personas.porEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        List<Informes.Informe> lista = informes.porPaciente(paciente.id());
        Map<Long, Persona> medicosMap = personas.porIds(lista.stream().map(Informes.Informe::medicoId).toList());
        return lista.stream()
                .map(inf -> new Dtos.InformeClinicoResponse(inf.id(), inf.fecha(), inf.tipoInforme(), inf.planTrabajo(),
                        inf.contenido(), inf.nombreArchivo(), inf.fechaCreacion(), inf.estado(), inf.informeOriginalId(),
                        inf.motivo(), inf.hashIntegridad(), nombreMedico(medicosMap.get(inf.medicoId()))))
                .toList();
    }

    @Transactional
    public Dtos.PacienteDto actualizarPaciente(Long pacienteId, String medicoEmail, Dtos.PacienteDto dto) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        Persona paciente = personas.porId(pacienteId)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        accesoPacientes.exigirAtendido(medico.id(), paciente.id());

        ClienteInterno.PacienteMonolito monolito;
        try {
            monolito = clienteInterno.actualizarPaciente(pacienteId, dto);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400) {
                throw new IllegalArgumentException(e.getResponseBodyAsString());
            }
            throw new ServicioNoDisponibleException("No se pudo actualizar el paciente en este momento. Probá de nuevo en unos minutos.", e);
        } catch (RestClientException e) {
            throw new ServicioNoDisponibleException("No se pudo actualizar el paciente en este momento. Probá de nuevo en unos minutos.", e);
        }

        personas.guardar(monolito.persona());
        int unread = clienteChat.resumen(medico.id()).noLeidosPorPaciente().getOrDefault(pacienteId, 0);
        return construirPacienteDto(monolito.persona(), "Hoy", false, false, unread);
    }

    @Transactional
    public void eliminarInforme(Long id, String medicoEmail, String motivo) {
        Informes.Informe informe = informes.porId(id)
                .orElseThrow(() -> new NoEncontradoException("Informe no encontrado"));
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        if (!informe.medicoId().equals(medico.id())) {
            throw new ProhibidoException("No tiene permisos para eliminar este informe");
        }

        informes.actualizarEstadoYMotivo(id, ESTADO_ANULADO, motivo != null ? motivo : "No especificado", informe.hashIntegridad());
    }

    @Transactional
    public Dtos.InformeClinicoResponse editarInforme(Long id, String medicoEmail, Dtos.InformeClinicoRequest dto) {
        Informes.Informe original = informes.porId(id)
                .orElseThrow(() -> new NoEncontradoException("Informe no encontrado"));
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        if (!original.medicoId().equals(medico.id())) {
            throw new ProhibidoException("No tiene permisos para editar este informe");
        }
        if (ESTADO_ANULADO.equals(original.estado())) {
            throw new IllegalStateException("No se puede corregir un informe anulado");
        }

        informes.actualizarEstadoYMotivo(id, ESTADO_VIGENTE_CORREGIDO, original.motivo(), original.hashIntegridad());

        LocalDateTime ahora = LocalDateTime.now();
        String hash = calcularHash(medico.email(), String.valueOf(original.pacienteId()),
                dto.tipoInforme(), dto.planTrabajo(), dto.contenido(), ahora.toString());

        Long anexoId = informes.insertarNuevo(new Informes.Informe(
                null, original.pacienteId(), original.medicoId(), LocalDate.now(), dto.tipoInforme(), dto.planTrabajo(),
                dto.contenido(), original.nombreArchivo(), ahora, ESTADO_ANEXO_CORRECCION, original.id(), dto.motivo(), hash));

        return new Dtos.InformeClinicoResponse(anexoId, LocalDate.now(), dto.tipoInforme(), dto.planTrabajo(), dto.contenido(),
                original.nombreArchivo(), ahora, ESTADO_ANEXO_CORRECCION, original.id(), dto.motivo(), hash, nombreMedico(medico));
    }

    @Transactional
    public void eliminarSeguimiento(Long id, String medicoEmail, String motivo) {
        Seguimientos.Seguimiento seguimiento = seguimientos.porId(id)
                .orElseThrow(() -> new NoEncontradoException("Seguimiento no encontrado"));
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        if (!seguimiento.medicoId().equals(medico.id())) {
            throw new ProhibidoException("No tiene permisos para eliminar este seguimiento");
        }

        seguimientos.actualizarEstadoYMotivo(id, ESTADO_ANULADO, motivo != null ? motivo : "No especificado", seguimiento.hashIntegridad());
    }
}
