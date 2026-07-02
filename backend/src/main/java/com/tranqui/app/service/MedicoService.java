package com.tranqui.app.service;

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

    private static final List<MedicoDto.TarifaDto> DEFAULT_TARIFFS = Arrays.asList(
            new MedicoDto.TarifaDto("particular", "Consulta particular", new BigDecimal("60000"), true),
            new MedicoDto.TarifaDto("primera", "Primera consulta (+30%)", new BigDecimal("80000"), true),
            new MedicoDto.TarifaDto("sobreturno", "Sobreturno", new BigDecimal("90000"), true),
            new MedicoDto.TarifaDto("osde", "Copago OSDE", new BigDecimal("10500"), true),
            new MedicoDto.TarifaDto("receta-fuera", "Receta fuera de turno", new BigDecimal("45000"), true),
            new MedicoDto.TarifaDto("certificado", "Certificado", new BigDecimal("55000"), true),
            new MedicoDto.TarifaDto("certificado-laboral", "Certificado laboral", new BigDecimal("55000"), true),
            new MedicoDto.TarifaDto("informe-apto", "Informe / Apto médico", new BigDecimal("165000"), true)
    );

    @Transactional(readOnly = true)
    public List<MedicoDto> obtenerMedicosActivos() {
        List<Usuario> medicos = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.PSIQUIATRA)
                .collect(Collectors.toList());

        List<MedicoDto> dtos = new ArrayList<>();
        for (Usuario m : medicos) {
            dtos.add(construirMedicoDto(m));
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

        medico.setNombre(dto.getName());
        medico.setMatricula(dto.getMatricula());
        medico.setTitulo(dto.getDegree());
        medico.setSpecialty(dto.getSpecialty());
        medico.setCuit(dto.getCuit());
        medico.setPrecio(dto.getPrice());
        medico.setColor(dto.getColor());

        if (dto.getTags() != null) {
            medico.setTags(String.join(",", dto.getTags()));
        }

        usuarioRepository.save(medico);

        if (dto.getTariffs() != null) {
            for (MedicoDto.TarifaDto tDto : dto.getTariffs()) {
                Optional<TarifaMedico> tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), tDto.getId());
                TarifaMedico tarifa;
                if (tarifaOpt.isPresent()) {
                    tarifa = tarifaOpt.get();
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
        }

        return construirMedicoDto(medico);
    }

    private MedicoDto construirMedicoDto(Usuario m) {
        List<TarifaMedico> tarifasDb = tarifaRepository.findByMedicoId(m.getId());
        
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
                .map(t -> MedicoDto.TarifaDto.builder()
                        .id(t.getServicioId())
                        .label(t.getLabel())
                        .price(t.getPrecio())
                        .enabled(t.isHabilitado())
                        .build())
                .collect(Collectors.toList());

        List<String> tagsList = new ArrayList<>();
        if (m.getTags() != null && !m.getTags().trim().isEmpty()) {
            tagsList = Arrays.asList(m.getTags().split(","));
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

        return MedicoDto.builder()
                .id(m.getId())
                .name(m.getNombre())
                .email(m.getEmail())
                .initials(initials)
                .degree(m.getTitulo() != null ? m.getTitulo() : "Médico/a")
                .specialty(m.getSpecialty() != null ? m.getSpecialty() : "General")
                .matricula(m.getMatricula())
                .cuit(m.getCuit())
                .price(m.getPrecio() != null ? m.getPrecio() : new BigDecimal("60000"))
                .tags(tagsList)
                .color(m.getColor() != null ? m.getColor() : "#E8F5EE")
                .tariffs(tarifasDto)
                .build();
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
        int noShowsThisMonth = 0;
        String noShowsChange = "Política 24hs activa";

        return DashboardStatsDto.builder()
                .sessionsToday(sessionsToday)
                .sessionsTodayChange(sessionsTodayChange)
                .earningsThisWeek(earningsThisWeek)
                .earningsThisWeekChange(earningsThisWeekChange)
                .activePatients(activePatients)
                .activePatientsChange(activePatientsChange)
                .noShowsThisMonth(noShowsThisMonth)
                .noShowsChange(noShowsChange)
                .build();
    }
}
