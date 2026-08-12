package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClinicalService {

    private static final String ESTADO_VIGENTE = "VIGENTE";
    private static final String ESTADO_VIGENTE_CORREGIDO = "VIGENTE_CORREGIDO";
    private static final String ESTADO_ANULADO = "ANULADO";
    private static final String ESTADO_ANEXO_CORRECCION = "ANEXO_CORRECCION";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private SeguimientoDiarioRepository seguimientoDiarioRepository;

    @Autowired
    private InformeClinicoRepository informeClinicoRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    // Sella la integridad de un asiento clínico en el momento de su creación (o de su anexo de
    // corrección) — no es una firma digital en el sentido de la Ley 25.506 (eso requeriría un
    // certificador licenciado), pero permite detectar si el contenido persistido fue alterado
    // por fuera de los caminos de la aplicación, sosteniendo valor probatorio bajo Ley 27.706.
    private String calcularHash(String... partes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String contenido = String.join("|", java.util.Arrays.stream(partes)
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

    @Transactional(readOnly = true)
    public List<PacienteDto> obtenerPacientesAtendidos(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Usuario> pacientesConTurno = turnoRepository.findDistinctPacientesByMedicoId(medico.getId());
        List<Usuario> pacientesConChat = mensajeRepository.findPacientesConMensajesConMedico(medico.getId());

        java.util.Map<Long, Usuario> pacientesMap = new java.util.LinkedHashMap<>();
        for (Usuario p : pacientesConTurno) {
            pacientesMap.put(p.getId(), p);
        }
        for (Usuario p : pacientesConChat) {
            pacientesMap.put(p.getId(), p);
        }

        java.util.List<Usuario> todosLosPacientes = new java.util.ArrayList<>(pacientesMap.values());

        // For priority checking, check if there are confirmed appointments in the next 3 days
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(3);

        // Both of these used to run once PER PATIENT inside the loop below (identical turnos
        // query every time, one COUNT query per patient) — fetched once here and grouped in
        // memory instead, same pattern already used by TurnoService.emailsConTurnoNoCancelado.
        List<Turno> todosLosTurnosDelMedico = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);
        java.util.Map<Long, List<Turno>> turnosPorPaciente = todosLosTurnosDelMedico.stream()
                .collect(Collectors.groupingBy(t -> t.getPaciente().getId()));

        java.util.Map<Long, Integer> mensajesSinLeerPorPaciente = mensajeRepository.countUnreadMessagesGroupedByRemitente(medico.getId()).stream()
                .collect(Collectors.toMap(
                        com.tranqui.app.repository.MensajeRepository.UnreadCountPorRemitente::getRemitenteId,
                        com.tranqui.app.repository.MensajeRepository.UnreadCountPorRemitente::getCantidad));

        List<PacienteDto> dtos = new ArrayList<>();
        for (Usuario p : todosLosPacientes) {
            List<Turno> patientTurnos = turnosPorPaciente.getOrDefault(p.getId(), java.util.Collections.emptyList());

            // Determine if clinical priority is high
            boolean highPriority = patientTurnos.stream()
                    .anyMatch(t -> t.getEstado() == EstadoTurno.CONFIRMADO &&
                                   !t.getFecha().isBefore(hoy) &&
                                   !t.getFecha().isAfter(limite));

            // Find last visit date
            String ultimaVisita = patientTurnos.stream()
                    .filter(t -> t.getFecha().isBefore(hoy) || t.getFecha().isEqual(hoy))
                    .map(t -> t.getFecha().toString())
                    .max(String::compareTo)
                    .orElse("Ninguna");

            int unreadMessagesCount = mensajesSinLeerPorPaciente.getOrDefault(p.getId(), 0);
            boolean sinTurno = patientTurnos.isEmpty();
            dtos.add(construirPacienteDto(p, sinTurno ? "Sin turnos registrados" : ultimaVisita, sinTurno, highPriority, unreadMessagesCount));
        }

        return dtos;
    }

    private PacienteDto construirPacienteDto(Usuario p, String ultimaVisita, boolean sinTurno, boolean highPriority, int unreadMessagesCount) {
        PacienteDto.CredencialInfoDto cred = null;
        if (p.getCredencialCodEntidad() != null || p.getCredencialPan() != null) {
            cred = PacienteDto.CredencialInfoDto.builder()
                    .codEntidad(p.getCredencialCodEntidad())
                    .pan(p.getCredencialPan())
                    .plan(p.getCredencialPlan())
                    .token(p.getCredencialToken())
                    .build();
        }

        return PacienteDto.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .email(p.getEmail())
                .telefono(p.getTelefono())
                .dni(p.getDni())
                .direccion(p.getDireccion())
                .obraSocial(p.getObraSocial())
                .numAfiliado(p.getNumAfiliado())
                .ultimaVisita(ultimaVisita)
                .prioridadClinica(highPriority ? "PRIORIDAD_ALTA" : "PRIORIDAD_BAJA")
                .sinTurno(sinTurno)
                .unreadMessagesCount(unreadMessagesCount)
                .apellido(p.getApellido())
                .sexo(p.getSexo())
                .fechaNacimiento(p.getFechaNacimiento() != null ? p.getFechaNacimiento().toString() : null)
                .cuil(p.getCuil())
                .mail(p.getEmail())
                .tipoDocumento(p.getTipoDocumento())
                .numeroDocumento(p.getNumeroDocumento())
                .datosOfuscado(p.getDatosOfuscado() != null ? p.getDatosOfuscado() : "N")
                .credencial(cred)
                .domicilio(PacienteDto.DomicilioDto.builder()
                        .calle(p.getDomicilioCalle())
                        .numero(p.getDomicilioNumero())
                        .piso(p.getDomicilioPiso())
                        .dpto(p.getDomicilioDpto())
                        .codigoPostal(p.getDomicilioCodigoPostal())
                        .localidad(p.getDomicilioLocalidad())
                        .provincia(p.getDomicilioProvincia())
                        .pais(p.getDomicilioPais())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SeguimientoDiario> obtenerSeguimientos(Long pacienteId, String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        return seguimientoDiarioRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(pacienteId, medico.getId());
    }

    @Transactional
    public SeguimientoDiario guardarSeguimiento(Long pacienteId, String medicoEmail, SeguimientoDiario entry) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        entry.setMedico(medico);
        entry.setPaciente(paciente);
        if (entry.getFecha() == null) {
            entry.setFecha(LocalDate.now());
        }
        LocalDateTime ahora = LocalDateTime.now();
        entry.setFechaCreacion(ahora);
        entry.setEstado(ESTADO_VIGENTE);
        entry.setHashIntegridad(calcularHash(medico.getEmail(), String.valueOf(paciente.getId()),
                entry.getEstadoAnimo(), entry.getSintomas(), entry.getNotas(), ahora.toString()));

        SeguimientoDiario saved = seguimientoDiarioRepository.save(entry);

        // Notify professional/patient (in this case, since the professional logged it or the patient logged it, we can notify the user)
        notificacionService.crearNotificacion(
                medico, 
                "Seguimiento Diario cargado", 
                "Se registró un nuevo seguimiento diario para " + paciente.getNombre(), 
                "SEGUIMIENTO"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<InformeClinico> obtenerInformes(Long pacienteId, String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        return informeClinicoRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(pacienteId, medico.getId());
    }

    @Transactional
    public InformeClinico guardarInforme(String medicoEmail, Long pacienteId, String tipoInforme, String planTrabajo, String contenido, String nombreArchivo) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        LocalDateTime ahora = LocalDateTime.now();
        InformeClinico informe = InformeClinico.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now())
                .fechaCreacion(ahora)
                .tipoInforme(tipoInforme)
                .planTrabajo(planTrabajo)
                .contenido(contenido)
                .nombreArchivo(nombreArchivo)
                .estado(ESTADO_VIGENTE)
                .hashIntegridad(calcularHash(medico.getEmail(), String.valueOf(paciente.getId()),
                        tipoInforme, planTrabajo, contenido, ahora.toString()))
                .build();

        InformeClinico saved = informeClinicoRepository.save(informe);

        notificacionService.crearNotificacion(
                medico,
                "Informe clínico creado",
                "Se creó un nuevo informe de tipo " + tipoInforme + " para " + paciente.getNombre(),
                "INFORME"
        );

        notificacionService.crearNotificacion(
                paciente,
                "Nuevo Informe Clínico disponible",
                "El profesional " + medico.getNombre() + " ha emitido un informe de tipo " + tipoInforme + ".",
                "INFORME"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SeguimientoDiario> obtenerSeguimientosPaciente(String email) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));
        return seguimientoDiarioRepository.findByPacienteIdOrderByFechaDesc(paciente.getId());
    }

    @Transactional
    public SeguimientoDiario guardarSeguimientoPaciente(String email, SeguimientoDiario entry) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        if (entry.getMedico() == null) {
            Usuario medico = usuarioRepository.findByEmail("paula@tranqui.com")
                    .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
            entry.setMedico(medico);
        }
        
        entry.setPaciente(paciente);
        if (entry.getFecha() == null) {
            entry.setFecha(LocalDate.now());
        }
        LocalDateTime ahora = LocalDateTime.now();
        entry.setFechaCreacion(ahora);
        entry.setEstado(ESTADO_VIGENTE);
        entry.setHashIntegridad(calcularHash(entry.getMedico().getEmail(), String.valueOf(paciente.getId()),
                entry.getEstadoAnimo(), entry.getSintomas(), entry.getNotas(), ahora.toString()));

        SeguimientoDiario saved = seguimientoDiarioRepository.save(entry);

        notificacionService.crearNotificacion(
                entry.getMedico(),
                "Seguimiento cargado por paciente",
                paciente.getNombre() + " registró su seguimiento clínico diario.",
                "SEGUIMIENTO"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<InformeClinico> obtenerInformesPaciente(String email) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));
        return informeClinicoRepository.findByPacienteIdOrderByFechaDesc(paciente.getId());
    }

    private static final java.util.Set<String> TIPOS_DOCUMENTO_VALIDOS = java.util.Set.of("DNI", "LC", "LE", "Pasaporte");

    // Same manual accumulate-and-throw convention as RecetaService.emitirReceta's
    // datosFaltantes check: collect every problem instead of failing on the first one, so a
    // psiquiatra correcting a form sees all the issues at once instead of one per submit.
    // Fields are only validated when present — this endpoint doubles as the "borrar datos
    // cargados" action (Historia Clínica), which intentionally sends most fields as null.
    private void validarDatosPaciente(PacienteDto dto) {
        List<String> errores = new ArrayList<>();

        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            errores.add("el nombre del paciente es obligatorio");
        }

        if (dto.getTipoDocumento() != null && !dto.getTipoDocumento().trim().isEmpty()
                && !TIPOS_DOCUMENTO_VALIDOS.contains(dto.getTipoDocumento().trim())) {
            errores.add("el tipo de documento debe ser DNI, LC, LE o Pasaporte");
        }

        if (dto.getNumeroDocumento() != null) {
            int digits = String.valueOf(Math.abs(dto.getNumeroDocumento())).length();
            if (dto.getNumeroDocumento() <= 0 || digits < 6 || digits > 9) {
                errores.add("el número de documento debe tener entre 6 y 9 dígitos");
            }
        }

        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().trim().isEmpty()) {
            try {
                LocalDate fecha = LocalDate.parse(dto.getFechaNacimiento().trim());
                if (fecha.isAfter(LocalDate.now())) {
                    errores.add("la fecha de nacimiento no puede ser futura");
                } else if (fecha.isBefore(LocalDate.of(1900, 1, 1))) {
                    errores.add("la fecha de nacimiento no es válida");
                }
            } catch (java.time.format.DateTimeParseException e) {
                errores.add("la fecha de nacimiento no tiene un formato válido");
            }
        }

        if (dto.getTelefono() != null && !dto.getTelefono().trim().isEmpty()) {
            String soloDigitos = dto.getTelefono().trim().replaceFirst("^\\+54\\s*", "").replaceAll("\\D", "");
            if (!soloDigitos.matches("\\d{6,15}")) {
                errores.add("el teléfono debe contener solo números, entre 6 y 15 dígitos");
            }
        }

        if (dto.getDomicilio() != null) {
            PacienteDto.DomicilioDto d = dto.getDomicilio();
            List<String> partes = List.of(
                    d.getCalle() == null ? "" : d.getCalle().trim(),
                    d.getNumero() == null ? "" : d.getNumero().trim(),
                    d.getLocalidad() == null ? "" : d.getLocalidad().trim(),
                    d.getProvincia() == null ? "" : d.getProvincia().trim());
            boolean algunaCompleta = partes.stream().anyMatch(p -> !p.isEmpty());
            boolean todasCompletas = partes.stream().allMatch(p -> !p.isEmpty());
            if (algunaCompleta && !todasCompletas) {
                errores.add("el domicilio requiere calle, número, localidad y provincia completos");
            }
        }

        if (dto.getCredencial() != null) {
            PacienteDto.CredencialInfoDto c = dto.getCredencial();
            if (c.getCodEntidad() == null) {
                errores.add("falta seleccionar la obra social");
            }
            if (c.getPan() == null || c.getPan().trim().isEmpty()) {
                errores.add("el número de afiliado es obligatorio cuando hay obra social");
            } else if (!c.getPan().trim().matches("[A-Za-z0-9\\-/. ]{6,30}")) {
                // QBI2 rechaza la receta con QBI124 "LA CANTIDAD DE CARACTERES NO CUMPLE EL RANGO
                // MINIMO O MAXIMO PARA EL FINANCIADOR SELECCIONADO" recién al emitir — sin este
                // chequeo, un número de afiliado de 1-2 caracteres pasaba esta validación y llegaba
                // hasta QBI2 antes de ser rechazado.
                errores.add("el número de afiliado debe tener entre 6 y 30 caracteres válidos (letras, números, guiones, barras o puntos)");
            }
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Datos del paciente inválidos: " + String.join("; ", errores) + ".");
        }
    }

    @Transactional
    public PacienteDto actualizarPaciente(Long pacienteId, String medicoEmail, PacienteDto dto) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        validarDatosPaciente(dto);

        paciente.setNombre(dto.getNombre());
        paciente.setApellido(dto.getApellido());
        paciente.setNumAfiliado(dto.getNumAfiliado());
        paciente.setDni(dto.getDni());
        paciente.setObraSocial(dto.getObraSocial());
        paciente.setDireccion(dto.getDireccion());
        String patientTel = dto.getTelefono();
        if (patientTel != null && !patientTel.trim().isEmpty() && !patientTel.trim().startsWith("+54")) {
            patientTel = "+54 " + patientTel.trim();
        }
        paciente.setTelefono(patientTel);
        paciente.setSexo(dto.getSexo());
        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().trim().isEmpty()) {
            paciente.setFechaNacimiento(java.time.LocalDate.parse(dto.getFechaNacimiento().trim()));
        }
        paciente.setCuil(dto.getCuil());
        paciente.setTipoDocumento(dto.getTipoDocumento());
        paciente.setNumeroDocumento(dto.getNumeroDocumento());
        paciente.setDatosOfuscado(dto.getDatosOfuscado());

        if (dto.getDomicilio() != null) {
            paciente.setDomicilioCalle(dto.getDomicilio().getCalle());
            paciente.setDomicilioNumero(dto.getDomicilio().getNumero());
            paciente.setDomicilioPiso(dto.getDomicilio().getPiso());
            paciente.setDomicilioDpto(dto.getDomicilio().getDpto());
            paciente.setDomicilioCodigoPostal(dto.getDomicilio().getCodigoPostal());
            paciente.setDomicilioLocalidad(dto.getDomicilio().getLocalidad());
            paciente.setDomicilioProvincia(dto.getDomicilio().getProvincia());
            paciente.setDomicilioPais(dto.getDomicilio().getPais());
        }

        if (dto.getCredencial() != null) {
            paciente.setCredencialCodEntidad(dto.getCredencial().getCodEntidad());
            paciente.setCredencialPan(dto.getCredencial().getPan());
            paciente.setCredencialPlan(dto.getCredencial().getPlan());
            paciente.setCredencialToken(dto.getCredencial().getToken());
            if (dto.getCredencial().getPlan() != null && !dto.getCredencial().getPlan().trim().isEmpty()) {
                String baseOs = (dto.getObraSocial() != null && !dto.getObraSocial().isBlank()) ? dto.getObraSocial() : "Obra Social";
                paciente.setObraSocial(baseOs + " " + dto.getCredencial().getPlan());
            } else {
                paciente.setObraSocial(dto.getObraSocial());
            }
        } else {
            paciente.setCredencialCodEntidad(null);
            paciente.setCredencialPan(null);
            paciente.setCredencialPlan(null);
            paciente.setCredencialToken(null);
            paciente.setObraSocial(null);
        }
        
        Usuario saved = usuarioRepository.save(paciente);
        int unreadMessagesCount = mensajeRepository.countUnreadMessages(saved.getId(), medico.getId());

        return construirPacienteDto(saved, "Hoy", false, false, unreadMessagesCount);
    }

    // Soft-delete: la historia clínica no puede perder asientos (Ley 26.529 art. 15), así que
    // esto nunca borra la fila — solo la marca ANULADO con motivo y quién la anuló. Ver
    // .agent/Etapas/09_cumplimiento_legal_historia_clinica.md, Fase A.1.
    @Transactional
    public void eliminarInforme(Long id, String medicoEmail, String motivo) {
        InformeClinico informe = informeClinicoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informe no encontrado"));
        if (!informe.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar este informe");
        }
        informe.setEstado(ESTADO_ANULADO);
        informe.setMotivo(motivo);
        informeClinicoRepository.save(informe);
    }

    // Corrección por anexo, no edición en el lugar (Ley 26.529 art. 15 + Ley 27.706): el
    // original queda intacto y marcado VIGENTE_CORREGIDO, y se crea una fila nueva
    // (ANEXO_CORRECCION) con el contenido corregido, enlazada a través de informeOriginalId.
    // Ambas quedan visibles y en orden cronológico para el paciente y el profesional.
    @Transactional
    public InformeClinico editarInforme(Long id, String medicoEmail, String tipoInforme, String planTrabajo, String contenido, String motivo) {
        InformeClinico original = informeClinicoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informe no encontrado"));
        if (!original.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para editar este informe");
        }
        if (ESTADO_ANULADO.equals(original.getEstado())) {
            throw new IllegalStateException("No se puede corregir un informe anulado");
        }

        original.setEstado(ESTADO_VIGENTE_CORREGIDO);
        informeClinicoRepository.save(original);

        LocalDateTime ahora = LocalDateTime.now();
        InformeClinico anexo = InformeClinico.builder()
                .medico(original.getMedico())
                .paciente(original.getPaciente())
                .fecha(LocalDate.now())
                .fechaCreacion(ahora)
                .tipoInforme(tipoInforme)
                .planTrabajo(planTrabajo)
                .contenido(contenido)
                .nombreArchivo(original.getNombreArchivo())
                .estado(ESTADO_ANEXO_CORRECCION)
                .informeOriginalId(original.getId())
                .motivo(motivo)
                .hashIntegridad(calcularHash(original.getMedico().getEmail(), String.valueOf(original.getPaciente().getId()),
                        tipoInforme, planTrabajo, contenido, ahora.toString()))
                .build();

        return informeClinicoRepository.save(anexo);
    }

    @Transactional
    public void eliminarSeguimiento(Long id, String medicoEmail, String motivo) {
        SeguimientoDiario seguimiento = seguimientoDiarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Seguimiento no encontrado"));
        if (!seguimiento.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar este seguimiento");
        }
        seguimiento.setEstado(ESTADO_ANULADO);
        seguimiento.setMotivo(motivo);
        seguimientoDiarioRepository.save(seguimiento);
    }
}
