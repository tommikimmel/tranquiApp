package com.tranqui.app.service;

import com.tranqui.app.model.EstadoAsistencia;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.model.dto.DashboardStatsDto;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MedicoService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TarifaMedicoRepository tarifaRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    private static final long MAX_FOTO_BYTES = 3L * 1024 * 1024; // 3MB decoded

    // Fallback values for médicos who haven't configured their agenda settings yet —
    // 45 matches the historical hardcoded turno duration, 10 matches the mockup's default.
    private static final int DEFAULT_DURACION_TURNO_MINUTOS = 45;
    private static final int DEFAULT_INTERVALO_ENTRE_TURNOS_MINUTOS = 10;

    private static final List<MedicoDto.TarifaDto> DEFAULT_TARIFFS = Arrays.asList(
            new MedicoDto.TarifaDto("particular", "Consulta particular", new BigDecimal("60000"), true),
            new MedicoDto.TarifaDto("sobreturno", "Sobreturno", new BigDecimal("90000"), true),
            new MedicoDto.TarifaDto("osde", "Copago OSDE", new BigDecimal("10500"), true),
            new MedicoDto.TarifaDto("receta-fuera", "Receta fuera de turno", new BigDecimal("45000"), true),
            new MedicoDto.TarifaDto("certificado", "Certificado", new BigDecimal("55000"), true),
            new MedicoDto.TarifaDto("informe-apto", "Informe / Apto médico", new BigDecimal("165000"), true)
    );

    @Transactional(readOnly = true)
    public List<MedicoDto> obtenerMedicosActivos() {
        // Filter by role at the DB level (was findAll() + Java-side filtering, scanning every
        // usuario row — patients included — on every public homepage load).
        List<Usuario> medicos = usuarioRepository.findByRol(Rol.PSIQUIATRA).stream()
                .filter(this::isMedicoVerificado)
                .collect(Collectors.toList());

        if (medicos.isEmpty()) {
            return new ArrayList<>();
        }

        // Batch-fetch every matching médico's tariffs in one query instead of one query per
        // médico (the N+1 that used to run inside construirMedicoDto for each doctor).
        List<Long> medicoIds = medicos.stream().map(Usuario::getId).collect(Collectors.toList());
        Map<Long, List<TarifaMedico>> tarifasPorMedico = tarifaRepository.findByMedicoIdIn(medicoIds).stream()
                .collect(Collectors.groupingBy(t -> t.getMedico().getId()));

        List<MedicoDto> dtos = new ArrayList<>();
        for (Usuario m : medicos) {
            dtos.add(construirMedicoDto(m, tarifasPorMedico.getOrDefault(m.getId(), new ArrayList<>())));
        }
        return dtos;
    }

    @Transactional
    public MedicoDto obtenerPerfil(String email) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado con email: " + email));
        
        // Ensure user is PSIQUIATRA
        if (medico.getRol() != Rol.PSIQUIATRA) {
            medico.setRol(Rol.PSIQUIATRA);
            usuarioRepository.save(medico);
        }

        return construirMedicoDto(medico);
    }

    @Transactional
    public MedicoDto actualizarPerfil(String email, MedicoDto dto) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        medico.setNombre(dto.getNombre());
        medico.setApellido(dto.getApellido());
        medico.setSexo(dto.getSexo());
        medico.setFechaNacimiento(dto.getFechaNacimiento());
        medico.setCuil(dto.getCuil());
        medico.setTipoDocumento(dto.getTipoDocumento());
        medico.setNumeroDocumento(dto.getNumeroDocumento());
        medico.setDomicilioAtencion(dto.getDomicilioAtencion());
        medico.setDomicilioLat(dto.getDomicilioLat());
        medico.setDomicilioLng(dto.getDomicilioLng());
        medico.setCodigoReFeps(dto.getCodigoReFeps());

        if (dto.getMatriculaInfo() != null) {
            medico.setMatriculaTipo(dto.getMatriculaInfo().getTipo());
            medico.setMatriculaProvincia(dto.getMatriculaInfo().getProvincia());
            medico.setMatriculaNumero(dto.getMatriculaInfo().getNumero());
            if (dto.getMatriculaInfo().getEspecialidad() != null) {
                medico.setMatriculaEspecialidad(dto.getMatriculaInfo().getEspecialidad().getTextoLibre());
            }
            if (dto.getMatriculaInfo().getAsociada() != null) {
                medico.setMatriculaAsocTipo(dto.getMatriculaInfo().getAsociada().getTipo());
                medico.setMatriculaAsocProvincia(dto.getMatriculaInfo().getAsociada().getProvincia());
                medico.setMatriculaAsocNumero(dto.getMatriculaInfo().getAsociada().getNumero());
            }
            if (dto.getMatriculaInfo().getNumero() != null) {
                medico.setMatricula(String.valueOf(dto.getMatriculaInfo().getNumero()));
            }
        } else {
            medico.setMatricula(dto.getMatricula());
        }

        medico.setTitulo(dto.getDegree());
        medico.setSpecialty(dto.getSpecialty());
        medico.setCuit(dto.getCuit());
        medico.setPrecio(dto.getPrice());
        medico.setColor(dto.getColor());

        if (dto.getFotoUrl() != null && com.tranqui.app.util.ImageUtils.decodedByteSize(dto.getFotoUrl()) > MAX_FOTO_BYTES) {
            throw new IllegalArgumentException(
                    "La foto de perfil es demasiado grande (máx. " + (MAX_FOTO_BYTES / (1024 * 1024)) + "MB). Elegí una imagen más liviana.");
        }
        medico.setFotoUrl(dto.getFotoUrl());
        medico.setOfreceOnline(dto.isOfreceOnline());
        medico.setOfrecePresencial(dto.isOfrecePresencial());

        if (dto.getFirmaUrl() != null && com.tranqui.app.util.ImageUtils.decodedByteSize(dto.getFirmaUrl()) > MAX_FOTO_BYTES) {
            throw new IllegalArgumentException(
                    "La imagen de firma es demasiado grande (máx. " + (MAX_FOTO_BYTES / (1024 * 1024)) + "MB). Elegí una imagen más liviana.");
        }
        medico.setFirmaUrl(dto.getFirmaUrl());
        medico.setSelloLinea1(dto.getSelloLinea1());
        medico.setSelloLinea2(dto.getSelloLinea2());
        medico.setSelloLinea3(dto.getSelloLinea3());

        if (dto.getTags() != null) {
            medico.setTags(String.join(",", dto.getTags()));
        }

        medico.setDescripcionPerfil(dto.getDescripcionPerfil());
        medico.setInstitucionFormacion(dto.getInstitucionFormacion());
        medico.setAniosExperiencia(dto.getAniosExperiencia());
        if (dto.getPacientesAtiende() != null) {
            medico.setPacientesAtiende(String.join(",", dto.getPacientesAtiende()));
        }

        usuarioRepository.save(medico);

        if (dto.getTariffs() != null) {
            Set<String> submittedIds = dto.getTariffs().stream()
                    .map(MedicoDto.TarifaDto::getId)
                    .collect(Collectors.toSet());

            for (MedicoDto.TarifaDto tDto : dto.getTariffs()) {
                Optional<TarifaMedico> tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), tDto.getId());
                TarifaMedico tarifa;
                if (tarifaOpt.isPresent()) {
                    tarifa = tarifaOpt.get();
                    tarifa.setLabel(tDto.getLabel());
                    tarifa.setPrecio(tDto.getPrice());
                    tarifa.setHabilitado(tDto.isEnabled());
                } else {
                    tarifa = TarifaMedico.builder()
                            .medico(medico)
                            .servicioId(tDto.getId())
                            .label(tDto.getLabel())
                            .precio(tDto.getPrice())
                            .habilitado(tDto.isEnabled())
                            .build();
                }
                tarifaRepository.save(tarifa);
            }

            // Any tariff the médico had before that isn't in this submission was removed via
            // the "eliminar" action on the frontend — delete it so it doesn't reappear on the
            // next profile load.
            List<TarifaMedico> tarifasAEliminar = tarifaRepository.findByMedicoId(medico.getId()).stream()
                    .filter(t -> !submittedIds.contains(t.getServicioId()))
                    .collect(Collectors.toList());
            if (!tarifasAEliminar.isEmpty()) {
                tarifaRepository.deleteAll(tarifasAEliminar);
            }
        }

        return construirMedicoDto(medico);
    }

    private MedicoDto construirMedicoDto(Usuario m) {
        return construirMedicoDto(m, tarifaRepository.findByMedicoId(m.getId()));
    }

    private MedicoDto construirMedicoDto(Usuario m, List<TarifaMedico> tarifasDb) {
        // If doctor has no tariffs in DB, initialize them with defaults
        if (tarifasDb.isEmpty()) {
            tarifasDb = new ArrayList<>();
            for (MedicoDto.TarifaDto def : DEFAULT_TARIFFS) {
                TarifaMedico t = TarifaMedico.builder()
                        .medico(m)
                        .servicioId(def.getId())
                        .label(def.getLabel())
                        .precio(def.getPrice())
                        .habilitado(def.isEnabled())
                        .build();
                tarifasDb.add(tarifaRepository.save(t));
            }
        }

        List<MedicoDto.TarifaDto> tarifasDto = tarifasDb.stream()
                .filter(t -> !"primera".equals(t.getServicioId()) && !"certificado-laboral".equals(t.getServicioId()))
                .map(t -> MedicoDto.TarifaDto.builder()
                        .id(t.getServicioId())
                        .label(t.getLabel())
                        .price(t.getPrecio())
                        .enabled(t.isHabilitado())
                        .build())
                .collect(Collectors.toList());

        // The public "Desde $X" price must reflect what the médico actually configured in
        // "Honorarios y servicios" (the "particular" tariff), not the legacy Usuario.precio
        // field, which is only ever set once on account creation and never kept in sync.
        BigDecimal price = tarifasDto.stream()
                .filter(t -> "particular".equals(t.getId()) && t.isEnabled())
                .map(MedicoDto.TarifaDto::getPrice)
                .findFirst()
                .orElse(m.getPrecio() != null ? m.getPrecio() : new BigDecimal("60000"));

        List<String> tagsList = new ArrayList<>();
        if (m.getTags() != null && !m.getTags().trim().isEmpty()) {
            tagsList = Arrays.asList(m.getTags().split(","));
        }

        List<String> pacientesAtiendeList = new ArrayList<>();
        if (m.getPacientesAtiende() != null && !m.getPacientesAtiende().trim().isEmpty()) {
            pacientesAtiendeList = Arrays.asList(m.getPacientesAtiende().split(","));
        }

        String initials = "";
        if (m.getNombre() != null) {
            String[] parts = m.getNombre().split(" ");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty() && !part.contains("Lic.") && !part.contains("Dr.") && !part.contains("Dra.")) {
                    sb.append(part.charAt(0));
                }
            }
            initials = sb.toString().toUpperCase();
            if (initials.isEmpty()) {
                initials = m.getNombre().substring(0, Math.min(2, m.getNombre().length())).toUpperCase();
            }
        }

        MedicoDto.MatriculaInfoDto matInfo = null;
        if (m.getMatriculaTipo() != null || m.getMatriculaNumero() != null) {
            matInfo = MedicoDto.MatriculaInfoDto.builder()
                    .tipo(m.getMatriculaTipo())
                    .provincia(m.getMatriculaProvincia())
                    .numero(m.getMatriculaNumero())
                    .especialidad(MedicoDto.EspecialidadDto.builder().textoLibre(m.getMatriculaEspecialidad()).build())
                    .asociada(MedicoDto.AsociadaDto.builder()
                            .tipo(m.getMatriculaAsocTipo())
                            .provincia(m.getMatriculaAsocProvincia())
                            .numero(m.getMatriculaAsocNumero())
                            .build())
                    .build();
        }

        String nombreCompleto = m.getNombre();
        if (m.getApellido() != null && !m.getApellido().trim().isEmpty()) {
            nombreCompleto = nombreCompleto + " " + m.getApellido();
        }

        return MedicoDto.builder()
                .id(m.getId())
                .name(nombreCompleto)
                .nombre(m.getNombre())
                .email(m.getEmail())
                .initials(initials)
                .degree(m.getTitulo() != null ? m.getTitulo() : "Médico/a")
                .specialty(m.getSpecialty() != null ? m.getSpecialty() : "General")
                .matricula(m.getMatricula())
                .cuit(m.getCuit())
                .price(price)
                .tags(tagsList)
                .color(m.getColor() != null ? m.getColor() : "#E8F5EE")
                .fotoUrl(m.getFotoUrl())
                .ofreceOnline(m.isOfreceOnline())
                .ofrecePresencial(m.isOfrecePresencial())
                .tariffs(tarifasDto)
                .apellido(m.getApellido())
                .sexo(m.getSexo())
                .fechaNacimiento(m.getFechaNacimiento())
                .cuil(m.getCuil())
                .tipoDocumento(m.getTipoDocumento())
                .numeroDocumento(m.getNumeroDocumento())
                .domicilioAtencion(m.getDomicilioAtencion())
                .domicilioLat(m.getDomicilioLat())
                .domicilioLng(m.getDomicilioLng())
                .codigoReFeps(m.getCodigoReFeps())
                .matriculaInfo(matInfo)
                .verificado(isMedicoVerificado(m))
                .verificadoAdmin(m.getVerificadoAdmin())
                .descripcionPerfil(m.getDescripcionPerfil())
                .pacientesAtiende(pacientesAtiendeList)
                .institucionFormacion(m.getInstitucionFormacion())
                .aniosExperiencia(m.getAniosExperiencia())
                .duracionTurnoMinutos(m.getDuracionTurnoMinutos() != null ? m.getDuracionTurnoMinutos() : DEFAULT_DURACION_TURNO_MINUTOS)
                .intervaloEntreTurnosMinutos(m.getIntervaloEntreTurnosMinutos() != null ? m.getIntervaloEntreTurnosMinutos() : DEFAULT_INTERVALO_ENTRE_TURNOS_MINUTOS)
                .firmaUrl(m.getFirmaUrl())
                .selloLinea1(m.getSelloLinea1())
                .selloLinea2(m.getSelloLinea2())
                .selloLinea3(m.getSelloLinea3())
                .build();
    }

    /**
     * Updates only the médico's agenda settings (turno duration + gap between bookable
     * slots) without touching the rest of the profile — keeps this independent from
     * actualizarPerfil, which does a full-replace of every profile field.
     */
    @Transactional
    public MedicoDto actualizarConfigAgenda(String email, Integer duracionTurnoMinutos, Integer intervaloEntreTurnosMinutos) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        if (duracionTurnoMinutos != null) {
            if (duracionTurnoMinutos <= 0) {
                throw new IllegalArgumentException("La duración del turno debe ser mayor a 0 minutos.");
            }
            medico.setDuracionTurnoMinutos(duracionTurnoMinutos);
        }
        if (intervaloEntreTurnosMinutos != null) {
            if (intervaloEntreTurnosMinutos < 0) {
                throw new IllegalArgumentException("El intervalo entre turnos no puede ser negativo.");
            }
            medico.setIntervaloEntreTurnosMinutos(intervaloEntreTurnosMinutos);
        }

        usuarioRepository.save(medico);
        return construirMedicoDto(medico);
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto obtenerStats(String email) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado con el email: " + email));

        List<Turno> turnos = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);

        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);

        // 1. Sessions today
        int sessionsToday = (int) turnos.stream()
                .filter(t -> t.getFecha().equals(hoy))
                .count();

        // Sessions yesterday
        int sessionsYesterday = (int) turnos.stream()
                .filter(t -> t.getFecha().equals(ayer))
                .count();

        String sessionsTodayChange;
        int diffToday = sessionsToday - sessionsYesterday;
        if (diffToday > 0) {
            sessionsTodayChange = "+" + diffToday + " vs ayer";
        } else if (diffToday < 0) {
            sessionsTodayChange = diffToday + " vs ayer";
        } else {
            sessionsTodayChange = "igual que ayer";
        }

        // 2. Earnings this week vs last week
        LocalDate startOfThisWeek = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfThisWeek = startOfThisWeek.plusDays(6);
        LocalDate startOfLastWeek = startOfThisWeek.minusWeeks(1);
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);

        BigDecimal earningsThisWeek = turnos.stream()
                .filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO && !t.getFecha().isBefore(startOfThisWeek) && !t.getFecha().isAfter(endOfThisWeek))
                .map(Turno::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal earningsLastWeek = turnos.stream()
                .filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO && !t.getFecha().isBefore(startOfLastWeek) && !t.getFecha().isAfter(endOfLastWeek))
                .map(Turno::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String earningsThisWeekChange;
        if (earningsLastWeek.compareTo(BigDecimal.ZERO) == 0) {
            if (earningsThisWeek.compareTo(BigDecimal.ZERO) == 0) {
                earningsThisWeekChange = "0% vs sem. anterior";
            } else {
                earningsThisWeekChange = "+100% vs sem. anterior";
            }
        } else {
            BigDecimal percentChange = earningsThisWeek.subtract(earningsLastWeek)
                    .multiply(new BigDecimal("100"))
                    .divide(earningsLastWeek, 1, java.math.RoundingMode.HALF_UP);
            if (percentChange.compareTo(BigDecimal.ZERO) > 0) {
                earningsThisWeekChange = "+" + percentChange + "% vs sem. anterior";
            } else if (percentChange.compareTo(BigDecimal.ZERO) < 0) {
                earningsThisWeekChange = percentChange + "% vs sem. anterior";
            } else {
                earningsThisWeekChange = "0% vs sem. anterior";
            }
        }

        // 3. Active patients
        Set<Long> activePatientIds = turnos.stream()
                .map(t -> t.getPaciente().getId())
                .collect(Collectors.toSet());
        int activePatients = activePatientIds.size();

        // Active patients added this month (meaning their first appointment was this month)
        LocalDate startOfThisMonth = hoy.with(TemporalAdjusters.firstDayOfMonth());
        Map<Long, LocalDate> firstAppointmentDate = new HashMap<>();
        for (Turno t : turnos) {
            Long pId = t.getPaciente().getId();
            LocalDate pDate = t.getFecha();
            if (!firstAppointmentDate.containsKey(pId) || pDate.isBefore(firstAppointmentDate.get(pId))) {
                firstAppointmentDate.put(pId, pDate);
            }
        }
        long newPatientsThisMonth = firstAppointmentDate.values().stream()
                .filter(d -> !d.isBefore(startOfThisMonth))
                .count();

        String activePatientsChange = "+" + newPatientsThisMonth + " este mes";

        // 4. No shows this month
        LocalDate endOfThisMonth = hoy.with(TemporalAdjusters.lastDayOfMonth());

        int noShowsThisMonthVal = (int) turnos.stream()
                .filter(t -> t.getAsistencia() == EstadoAsistencia.AUSENTE
                        && !t.getFecha().isBefore(startOfThisMonth)
                        && !t.getFecha().isAfter(endOfThisMonth))
                .count();

        LocalDate startOfLastMonth = startOfThisMonth.minusMonths(1);
        LocalDate endOfLastMonth = startOfThisMonth.minusDays(1);
        int noShowsLastMonthVal = (int) turnos.stream()
                .filter(t -> t.getAsistencia() == EstadoAsistencia.AUSENTE
                        && !t.getFecha().isBefore(startOfLastMonth)
                        && !t.getFecha().isAfter(endOfLastMonth))
                .count();

        String noShowsChange;
        int diffNoShows = noShowsThisMonthVal - noShowsLastMonthVal;
        if (diffNoShows > 0) {
            noShowsChange = "+" + diffNoShows + " vs mes anterior";
        } else if (diffNoShows < 0) {
            noShowsChange = diffNoShows + " vs mes anterior";
        } else {
            noShowsChange = "igual que mes anterior";
        }

        return DashboardStatsDto.builder()
                .sessionsToday(sessionsToday)
                .sessionsTodayChange(sessionsTodayChange)
                .earningsThisWeek(earningsThisWeek)
                .earningsThisWeekChange(earningsThisWeekChange)
                .activePatients(activePatients)
                .activePatientsChange(activePatientsChange)
                .noShowsThisMonth(noShowsThisMonthVal)
                .noShowsChange(noShowsChange)
                .build();
    }

    public boolean isMedicoVerificado(Usuario u) {
        return u.getNombre() != null && !u.getNombre().trim().isEmpty()
                && u.getApellido() != null && !u.getApellido().trim().isEmpty()
                && u.getSexo() != null && !u.getSexo().trim().isEmpty()
                && u.getFechaNacimiento() != null
                && u.getCuil() != null
                && u.getEmail() != null && !u.getEmail().trim().isEmpty()
                && u.getTipoDocumento() != null && !u.getTipoDocumento().trim().isEmpty()
                && u.getNumeroDocumento() != null
                && u.getDomicilioAtencion() != null && !u.getDomicilioAtencion().trim().isEmpty()
                && u.getCodigoReFeps() != null
                && u.getMatriculaTipo() != null && !u.getMatriculaTipo().trim().isEmpty()
                && u.getMatriculaProvincia() != null && !u.getMatriculaProvincia().trim().isEmpty()
                && u.getMatriculaNumero() != null
                && u.getFotoUrl() != null && !u.getFotoUrl().trim().isEmpty()
                && u.getDescripcionPerfil() != null && !u.getDescripcionPerfil().trim().isEmpty()
                && u.getPacientesAtiende() != null && !u.getPacientesAtiende().trim().isEmpty()
                && u.getInstitucionFormacion() != null && !u.getInstitucionFormacion().trim().isEmpty()
                && u.getAniosExperiencia() != null
                && u.getTags() != null && !u.getTags().trim().isEmpty()
                && (u.isOfreceOnline() || u.isOfrecePresencial())
                && Boolean.TRUE.equals(u.getVerificadoAdmin());
    }
}
