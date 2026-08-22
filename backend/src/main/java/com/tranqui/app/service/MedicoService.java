package com.tranqui.app.service;

import com.tranqui.app.model.EstadoAsistencia;
import com.tranqui.app.model.EstadoPago;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MedicoService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TarifaMedicoRepository tarifaRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    // Same flag MedicoController/MercadoPagoService use to know whether MP is really wired up
    // (real deployments) vs. simulated (local/dev) — verification only requires a connected MP
    // account when the integration is actually live, so local dev/tests aren't blocked forever.
    @Value("${mercadopago.enabled:false}")
    private boolean mercadoPagoEnabled;

    private static final long MAX_FOTO_BYTES = 3L * 1024 * 1024; // 3MB decoded

    // Fallback values for médicos who haven't configured their agenda settings yet —
    // 45 matches the historical hardcoded turno duration, 10 matches the mockup's default.
    private static final int DEFAULT_DURACION_TURNO_MINUTOS = 45;
    private static final int DEFAULT_INTERVALO_ENTRE_TURNOS_MINUTOS = 0;

    // "osde" replaces the old generic "obra_social" default as the seed for NEW médicos with no
    // tariffs yet — a specific obra social ("OSDE") instead of a generic "any obra social"
    // service, matching the new per-servicio obraSocial model. Médicos who already have an
    // "obra_social" row persisted keep it untouched; TurnoService.reservarTurno still treats
    // "obra_social"/"osde" as synonyms so both keep working.
    private static final List<MedicoDto.TarifaDto> DEFAULT_TARIFFS = Arrays.asList(
            new MedicoDto.TarifaDto("particular", "Consulta particular", new BigDecimal("60000"), true, false, null, null, null, true, false),
            new MedicoDto.TarifaDto("sobreturno", "Sobreturno", new BigDecimal("90000"), true, false, null, null, null, true, false),
            new MedicoDto.TarifaDto("osde", "Obra Social OSDE", new BigDecimal("10500"), true, true, "OSDE", null, null, true, false),
            // These three are pure document services — no consultorio, no videollamada — so they
            // don't reserve a slot on the médico's agenda. See TarifaMedico.requiereAgenda. Only
            // "receta-fuera" is also flagged esReceta — see TarifaMedico.esReceta.
            new MedicoDto.TarifaDto("receta-fuera", "Receta fuera de turno", new BigDecimal("45000"), true, false, null, null, null, false, true),
            new MedicoDto.TarifaDto("certificado", "Certificado", new BigDecimal("55000"), true, false, null, null, null, false, false),
            new MedicoDto.TarifaDto("informe-apto", "Informe / Apto médico", new BigDecimal("165000"), true, false, null, null, null, false, false)
    );

    // NOT readOnly: construirMedicoDto self-heals by persisting DEFAULT_TARIFFS the first time a
    // médico has zero TarifaMedico rows (e.g. right after they clear isMedicoVerificado but before
    // ever opening "Honorarios y servicios") — under readOnly=true that INSERT throws (Postgres
    // rejects writes in a read-only transaction) and takes down the entire public listing with a
    // 500, not just that one médico's card.
    @Transactional
    public List<MedicoDto> obtenerMedicosActivos() {
        // Filter by role at the DB level (was findAll() + Java-side filtering, scanning every
        // usuario row — patients included — on every public homepage load).
        // Perfil completo + verificado por admin (isMedicoVerificado) Y suscripción activa
        // (isAccessAllowed) — un profesional sin pago al día deja de listarse públicamente aunque
        // su perfil esté impecable. Chequeos separados a propósito: isMedicoVerificado también
        // alimenta MedicoDto.verificado (el banner de "completá tu perfil" del propio profesional),
        // que es un concepto distinto de si está al día con el pago.
        List<Usuario> medicos = usuarioRepository.findByRol(Rol.PSIQUIATRA).stream()
                .filter(this::isMedicoVerificado)
                .filter(m -> subscriptionService.isAccessAllowed(m.getId()))
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

    private static String trimToNull(String value, int maxLen) {
        String trimmed = value != null ? value.trim() : null;
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLen) {
            throw new IllegalArgumentException("Uno de los campos de la dirección de atención supera el largo máximo permitido (" + maxLen + " caracteres).");
        }
        return trimmed;
    }

    @Transactional
    public MedicoDto actualizarPerfil(String email, MedicoDto dto) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        medico.setNombre(dto.getNombre());
        medico.setApellido(dto.getApellido());
        medico.setTelefono(dto.getTelefono() != null ? dto.getTelefono().trim() : null);
        String emailContactoTrim = dto.getEmailContacto() != null ? dto.getEmailContacto().trim() : null;
        if (emailContactoTrim != null && !emailContactoTrim.isEmpty() && !emailContactoTrim.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email de contacto no tiene un formato válido.");
        }
        medico.setEmailContacto(emailContactoTrim != null && !emailContactoTrim.isEmpty() ? emailContactoTrim : null);
        medico.setSexo(dto.getSexo());
        medico.setFechaNacimiento(dto.getFechaNacimiento());
        medico.setCuil(dto.getCuil());
        medico.setTipoDocumento(dto.getTipoDocumento());
        medico.setNumeroDocumento(dto.getNumeroDocumento());
        // AddressMapPicker's Nominatim autocomplete used to store the full display_name for an
        // Argentine address — every administrative level it knows about (barrio, pedanía,
        // municipio, departamento, provincia twice, CP, país), 150+ characters of noise that
        // prints on the QBI2 prescription PDF. The picker builds a short "Calle Altura, Localidad"
        // now, but validate here too since this DTO can be hit directly, not just through the UI.
        String domicilioTrim = dto.getDomicilioAtencion() != null ? dto.getDomicilioAtencion().trim() : null;
        if (domicilioTrim != null && !domicilioTrim.isEmpty() && (domicilioTrim.length() < 8 || domicilioTrim.length() > 140)) {
            throw new IllegalArgumentException(
                    "El domicilio de atención debe tener entre 8 y 140 caracteres. Usá el buscador de direcciones y elegí una sugerencia en vez de pegar la dirección completa.");
        }
        medico.setDomicilioAtencion(domicilioTrim != null && !domicilioTrim.isEmpty() ? domicilioTrim : null);
        medico.setDomicilioLat(dto.getDomicilioLat());
        medico.setDomicilioLng(dto.getDomicilioLng());
        // Torre/Piso/Depto/Barrio solo tienen sentido si el médico atiende de forma presencial.
        if (dto.isOfrecePresencial()) {
            medico.setDomicilioAtencionTorre(trimToNull(dto.getDomicilioAtencionTorre(), 50));
            medico.setDomicilioAtencionPiso(trimToNull(dto.getDomicilioAtencionPiso(), 20));
            medico.setDomicilioAtencionDepto(trimToNull(dto.getDomicilioAtencionDepto(), 20));
            medico.setDomicilioAtencionBarrio(trimToNull(dto.getDomicilioAtencionBarrio(), 100));
        } else {
            medico.setDomicilioAtencionTorre(null);
            medico.setDomicilioAtencionPiso(null);
            medico.setDomicilioAtencionDepto(null);
            medico.setDomicilioAtencionBarrio(null);
        }

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
        medico.setExperiencia(dto.getExperiencia());
        medico.setPublicaciones(dto.getPublicaciones());

        if (dto.getRedesSociales() != null) {
            medico.setInstagramUrl(dto.getRedesSociales().getInstagram());
            medico.setFacebookUrl(dto.getRedesSociales().getFacebook());
            medico.setLinkedinUrl(dto.getRedesSociales().getLinkedin());
            medico.setSitioWebUrl(dto.getRedesSociales().getSitioWeb());
        }

        // QBI2 rechaza con QBI235 "EL CAMPO MEDICO IDREFEPS NO CUMPLE EL RANGO MÍNIMO O MÁXIMO DE
        // CARACTERES" cualquier valor que no sea el código numérico de 12 dígitos que SISA asigna
        // en el Registro Federal de Profesionales de la Salud — pero ese rechazo solo aparecía al
        // emitir una receta, mucho después de guardar el perfil. Validamos acá para que el error
        // se vea de inmediato, en el momento en que el médico carga el dato.
        String refepsTrim = dto.getCodigoRefeps() != null ? dto.getCodigoRefeps().trim() : null;
        if (refepsTrim != null && !refepsTrim.isEmpty() && !refepsTrim.matches("\\d{12}")) {
            throw new IllegalArgumentException(
                    "El código REFEPS debe tener exactamente 12 dígitos numéricos (lo asigna SISA al matricularte, no se inventa). Podés consultarlo en sisa.msal.gov.ar.");
        }
        medico.setCodigoRefeps(refepsTrim != null && !refepsTrim.isEmpty() ? refepsTrim : null);

        // sello_linea1/2 son varchar(40) y sello_linea3 varchar(25) en la base. El frontend
        // autogenera un valor por defecto para linea1 ("Dr. {nombre} {apellido}") que puede superar
        // 40 caracteres para médicos con nombres largos — sin este chequeo, guardar el perfil
        // rompía con un PSQLException "value too long for type character varying(40)" sin capturar,
        // que Spring convierte en un 500 pelado sin ningún mensaje útil para el médico.
        if (dto.getSelloLinea1() != null && dto.getSelloLinea1().length() > 40) {
            throw new IllegalArgumentException("La línea 1 del sello no puede superar los 40 caracteres.");
        }
        if (dto.getSelloLinea2() != null && dto.getSelloLinea2().length() > 40) {
            throw new IllegalArgumentException("La línea 2 del sello no puede superar los 40 caracteres.");
        }
        if (dto.getSelloLinea3() != null && dto.getSelloLinea3().length() > 25) {
            throw new IllegalArgumentException("La línea 3 del sello no puede superar los 25 caracteres.");
        }
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
                // A service "requiere obra social" if it has a specific obra social assigned
                // (the new per-servicio model), or is one of the legacy generic ids, or the
                // médico explicitly flagged it — enforced server-side too, not just in the UI,
                // so it can't be bypassed by calling the API directly.
                boolean esServicioObraSocial = "obra_social".equals(tDto.getId()) || "osde".equals(tDto.getId());
                String obraSocialTrim = tDto.getObraSocial() != null ? tDto.getObraSocial().trim() : null;
                if (obraSocialTrim != null && obraSocialTrim.isEmpty()) obraSocialTrim = null;
                boolean requiereObraSocial = esServicioObraSocial || obraSocialTrim != null || tDto.isRequiereObraSocial();

                Optional<TarifaMedico> tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), tDto.getId());
                TarifaMedico tarifa;
                if (tarifaOpt.isPresent()) {
                    tarifa = tarifaOpt.get();
                    tarifa.setLabel(tDto.getLabel());
                    tarifa.setPrecio(tDto.getPrice());
                    tarifa.setHabilitado(tDto.isEnabled());
                    tarifa.setRequiereObraSocial(requiereObraSocial);
                    tarifa.setObraSocial(obraSocialTrim);
                    tarifa.setPrecioOnline(tDto.getPrecioOnline());
                    tarifa.setPrecioPresencial(tDto.getPrecioPresencial());
                    tarifa.setRequiereAgenda(tDto.isRequiereAgenda());
                    // Only meaningful when requiereAgenda is false — a servicio that requires
                    // agenda is never a "receta fuera de turno" candidate to begin with.
                    tarifa.setEsReceta(tDto.isRequiereAgenda() ? false : tDto.isEsReceta());
                } else {
                    tarifa = TarifaMedico.builder()
                            .medico(medico)
                            .servicioId(tDto.getId())
                            .label(tDto.getLabel())
                            .precio(tDto.getPrice())
                            .habilitado(tDto.isEnabled())
                            .requiereObraSocial(requiereObraSocial)
                            .obraSocial(obraSocialTrim)
                            .precioOnline(tDto.getPrecioOnline())
                            .precioPresencial(tDto.getPrecioPresencial())
                            .requiereAgenda(tDto.isRequiereAgenda())
                            .esReceta(tDto.isRequiereAgenda() ? false : tDto.isEsReceta())
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
                        .requiereObraSocial(def.isRequiereObraSocial())
                        .obraSocial(def.getObraSocial())
                        .requiereAgenda(def.isRequiereAgenda())
                        .esReceta(def.isEsReceta())
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
                        .requiereObraSocial(t.isRequiereObraSocial())
                        .obraSocial(t.getObraSocial())
                        .precioOnline(t.getPrecioOnline())
                        .precioPresencial(t.getPrecioPresencial())
                        .requiereAgenda(t.isRequiereAgenda())
                        .esReceta(t.isEsReceta())
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
                .emailContacto(m.getEmailContacto())
                .telefono(m.getTelefono())
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
                .domicilioAtencionTorre(m.getDomicilioAtencionTorre())
                .domicilioAtencionPiso(m.getDomicilioAtencionPiso())
                .domicilioAtencionDepto(m.getDomicilioAtencionDepto())
                .domicilioAtencionBarrio(m.getDomicilioAtencionBarrio())
                .matriculaInfo(matInfo)
                .verificado(isMedicoVerificado(m))
                .verificadoAdmin(m.getVerificadoAdmin())
                .descripcionPerfil(m.getDescripcionPerfil())
                .pacientesAtiende(pacientesAtiendeList)
                .institucionFormacion(m.getInstitucionFormacion())
                .aniosExperiencia(m.getAniosExperiencia())
                .duracionTurnoMinutos(m.getDuracionTurnoMinutos() != null ? m.getDuracionTurnoMinutos() : DEFAULT_DURACION_TURNO_MINUTOS)
                .intervaloEntreTurnosMinutos(m.getIntervaloEntreTurnosMinutos() != null ? m.getIntervaloEntreTurnosMinutos() : DEFAULT_INTERVALO_ENTRE_TURNOS_MINUTOS)
                .codigoRefeps(m.getCodigoRefeps())
                .selloLinea1(m.getSelloLinea1())
                .selloLinea2(m.getSelloLinea2())
                .selloLinea3(m.getSelloLinea3())
                .experiencia(m.getExperiencia())
                .publicaciones(m.getPublicaciones())
                .redesSociales(MedicoDto.RedesSocialesDto.builder()
                        .instagram(m.getInstagramUrl())
                        .facebook(m.getFacebookUrl())
                        .linkedin(m.getLinkedinUrl())
                        .sitioWeb(m.getSitioWebUrl())
                        .build())
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
    public DashboardStatsDto obtenerStats(String email, String periodo) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado con el email: " + email));

        LocalDate hoy = LocalDate.now();

        // Every metric on the dashboard's stat cards used to be hardcoded to its own fixed
        // window (sessions=today, earnings=this week, no-shows=this month) — the médico now
        // picks one shared window (Diario/Semanal/Mensual) and all four cards + their "vs
        // período anterior" comparison follow it.
        LocalDate currentStart;
        LocalDate currentEnd;
        LocalDate previousStart;
        LocalDate previousEnd;
        String changeSuffix;
        String flatMessage;
        String periodoNorm = periodo == null ? "MENSUAL" : periodo.trim().toUpperCase();

        switch (periodoNorm) {
            case "DIARIO":
                currentStart = hoy;
                currentEnd = hoy;
                previousStart = hoy.minusDays(1);
                previousEnd = hoy.minusDays(1);
                changeSuffix = "vs ayer";
                flatMessage = "igual que ayer";
                break;
            case "SEMANAL":
                currentStart = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                currentEnd = currentStart.plusDays(6);
                previousStart = currentStart.minusWeeks(1);
                previousEnd = previousStart.plusDays(6);
                changeSuffix = "vs sem. anterior";
                flatMessage = "igual que la semana anterior";
                break;
            case "MENSUAL":
            default:
                currentStart = hoy.with(TemporalAdjusters.firstDayOfMonth());
                currentEnd = hoy.with(TemporalAdjusters.lastDayOfMonth());
                previousStart = currentStart.minusMonths(1);
                previousEnd = currentStart.minusDays(1);
                changeSuffix = "vs mes anterior";
                flatMessage = "igual que el mes anterior";
                break;
        }

        // Bounded to [previousStart, currentEnd] — used to load the médico's ENTIRE turno
        // history on every dashboard refresh, which grows unbounded with account age. The one
        // metric that genuinely needs full history (has this patient EVER been seen before this
        // period, for "new patients") is computed separately below via a lightweight aggregate
        // query instead of scanning every Turno entity.
        List<Turno> turnos = turnoRepository.findByMedicoIdAndEstadoNotAndFechaBetween(medico.getId(), EstadoTurno.CANCELADO, previousStart, currentEnd);

        // Turnos cancelados: se cuentan aparte de `turnos` (que los excluye) para poder distinguir,
        // dentro de las ganancias, los cancelados SIN reembolso (el paciente no recupera el dinero,
        // así que el profesional sigue ganando ese turno) de los cancelados CON reembolso (pago pasa
        // a REEMBOLSADO, no cuenta como ganancia). Ver ReembolsoService: bloquea el reembolso
        // automático si la cancelación ocurre dentro de las 48hs previas al turno.
        List<Turno> turnosCancelados = turnoRepository.findByMedicoIdAndEstadoAndFechaBetween(medico.getId(), EstadoTurno.CANCELADO, previousStart, currentEnd);
        List<Turno> canceladosSinReembolso = turnosCancelados.stream()
                .filter(t -> t.getPago() != null && t.getPago().getEstado() == EstadoPago.APROBADO)
                .collect(Collectors.toList());

        // 1. Sessions in the selected period vs the equivalent previous period
        int sessionsCurrent = (int) turnos.stream()
                .filter(t -> !t.getFecha().isBefore(currentStart) && !t.getFecha().isAfter(currentEnd))
                .count();
        int sessionsPrevious = (int) turnos.stream()
                .filter(t -> !t.getFecha().isBefore(previousStart) && !t.getFecha().isAfter(previousEnd))
                .count();

        String sessionsChange;
        int diffSessions = sessionsCurrent - sessionsPrevious;
        if (diffSessions > 0) {
            sessionsChange = "+" + diffSessions + " " + changeSuffix;
        } else if (diffSessions < 0) {
            sessionsChange = diffSessions + " " + changeSuffix;
        } else {
            sessionsChange = flatMessage;
        }

        // 2. Earnings in the selected period vs the equivalent previous period. Includes turnos
        // cancelados sin reembolso: el dinero no vuelve al paciente, así que sigue siendo ganancia
        // del profesional.
        BigDecimal earningsCurrent = Stream.concat(
                        turnos.stream().filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO),
                        canceladosSinReembolso.stream())
                .filter(t -> !t.getFecha().isBefore(currentStart) && !t.getFecha().isAfter(currentEnd))
                .map(Turno::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal earningsPrevious = Stream.concat(
                        turnos.stream().filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO),
                        canceladosSinReembolso.stream())
                .filter(t -> !t.getFecha().isBefore(previousStart) && !t.getFecha().isAfter(previousEnd))
                .map(Turno::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String earningsChange;
        if (earningsPrevious.compareTo(BigDecimal.ZERO) == 0) {
            earningsChange = earningsCurrent.compareTo(BigDecimal.ZERO) == 0
                    ? "0% " + changeSuffix
                    : "+100% " + changeSuffix;
        } else {
            BigDecimal percentChange = earningsCurrent.subtract(earningsPrevious)
                    .multiply(new BigDecimal("100"))
                    .divide(earningsPrevious, 1, java.math.RoundingMode.HALF_UP);
            if (percentChange.compareTo(BigDecimal.ZERO) > 0) {
                earningsChange = "+" + percentChange + "% " + changeSuffix;
            } else if (percentChange.compareTo(BigDecimal.ZERO) < 0) {
                earningsChange = percentChange + "% " + changeSuffix;
            } else {
                earningsChange = "0% " + changeSuffix;
            }
        }

        // 3. Distinct patients seen within the selected period, and how many of those are new
        // (their first-ever appointment with this médico falls inside the period)
        Set<Long> activePatientIds = turnos.stream()
                .filter(t -> !t.getFecha().isBefore(currentStart) && !t.getFecha().isAfter(currentEnd))
                .map(t -> t.getPaciente().getId())
                .collect(Collectors.toSet());
        int activePatients = activePatientIds.size();

        // "New patient" needs each patient's true first-ever appointment date across the médico's
        // whole history, which the date-bounded `turnos` list above no longer contains — resolved
        // with a lightweight grouped aggregate (2 columns per patient) instead of loading every
        // Turno entity ever created for this médico just to find a MIN(fecha).
        Map<Long, LocalDate> firstAppointmentDate = turnoRepository.findPrimeraFechaPorPaciente(medico.getId(), EstadoTurno.CANCELADO).stream()
                .collect(Collectors.toMap(
                        com.tranqui.app.repository.TurnoRepository.PrimeraFechaPorPaciente::getPacienteId,
                        com.tranqui.app.repository.TurnoRepository.PrimeraFechaPorPaciente::getPrimeraFecha));
        long newPatientsInPeriod = firstAppointmentDate.values().stream()
                .filter(d -> !d.isBefore(currentStart) && !d.isAfter(currentEnd))
                .count();

        String activePatientsChange = "+" + newPatientsInPeriod + " en el período";

        // 4. Inasistencias a turnos in the selected period vs the equivalent previous period:
        // turnos marcados AUSENTE por el médico + turnos cancelados (con o sin reembolso; toda
        // cancelación cuenta como una inasistencia).
        int noShowsCurrent = (int) turnos.stream()
                .filter(t -> t.getAsistencia() == EstadoAsistencia.AUSENTE
                        && !t.getFecha().isBefore(currentStart)
                        && !t.getFecha().isAfter(currentEnd))
                .count()
                + (int) turnosCancelados.stream()
                .filter(t -> !t.getFecha().isBefore(currentStart) && !t.getFecha().isAfter(currentEnd))
                .count();
        int noShowsPrevious = (int) turnos.stream()
                .filter(t -> t.getAsistencia() == EstadoAsistencia.AUSENTE
                        && !t.getFecha().isBefore(previousStart)
                        && !t.getFecha().isAfter(previousEnd))
                .count()
                + (int) turnosCancelados.stream()
                .filter(t -> !t.getFecha().isBefore(previousStart) && !t.getFecha().isAfter(previousEnd))
                .count();

        String noShowsChange;
        int diffNoShows = noShowsCurrent - noShowsPrevious;
        if (diffNoShows > 0) {
            noShowsChange = "+" + diffNoShows + " " + changeSuffix;
        } else if (diffNoShows < 0) {
            noShowsChange = diffNoShows + " " + changeSuffix;
        } else {
            noShowsChange = flatMessage;
        }

        return DashboardStatsDto.builder()
                .sessionsToday(sessionsCurrent)
                .sessionsTodayChange(sessionsChange)
                .earningsThisWeek(earningsCurrent)
                .earningsThisWeekChange(earningsChange)
                .activePatients(activePatients)
                .activePatientsChange(activePatientsChange)
                .noShowsThisMonth(noShowsCurrent)
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
                // Only required for professionals who actually offer in-person consultations —
                // an online-only médico has no consultorio to report. Mirrors the frontend's
                // getMissingRequirements() in App.tsx, which was fixed for this same reason.
                && (!u.isOfrecePresencial() || (u.getDomicilioAtencion() != null && !u.getDomicilioAtencion().trim().isEmpty()))
                && u.getMatriculaTipo() != null && !u.getMatriculaTipo().trim().isEmpty()
                && u.getMatriculaProvincia() != null && !u.getMatriculaProvincia().trim().isEmpty()
                && u.getMatriculaNumero() != null
                && u.getTitulo() != null && !u.getTitulo().trim().isEmpty()
                && u.getSpecialty() != null && !u.getSpecialty().trim().isEmpty()
                && u.getFotoUrl() != null && !u.getFotoUrl().trim().isEmpty()
                && u.getDescripcionPerfil() != null && !u.getDescripcionPerfil().trim().isEmpty()
                && u.getPacientesAtiende() != null && !u.getPacientesAtiende().trim().isEmpty()
                && u.getInstitucionFormacion() != null && !u.getInstitucionFormacion().trim().isEmpty()
                && u.getAniosExperiencia() != null
                && u.getTags() != null && !u.getTags().trim().isEmpty()
                && (u.isOfreceOnline() || u.isOfrecePresencial())
                // "[]" is what the frontend sends when the "Presencia y Experiencia" list is
                // empty — a non-blank string that isn't real content, so it slipped past the old
                // isBlank()-only check.
                && u.getExperiencia() != null && !u.getExperiencia().trim().isEmpty() && !"[]".equals(u.getExperiencia().trim())
                // Without Mercado Pago conectado, the médico can't actually get paid for a
                // particular consultation — only enforced once the integration is really live
                // (mercadopago.enabled=true), so local/dev/test environments aren't blocked.
                && (!mercadoPagoEnabled || u.getMpAccessTokenEncrypted() != null)
                && Boolean.TRUE.equals(u.getVerificadoAdmin());
    }
}
