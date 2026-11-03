package com.tranqui.profesionales.perfil;

import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData.Tarifa;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import com.tranqui.profesionales.datos.Tarifas;
import com.tranqui.profesionales.interno.ClienteInterno;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * El buscador, "Mi perfil" y la configuración de la agenda, con las mismas reglas que
 * {@code MedicoService} del monolito. Los datos de identidad del perfil se guardan en el monolito
 * ({@link ClienteInterno#guardarIdentidad}); el resto, acá.
 */
@Service
public class Perfiles {

    private static final long MAX_FOTO_BYTES = 3L * 1024 * 1024; // 3MB decodificados
    // Para los que nunca configuraron la agenda: 45 era la duración fija histórica.
    static final int DURACION_TURNO_POR_DEFECTO = 45;
    static final int INTERVALO_POR_DEFECTO = 0;
    private static final Pattern DATA_URI = Pattern.compile("^data:image/[^;]+;base64,(.+)$", Pattern.DOTALL);

    // Los servicios con los que arranca un profesional sin tarifas (MedicoService.DEFAULT_TARIFFS).
    private static final List<Tarifa> TARIFAS_POR_DEFECTO = List.of(
            new Tarifa("particular", "Consulta particular", new BigDecimal("60000"), true, false, null, null, null, true, false),
            new Tarifa("sobreturno", "Sobreturno", new BigDecimal("90000"), true, false, null, null, null, true, false),
            new Tarifa("osde", "Obra Social OSDE", new BigDecimal("10500"), true, true, "OSDE", null, null, true, false),
            new Tarifa("receta-fuera", "Receta fuera de turno", new BigDecimal("45000"), true, false, null, null, null, false, true),
            new Tarifa("certificado", "Certificado", new BigDecimal("55000"), true, false, null, null, null, false, false),
            new Tarifa("informe-apto", "Informe / Apto médico", new BigDecimal("165000"), true, false, null, null, null, false, false)
    );

    private final Profesionales profesionales;
    private final Tarifas tarifas;
    private final Publicador publicador;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean mercadoPagoHabilitado;

    public Perfiles(Profesionales profesionales, Tarifas tarifas, Publicador publicador, ClienteInterno interno,
                    TransactionTemplate tx, @Value("${mercadopago.enabled:false}") boolean mercadoPagoHabilitado) {
        this.profesionales = profesionales;
        this.tarifas = tarifas;
        this.publicador = publicador;
        this.interno = interno;
        this.tx = tx;
        this.mercadoPagoHabilitado = mercadoPagoHabilitado;
    }

    /** El buscador público: perfil completo, verificado por el administrador y con la suscripción al día. */
    public List<MedicoDto> buscador() {
        LocalDateTime ahora = LocalDateTime.now();
        return profesionales.profesionales().stream()
                .filter(this::estaVerificado)
                .filter(p -> tieneAcceso(p, ahora))
                .map(this::dto)
                .toList();
    }

    public MedicoDto perfil(String email) {
        return dto(buscar(email));
    }

    public MedicoDto actualizarPerfil(String email, MedicoDto dto) {
        Profesional p = buscar(email);
        PerfilProfesionalData anterior = p.perfil();

        // Mismas validaciones y en el mismo orden que MedicoService#actualizarPerfil.
        String emailContacto = dto.emailContacto() != null ? dto.emailContacto().trim() : null;
        if (emailContacto != null && !emailContacto.isEmpty() && !emailContacto.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email de contacto no tiene un formato válido.");
        }
        String domicilio = dto.domicilioAtencion() != null ? dto.domicilioAtencion().trim() : null;
        if (domicilio != null && !domicilio.isEmpty() && (domicilio.length() < 8 || domicilio.length() > 140)) {
            throw new IllegalArgumentException(
                    "El domicilio de atención debe tener entre 8 y 140 caracteres. Usá el buscador de direcciones y elegí una sugerencia en vez de pegar la dirección completa.");
        }
        String torre = null;
        String piso = null;
        String depto = null;
        String barrio = null;
        if (dto.ofrecePresencial()) {
            torre = recortar(dto.domicilioAtencionTorre(), 50);
            piso = recortar(dto.domicilioAtencionPiso(), 20);
            depto = recortar(dto.domicilioAtencionDepto(), 20);
            barrio = recortar(dto.domicilioAtencionBarrio(), 100);
        }
        if (dto.fotoUrl() != null && bytesDecodificados(dto.fotoUrl()) > MAX_FOTO_BYTES) {
            throw new IllegalArgumentException(
                    "La foto de perfil es demasiado grande (máx. " + (MAX_FOTO_BYTES / (1024 * 1024)) + "MB). Elegí una imagen más liviana.");
        }
        String refeps = dto.codigoRefeps() != null ? dto.codigoRefeps().trim() : null;
        if (refeps != null && !refeps.isEmpty() && !refeps.matches("\\d{12}")) {
            throw new IllegalArgumentException(
                    "El código REFEPS debe tener exactamente 12 dígitos numéricos (lo asigna SISA al matricularte, no se inventa). Podés consultarlo en sisa.msal.gov.ar.");
        }
        if (dto.selloLinea1() != null && dto.selloLinea1().length() > 40) {
            throw new IllegalArgumentException("La línea 1 del sello no puede superar los 40 caracteres.");
        }
        if (dto.selloLinea2() != null && dto.selloLinea2().length() > 40) {
            throw new IllegalArgumentException("La línea 2 del sello no puede superar los 40 caracteres.");
        }
        if (dto.selloLinea3() != null && dto.selloLinea3().length() > 25) {
            throw new IllegalArgumentException("La línea 3 del sello no puede superar los 25 caracteres.");
        }

        // Matrícula: con matriculaInfo pisa lo que trae; sin ella solo el número de matrícula "viejo".
        String matricula = anterior.matricula();
        String matriculaTipo = anterior.matriculaTipo();
        String matriculaProvincia = anterior.matriculaProvincia();
        Integer matriculaNumero = anterior.matriculaNumero();
        String matriculaEspecialidad = anterior.matriculaEspecialidad();
        String asocTipo = anterior.matriculaAsocTipo();
        String asocProvincia = anterior.matriculaAsocProvincia();
        Integer asocNumero = anterior.matriculaAsocNumero();
        if (dto.matriculaInfo() != null) {
            MedicoDto.MatriculaInfoDto m = dto.matriculaInfo();
            matriculaTipo = m.tipo();
            matriculaProvincia = m.provincia();
            matriculaNumero = m.numero();
            if (m.especialidad() != null) {
                matriculaEspecialidad = m.especialidad().textoLibre();
            }
            if (m.asociada() != null) {
                asocTipo = m.asociada().tipo();
                asocProvincia = m.asociada().provincia();
                asocNumero = m.asociada().numero();
            }
            if (m.numero() != null) {
                matricula = String.valueOf(m.numero());
            }
        } else {
            matricula = dto.matricula();
        }

        MedicoDto.RedesSocialesDto redes = dto.redesSociales();
        PerfilProfesionalData nuevo = new PerfilProfesionalData(
                emailContacto != null && !emailContacto.isEmpty() ? emailContacto : null,
                domicilio != null && !domicilio.isEmpty() ? domicilio : null,
                dto.domicilioLat(), dto.domicilioLng(), torre, piso, depto, barrio,
                matricula, matriculaTipo, matriculaProvincia, matriculaNumero, matriculaEspecialidad,
                asocTipo, asocProvincia, asocNumero,
                dto.degree(), dto.specialty(), dto.cuit(), dto.price(), dto.color(),
                dto.ofreceOnline(), dto.ofrecePresencial(), dto.experiencia(), dto.publicaciones(),
                redes != null ? redes.instagram() : anterior.instagramUrl(),
                redes != null ? redes.facebook() : anterior.facebookUrl(),
                redes != null ? redes.linkedin() : anterior.linkedinUrl(),
                redes != null ? redes.sitioWeb() : anterior.sitioWebUrl(),
                refeps != null && !refeps.isEmpty() ? refeps : null,
                dto.selloLinea1(), dto.selloLinea2(), dto.selloLinea3(),
                dto.tags() != null ? String.join(",", dto.tags()) : anterior.tags(),
                dto.descripcionPerfil(), dto.institucionFormacion(), dto.aniosExperiencia(),
                dto.pacientesAtiende() != null ? String.join(",", dto.pacientesAtiende()) : anterior.pacientesAtiende(),
                anterior.duracionTurnoMinutos(), anterior.intervaloEntreTurnosMinutos());

        // La identidad primero, en el monolito. Si no responde no se guarda nada (el profesional reintenta).
        ClienteInterno.ProfesionalMonolito monolito;
        try {
            monolito = interno.guardarIdentidad(p.id(), new ClienteInterno.Identidad(dto.nombre(), dto.apellido(),
                    dto.telefono() != null ? dto.telefono().trim() : null, dto.sexo(), dto.fechaNacimiento(), dto.cuil(),
                    dto.tipoDocumento(), dto.numeroDocumento(), dto.fotoUrl()));
        } catch (RestClientException e) {
            throw new ServicioNoDisponibleException("No se pudo guardar el perfil en este momento. Probá de nuevo en unos minutos.", e);
        }

        tx.executeWithoutResult(estado -> {
            if (monolito != null) {
                profesionales.guardarDatosMonolito(monolito.datos().conFoto(dto.fotoUrl()));
            }
            int version = profesionales.guardarPerfil(p.id(), nuevo);
            publicador.perfil(buscar(email), version);
            if (dto.tariffs() != null) {
                guardarTarifas(p, dto.tariffs());
            }
        });
        return dto(buscar(email));
    }

    private void guardarTarifas(Profesional p, List<MedicoDto.TarifaDto> enviadas) {
        // Un psicólogo (plan sin recetas_electronicas) no puede tener servicios de receta: se fuerza acá también.
        boolean puedeRecetar = p.puedeRecetar();
        Set<String> ids = enviadas.stream().map(MedicoDto.TarifaDto::id).collect(Collectors.toSet());
        for (MedicoDto.TarifaDto t : enviadas) {
            // "Requiere obra social" si tiene una asignada, si es uno de los ids viejos genéricos o si se marcó.
            boolean esServicioObraSocial = "obra_social".equals(t.id()) || "osde".equals(t.id());
            String obraSocial = t.obraSocial() != null ? t.obraSocial().trim() : null;
            if (obraSocial != null && obraSocial.isEmpty()) {
                obraSocial = null;
            }
            boolean requiereObraSocial = esServicioObraSocial || obraSocial != null || t.requiereObraSocial();
            boolean esReceta = !t.requiereAgenda() && t.esReceta() && puedeRecetar;
            tarifas.guardar(p.id(), new Tarifa(t.id(), t.label(), t.price(), t.enabled(), requiereObraSocial, obraSocial,
                    t.precioOnline(), t.precioPresencial(), t.requiereAgenda(), esReceta));
        }
        tarifas.eliminarSalvo(p.id(), ids);
        publicador.tarifas(p.id(), profesionales.subirVersionTarifas(p.id()), tarifas.deProfesional(p.id()));
    }

    public MedicoDto actualizarConfigAgenda(String email, Integer duracion, Integer intervalo) {
        Profesional p = buscar(email);
        if (duracion != null && duracion <= 0) {
            throw new IllegalArgumentException("La duración del turno debe ser mayor a 0 minutos.");
        }
        if (intervalo != null && intervalo < 0) {
            throw new IllegalArgumentException("El intervalo entre turnos no puede ser negativo.");
        }
        PerfilProfesionalData a = p.perfil();
        PerfilProfesionalData nuevo = new PerfilProfesionalData(a.emailContacto(), a.domicilioAtencion(), a.domicilioLat(),
                a.domicilioLng(), a.domicilioAtencionTorre(), a.domicilioAtencionPiso(), a.domicilioAtencionDepto(),
                a.domicilioAtencionBarrio(), a.matricula(), a.matriculaTipo(), a.matriculaProvincia(), a.matriculaNumero(),
                a.matriculaEspecialidad(), a.matriculaAsocTipo(), a.matriculaAsocProvincia(), a.matriculaAsocNumero(),
                a.titulo(), a.especialidad(), a.cuit(), a.precio(), a.color(), a.ofreceOnline(), a.ofrecePresencial(),
                a.experiencia(), a.publicaciones(), a.instagramUrl(), a.facebookUrl(), a.linkedinUrl(), a.sitioWebUrl(),
                a.codigoRefeps(), a.selloLinea1(), a.selloLinea2(), a.selloLinea3(), a.tags(), a.descripcionPerfil(),
                a.institucionFormacion(), a.aniosExperiencia(), a.pacientesAtiende(),
                duracion != null ? duracion : a.duracionTurnoMinutos(),
                intervalo != null ? intervalo : a.intervaloEntreTurnosMinutos());
        tx.executeWithoutResult(estado -> {
            int version = profesionales.guardarPerfil(p.id(), nuevo);
            publicador.perfil(buscar(email), version);
        });
        return dto(buscar(email));
    }

    Profesional buscar(String email) {
        return profesionales.porEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado con email: " + email));
    }

    /** {@code MedicoService#isMedicoVerificado}: perfil completo, Mercado Pago (si es real) y la verificación. */
    boolean estaVerificado(Profesional u) {
        PerfilProfesionalData p = u.perfil();
        return lleno(u.nombre()) && lleno(u.apellido()) && lleno(u.sexo()) && u.fechaNacimiento() != null
                && u.cuil() != null && lleno(u.email()) && lleno(u.tipoDocumento()) && u.numeroDocumento() != null
                // Solo para quien atiende presencial: el que atiende online no tiene consultorio.
                && (!p.ofrecePresencial() || lleno(p.domicilioAtencion()))
                && lleno(p.matriculaTipo()) && lleno(p.matriculaProvincia()) && p.matriculaNumero() != null
                && lleno(p.titulo()) && lleno(p.especialidad()) && lleno(u.fotoUrl())
                && lleno(p.descripcionPerfil()) && lleno(p.pacientesAtiende()) && lleno(p.institucionFormacion())
                && p.aniosExperiencia() != null && lleno(p.tags())
                && (p.ofreceOnline() || p.ofrecePresencial())
                // "[]" es lo que manda el frontend con la lista de experiencia vacía.
                && lleno(p.experiencia()) && !"[]".equals(p.experiencia().trim())
                && (!mercadoPagoHabilitado || u.mpVinculada())
                && Boolean.TRUE.equals(u.verificadoAdmin());
    }

    /**
     * {@code SubscriptionService#isAccessAllowed} con lo que copió la sincronización: activa, o cancelada con el
     * período pagado vigente, siempre hasta {@code currentPeriodEnd}.
     */
    static boolean tieneAcceso(Profesional p, LocalDateTime ahora) {
        if (!"PSIQUIATRA".equals(p.rol())) {
            return true;
        }
        LocalDateTime hasta = p.suscripcionPeriodoHasta();
        boolean vigente = hasta != null && hasta.isAfter(ahora);
        boolean estadoValido = "ACTIVE".equals(p.suscripcionEstado()) || ("CANCELLED".equals(p.suscripcionEstado()) && vigente);
        return estadoValido && hasta != null && vigente;
    }

    MedicoDto dto(Profesional m) {
        List<Tarifa> propias = tarifas.deProfesional(m.id());
        if (propias.isEmpty()) {
            propias = tarifasPorDefecto(m);
        }
        PerfilProfesionalData p = m.perfil();
        List<MedicoDto.TarifaDto> tarifasDto = propias.stream()
                .filter(t -> !"primera".equals(t.servicioId()) && !"certificado-laboral".equals(t.servicioId()))
                .map(t -> new MedicoDto.TarifaDto(t.servicioId(), t.label(), t.precio(), t.habilitado(), t.requiereObraSocial(),
                        t.obraSocial(), t.precioOnline(), t.precioPresencial(), t.requiereAgenda(), t.esReceta()))
                .toList();
        // El "Desde $X" público sale de la tarifa "particular", no del precio viejo del perfil.
        BigDecimal precio = tarifasDto.stream()
                .filter(t -> "particular".equals(t.id()) && t.enabled())
                .map(MedicoDto.TarifaDto::price)
                .findFirst()
                .orElse(p.precio() != null ? p.precio() : new BigDecimal("60000"));

        MedicoDto.MatriculaInfoDto matricula = null;
        if (p.matriculaTipo() != null || p.matriculaNumero() != null) {
            matricula = new MedicoDto.MatriculaInfoDto(p.matriculaTipo(), p.matriculaProvincia(), p.matriculaNumero(),
                    new MedicoDto.EspecialidadDto(p.matriculaEspecialidad()),
                    new MedicoDto.AsociadaDto(p.matriculaAsocTipo(), p.matriculaAsocProvincia(), p.matriculaAsocNumero()));
        }
        String nombreCompleto = m.nombre();
        if (m.apellido() != null && !m.apellido().trim().isEmpty()) {
            nombreCompleto = nombreCompleto + " " + m.apellido();
        }
        return new MedicoDto(m.id(), nombreCompleto, m.nombre(), m.email(), p.emailContacto(), m.telefono(), iniciales(m.nombre()),
                p.titulo() != null ? p.titulo() : "Médico/a", p.especialidad() != null ? p.especialidad() : "General",
                m.profession(), p.matricula(), p.cuit(), precio, lista(p.tags()), p.color() != null ? p.color() : "#E8F5EE",
                m.fotoUrl(), p.ofreceOnline(), p.ofrecePresencial(), tarifasDto, m.apellido(), m.sexo(), m.fechaNacimiento(),
                m.cuil(), m.tipoDocumento(), m.numeroDocumento(), p.domicilioAtencion(), p.domicilioLat(), p.domicilioLng(),
                p.domicilioAtencionTorre(), p.domicilioAtencionPiso(), p.domicilioAtencionDepto(), p.domicilioAtencionBarrio(),
                matricula, estaVerificado(m), m.verificadoAdmin(), p.experiencia(), p.publicaciones(),
                new MedicoDto.RedesSocialesDto(p.instagramUrl(), p.facebookUrl(), p.linkedinUrl(), p.sitioWebUrl()),
                p.descripcionPerfil(), lista(p.pacientesAtiende()), p.institucionFormacion(), p.aniosExperiencia(),
                p.duracionTurnoMinutos() != null ? p.duracionTurnoMinutos() : DURACION_TURNO_POR_DEFECTO,
                p.intervaloEntreTurnosMinutos() != null ? p.intervaloEntreTurnosMinutos() : INTERVALO_POR_DEFECTO,
                p.codigoRefeps(), p.selloLinea1(), p.selloLinea2(), p.selloLinea3());
    }

    /**
     * Un profesional sin tarifas arranca con las de siempre, como al abrir "Honorarios y servicios" en el
     * monolito. La de receta solo habilitada si su plan permite recetar. Se avisa al monolito (turnos).
     */
    private List<Tarifa> tarifasPorDefecto(Profesional m) {
        boolean puedeRecetar = m.puedeRecetar();
        List<Tarifa> iniciales = TARIFAS_POR_DEFECTO.stream()
                .map(t -> new Tarifa(t.servicioId(), t.label(), t.precio(), t.esReceta() ? puedeRecetar : t.habilitado(),
                        t.requiereObraSocial(), t.obraSocial(), t.precioOnline(), t.precioPresencial(), t.requiereAgenda(),
                        t.esReceta() && puedeRecetar))
                .toList();
        tx.executeWithoutResult(estado -> {
            for (Tarifa t : iniciales) {
                tarifas.guardar(m.id(), t);
            }
            publicador.tarifas(m.id(), profesionales.subirVersionTarifas(m.id()), tarifas.deProfesional(m.id()));
        });
        return tarifas.deProfesional(m.id());
    }

    private static String iniciales(String nombre) {
        if (nombre == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : nombre.split(" ")) {
            if (!parte.isEmpty() && !parte.contains("Lic.") && !parte.contains("Dr.") && !parte.contains("Dra.")) {
                sb.append(parte.charAt(0));
            }
        }
        String iniciales = sb.toString().toUpperCase();
        if (iniciales.isEmpty()) {
            iniciales = nombre.substring(0, Math.min(2, nombre.length())).toUpperCase();
        }
        return iniciales;
    }

    private static List<String> lista(String separadaPorComas) {
        if (separadaPorComas == null || separadaPorComas.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(separadaPorComas.split(","));
    }

    private static boolean lleno(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String recortar(String valor, int largoMaximo) {
        String recortado = valor != null ? valor.trim() : null;
        if (recortado == null || recortado.isEmpty()) {
            return null;
        }
        if (recortado.length() > largoMaximo) {
            throw new IllegalArgumentException("Uno de los campos de la dirección de atención supera el largo máximo permitido ("
                    + largoMaximo + " caracteres).");
        }
        return recortado;
    }

    /** {@code ImageUtils#decodedByteSize}: el tamaño de la imagen sin decodificar el base64. */
    static long bytesDecodificados(String dataUri) {
        Matcher m = DATA_URI.matcher(dataUri);
        if (!m.matches()) {
            return dataUri.length();
        }
        String b64 = m.group(1);
        int relleno = b64.endsWith("==") ? 2 : b64.endsWith("=") ? 1 : 0;
        return (long) (b64.length() / 4.0 * 3) - relleno;
    }
}
