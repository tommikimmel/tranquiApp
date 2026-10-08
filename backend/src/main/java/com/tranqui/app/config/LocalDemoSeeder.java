package com.tranqui.app.config;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import com.tranqui.app.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Datos de demo realistas para el entorno local (docker-compose.local.yml): profesionales de
 * distinto tipo, pacientes, turnos pasados, de hoy y futuros, pagos, chats, documentos,
 * notificaciones y tickets, como si la app estuviera en uso.
 *
 * Solo existe con el perfil "local" Y app.demo-data=true: producción nunca corre con ese perfil y
 * la propiedad es false por defecto (ver application.yml). Todos los datos son ficticios y las
 * fechas son relativas al día en que se levanta el entorno. Es idempotente: si los datos ya están,
 * no hace nada (para empezar de cero: npm run local:reset).
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);
    static final String PASSWORD = "admin123";
    static final String MARCADOR = "lucia.fernandez@demo.tranqui";

    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadRepository disponibilidadRepository;
    private final TarifaMedicoRepository tarifaRepository;
    private final TurnoRepository turnoRepository;
    private final PagoRepository pagoRepository;
    private final MensajeRepository mensajeRepository;
    private final NotificacionRepository notificacionRepository;
    private final TicketRepository ticketRepository;
    private final TicketMensajeRepository ticketMensajeRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;

    private final Random random = new Random(42);
    private String passwordHash;

    public LocalDemoSeeder(UsuarioRepository usuarioRepository, DisponibilidadRepository disponibilidadRepository,
                           TarifaMedicoRepository tarifaRepository, TurnoRepository turnoRepository,
                           PagoRepository pagoRepository, MensajeRepository mensajeRepository,
                           NotificacionRepository notificacionRepository, TicketRepository ticketRepository,
                           TicketMensajeRepository ticketMensajeRepository, PlanRepository planRepository,
                           SubscriptionRepository subscriptionRepository, PasswordEncoder passwordEncoder,
                           EncryptionUtil encryptionUtil) {
        this.usuarioRepository = usuarioRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.tarifaRepository = tarifaRepository;
        this.turnoRepository = turnoRepository;
        this.pagoRepository = pagoRepository;
        this.mensajeRepository = mensajeRepository;
        this.notificacionRepository = notificacionRepository;
        this.ticketRepository = ticketRepository;
        this.ticketMensajeRepository = ticketMensajeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionUtil = encryptionUtil;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (usuarioRepository.findByEmail(MARCADOR).isPresent()) {
            log.info("Datos de demo ya sembrados ({} existe): no se vuelven a crear.", MARCADOR);
            return;
        }
        passwordHash = passwordEncoder.encode(PASSWORD);

        // --- Profesionales ---
        Usuario lucia = guardar(profesional("lucia.fernandez@demo.tranqui", "Lucía", "Fernández", "F", "psicologo",
                "Licenciada en Psicología", "Psicología clínica", "Ansiedad,Estrés,Duelo,Vínculos", "Adolescentes,Adultos",
                "Psicóloga clínica con enfoque cognitivo-conductual. Trabajo con adolescentes y adultos en ansiedad, estrés y procesos de duelo.",
                "Universidad Nacional de La Plata", 9, true, true, "Av. Cabildo 1820, Belgrano, CABA", -34.5627, -58.4563,
                "https://images.unsplash.com/photo-1594824476967-48c8b964273f?auto=format&fit=crop&q=80&w=300", 31544, "MP_psico"));
        lucia.setGoogleCalendarConnected(true);
        Usuario mateo = guardar(profesional("mateo.suarez@demo.tranqui", "Mateo", "Suárez", "M", "psicologo",
                "Licenciado en Psicología", "Psicología de adolescentes", "Adolescentes,Ansiedad,Depresión", "Adolescentes,Adultos",
                "Acompaño a adolescentes y adultos jóvenes en procesos de ansiedad, autoestima y orientación. Atención 100% online.",
                "Universidad de Buenos Aires", 6, true, false, null, null, null,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=300", 28803, "MP_psico"));
        Usuario martin = guardar(profesional("martin.rios@demo.tranqui", "Martín", "Ríos", "M", "psiquiatra",
                "Médico Psiquiatra", "Psiquiatría de adultos", "Ansiedad,Depresión,Insomnio,Ataques de pánico", "Adultos,Adultos mayores",
                "Acompaño a adultos con ansiedad, depresión y trastornos del sueño, combinando psicoterapia y, cuando hace falta, tratamiento farmacológico.",
                "Universidad de Buenos Aires", 12, true, true, "Av. Santa Fe 2450, Palermo, CABA", -34.5956, -58.4021,
                "https://images.unsplash.com/photo-1622253692010-333f2da6031d?auto=format&fit=crop&q=80&w=300", 48127, "MN"));
        Usuario sofia = guardar(profesional("sofia.medina@demo.tranqui", "Sofía", "Medina", "F", "psicologo",
                "Licenciada en Psicología", "Terapia de pareja", "Pareja,Vínculos,Estrés", "Adultos",
                "Psicóloga sistémica. Trabajo con adultos y parejas en conflictos vinculares.",
                "Universidad Nacional de Córdoba", 15, true, true, "Av. Rivadavia 5100, Caballito, CABA", -34.6186, -58.4370,
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&q=80&w=300", 35210, "MP_psico"));
        // Recién registrado: sin verificar y con el perfil incompleto (sin foto, descripción ni temas).
        Usuario nuevo = profesional("tomas.ledesma@demo.tranqui", "Tomás", "Ledesma", "M", "psicologo",
                "Licenciado en Psicología", "Psicología clínica", null, null, null,
                null, null, true, false, null, null, null, null, 40102, "MP_psico");
        nuevo.setVerificadoAdmin(false);
        nuevo.setMpAccessTokenEncrypted(null);
        guardar(nuevo);

        for (Usuario p : List.of(lucia, mateo, martin, sofia)) {
            agenda(p);
        }
        tarifas(lucia, 45000, 15000, false);
        tarifas(mateo, 40000, null, false);
        tarifas(martin, 60000, 18000, true);
        tarifas(sofia, 50000, null, false);

        suscripcion(lucia, "consultorio", SubscriptionStatus.ACTIVE, "monthly", LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(20));
        suscripcion(mateo, "consultorio", SubscriptionStatus.ACTIVE, "annual", LocalDateTime.now().minusMonths(2), LocalDateTime.now().plusMonths(10));
        suscripcion(martin, "clinico", SubscriptionStatus.ACTIVE, "monthly", LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(25));
        // Vencida: al entrar ve la pantalla para reactivar y no aparece en el buscador.
        suscripcion(sofia, "consultorio", SubscriptionStatus.CANCELLED, "monthly", LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(10));

        // --- Pacientes ---
        List<Usuario> pacientes = new ArrayList<>();
        pacientes.add(guardar(paciente("valentina.gomez@demo.tranqui", "Valentina", "Gómez", "F", 38999111, "OSDE", "61234567801")));
        pacientes.add(guardar(paciente("juan.martinez@demo.tranqui", "Juan", "Martínez", "M", 40123456, null, null)));
        pacientes.add(guardar(paciente("camila.sosa@demo.tranqui", "Camila", "Sosa", "F", 41555222, null, null)));
        pacientes.add(guardar(paciente("nicolas.paz@demo.tranqui", "Nicolás", "Paz", "M", 37888999, "Swiss Medical", "8001234")));
        pacientes.add(guardar(paciente("florencia.ruiz@demo.tranqui", "Florencia", "Ruiz", "F", 42111333, null, null)));
        pacientes.add(guardar(paciente("agustin.vera@demo.tranqui", "Agustín", "Vera", "M", 39444777, null, null)));
        pacientes.add(guardar(paciente("martina.lopez@demo.tranqui", "Martina", "López", "F", 43222444, null, null)));
        // Sin datos personales: para probar el flujo de completar perfil antes de reservar.
        Usuario sinDatos = Usuario.builder().nombre("Pablo").email("pablo.sindatos@demo.tranqui").password(passwordHash)
                .rol(Rol.PACIENTE).emailVerificado(true).terminosAceptadosEn(LocalDateTime.now().minusDays(1)).build();
        guardar(sinDatos);

        // --- Turnos, pagos y documentos ---
        int turnos = 0;
        turnos += historial(lucia, pacientes.subList(0, 5), 45000);
        // Martina López (índice 6) queda sin turnos: la app no deja reservar a un paciente con un
        // turno activo, así que es la cuenta demo para probar una reserva de punta a punta.
        turnos += historial(mateo, pacientes.subList(2, 6), 40000);
        turnos += historial(martin, pacientes.subList(1, 6), 60000);
        documento(lucia, pacientes.get(1), false);
        documento(martin, pacientes.get(3), false);
        documento(martin, pacientes.get(0), true);

        // --- Chats ---
        chat(lucia, pacientes.get(0), List.of(
                "Hola Lucía, ¿podemos pasar el turno del jueves a la tarde?",
                "Hola Valentina, sí. Te propongo el jueves a las 16. ¿Te sirve?",
                "Perfecto, gracias!"), true);
        chat(lucia, pacientes.get(1), List.of(
                "Lucía, te consulto: ¿el certificado sale con la fecha de la sesión?",
                "Sí, Juan. Lo vas a recibir por mail en el día."), false);
        chat(martin, pacientes.get(3), List.of(
                "Doctor, desde que empecé la medicación duermo mejor.",
                "Muy bien, Nicolás. Lo charlamos en el control de la semana que viene."), true);
        chat(mateo, pacientes.get(4), List.of("Hola Mateo, ¿atendés por obra social?"), false);

        // --- Notificaciones del profesional ---
        notificacion(lucia, "Nuevo turno reservado", "Valentina Gómez reservó un turno para el jueves.", "TURNO_RESERVADO", false);
        notificacion(lucia, "Pago acreditado", "Se acreditó el pago de la consulta de Juan Martínez.", "PAGO_APROBADO", false);
        notificacion(lucia, "Nuevo mensaje", "Juan Martínez te escribió por el chat.", "NUEVO_MENSAJE", true);
        notificacion(martin, "Documento solicitado", "Nicolás Paz pidió un certificado.", "DOCUMENTO_SOLICITADO", false);

        // --- Tickets de soporte ---
        Usuario admin = usuarioRepository.findByEmail("admin@tranqui.com").orElse(null);
        ticket(pacientes.get(1), "Problema con un pago", "Pagué el turno pero sigue figurando pendiente.",
                admin, "Hola Juan, ya quedó acreditado. Gracias por avisarnos.", TicketEstado.RESUELTO);
        ticket(lucia, "No puedo cambiar mi foto de perfil", "Al subir la foto me aparece un error.",
                null, null, TicketEstado.PENDIENTE);

        log.info("Datos de demo sembrados: 5 profesionales, {} pacientes, {} turnos. Contraseña de todas las cuentas: {}",
                pacientes.size() + 1, turnos + 3, PASSWORD);
    }

    // ================================================================================

    private Usuario guardar(Usuario u) {
        return usuarioRepository.save(u);
    }

    private Usuario profesional(String email, String nombre, String apellido, String sexo, String profession,
                                String titulo, String especialidad, String tags, String pacientesAtiende,
                                String descripcion, String institucion, Integer anios, boolean online, boolean presencial,
                                String domicilio, Double lat, Double lng, String foto, int matricula, String matriculaTipo) throws Exception {
        return Usuario.builder()
                .nombre(nombre).apellido(apellido).email(email).password(passwordHash)
                .rol(Rol.PSIQUIATRA).profession(profession).sexo(sexo)
                .fechaNacimiento(LocalDate.of(1980 + random.nextInt(12), 1 + random.nextInt(12), 1 + random.nextInt(28)))
                .tipoDocumento("DNI").numeroDocumento(25000000 + random.nextInt(9000000))
                .cuil(27000000000L + random.nextInt(900000000)).telefono("+5411" + (40000000 + random.nextInt(9999999)))
                .titulo(titulo).specialty(especialidad).tags(tags).pacientesAtiende(pacientesAtiende)
                .descripcionPerfil(descripcion).institucionFormacion(institucion).aniosExperiencia(anios)
                .experiencia(descripcion == null ? null : "[{\"id\":\"1\",\"nombreLugar\":\"Consultorio particular\",\"desde\":\"2017\",\"hasta\":\"Actualidad\",\"descripcion\":\"Atención de pacientes en psicoterapia individual.\"}]")
                .ofreceOnline(online).ofrecePresencial(presencial)
                .domicilioAtencion(domicilio).domicilioLat(lat).domicilioLng(lng)
                .matriculaTipo(matriculaTipo).matriculaProvincia("CABA").matriculaNumero(matricula).matricula(String.valueOf(matricula))
                .fotoUrl(foto).precio(BigDecimal.valueOf(45000))
                .duracionTurnoMinutos(50).intervaloEntreTurnosMinutos(10)
                .verificadoAdmin(true).emailVerificado(true)
                .mpAccessTokenEncrypted(encryptionUtil.encrypt("TEST-demo-mock-access-token"))
                .mpUserId("999999999")
                .terminosAceptadosEn(LocalDateTime.now().minusMonths(1))
                .build();
    }

    private Usuario paciente(String email, String nombre, String apellido, String sexo, int dni, String obraSocial, String afiliado) {
        return Usuario.builder()
                .nombre(nombre).apellido(apellido).email(email).password(passwordHash).rol(Rol.PACIENTE).sexo(sexo)
                .fechaNacimiento(LocalDate.of(1985 + random.nextInt(18), 1 + random.nextInt(12), 1 + random.nextInt(28)))
                .tipoDocumento("DNI").numeroDocumento(dni).dni(String.valueOf(dni))
                .cuil(20000000000L + dni * 10L).telefono("+5411" + (50000000 + random.nextInt(9999999)))
                .direccion("Av. Corrientes " + (1000 + random.nextInt(4000)) + ", CABA")
                .obraSocial(obraSocial).numAfiliado(afiliado)
                .emailVerificado(true).terminosAceptadosEn(LocalDateTime.now().minusMonths(1))
                .build();
    }

    private void agenda(Usuario medico) {
        for (int dia = 1; dia <= 5; dia++) {
            disponibilidadRepository.save(Disponibilidad.builder().medico(medico).diaSemana(dia)
                    .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(13, 0)).build());
            disponibilidadRepository.save(Disponibilidad.builder().medico(medico).diaSemana(dia)
                    .horaInicio(LocalTime.of(14, 0)).horaFin(LocalTime.of(19, 0)).build());
        }
    }

    private void tarifas(Usuario medico, int particular, Integer obraSocial, boolean conReceta) {
        tarifaRepository.save(tarifa(medico, "particular", "Consulta particular", particular, true, false, null, false));
        if (obraSocial != null) {
            tarifaRepository.save(tarifa(medico, "osde", "Obra Social OSDE", obraSocial, true, true, "OSDE", false));
        }
        tarifaRepository.save(tarifa(medico, "certificado", "Certificado", 30000, false, false, null, false));
        if (conReceta) {
            tarifaRepository.save(tarifa(medico, "receta-fuera", "Receta fuera de turno", 25000, false, false, null, true));
        }
    }

    private TarifaMedico tarifa(Usuario medico, String id, String label, int precio, boolean agenda,
                                boolean requiereOs, String obraSocial, boolean esReceta) {
        return TarifaMedico.builder().medico(medico).servicioId(id).label(label).precio(BigDecimal.valueOf(precio))
                .habilitado(true).requiereAgenda(agenda).requiereObraSocial(requiereOs).obraSocial(obraSocial).esReceta(esReceta)
                .build();
    }

    private void suscripcion(Usuario medico, String planCode, SubscriptionStatus status, String ciclo,
                             LocalDateTime inicio, LocalDateTime fin) {
        Plan plan = planRepository.findByCode(planCode).orElse(null);
        if (plan == null) {
            log.warn("Plan '{}' no encontrado: {} queda sin suscripción.", planCode, medico.getEmail());
            return;
        }
        boolean anual = "annual".equals(ciclo);
        subscriptionRepository.save(Subscription.builder().professional(medico).plan(plan).status(status)
                .billingSource(BillingSource.MANUAL_TRANSFER).billingCycle(ciclo).seats(1)
                .amountArs(anual && plan.getPriceArsAnual() != null ? plan.getPriceArsAnual() : plan.getPriceArs())
                .currentPeriodStart(inicio).currentPeriodEnd(fin)
                .cancelAtPeriodEnd(status == SubscriptionStatus.CANCELLED)
                .cancelledAt(status == SubscriptionStatus.CANCELLED ? fin.minusDays(5) : null)
                .build());
    }

    /**
     * Turnos de las últimas 3 semanas y las próximas 2 en días hábiles: pasados con asistencia,
     * cancelados (con y sin reembolso), de hoy (uno ya atendido y uno dentro de la hora actual),
     * futuros pagos y uno pendiente de pago.
     */
    private int historial(Usuario medico, List<Usuario> pacientes, int precio) {
        int[] horas = {9, 10, 11, 12, 14, 15, 16, 17};
        int creados = 0;
        LocalDate hoy = LocalDate.now();
        for (int offset = -21; offset <= 14; offset += 3) {
            LocalDate fecha = habil(hoy.plusDays(offset));
            if (fecha.equals(hoy)) continue;
            Usuario paciente = pacientes.get(Math.floorMod(offset, pacientes.size()));
            LocalTime hora = LocalTime.of(horas[Math.floorMod(offset * 7, horas.length)], 0);
            boolean pasado = fecha.isBefore(hoy);
            EstadoTurno estado = EstadoTurno.CONFIRMADO;
            EstadoAsistencia asistencia = EstadoAsistencia.ESPERANDO;
            EstadoPago pago = EstadoPago.APROBADO;
            if (pasado) {
                asistencia = offset == -15 ? EstadoAsistencia.AUSENTE : EstadoAsistencia.COMPLETADA;
                if (offset == -12) { estado = EstadoTurno.CANCELADO; pago = EstadoPago.REEMBOLSADO; asistencia = EstadoAsistencia.ESPERANDO; }
            } else if (offset == 6) {
                estado = EstadoTurno.CANCELADO; pago = EstadoPago.APROBADO; // cancelado con menos de 48 hs: sin reembolso
            }
            turno(medico, paciente, fecha, hora, offset % 2 == 0 && medico.isOfrecePresencial() ? Modalidad.PRESENCIAL : modalidad(medico),
                    estado, asistencia, precio, pago, LocalDateTime.of(fecha.minusDays(4), LocalTime.of(20, 15)));
            creados++;
        }
        // Hoy: uno temprano ya atendido y uno en la hora actual (para el botón de Meet).
        LocalTime ahora = LocalTime.now();
        turno(medico, pacientes.get(0), hoy, LocalTime.of(Math.max(8, ahora.getHour() - 3), 0), modalidad(medico),
                EstadoTurno.CONFIRMADO, EstadoAsistencia.COMPLETADA, precio, EstadoPago.APROBADO, LocalDateTime.now().minusDays(2));
        if (ahora.getHour() < 23) {
            turno(medico, pacientes.get(1), hoy, LocalTime.of(ahora.getHour() + 1, 0), Modalidad.ONLINE,
                    EstadoTurno.CONFIRMADO, EstadoAsistencia.ESPERANDO, precio, EstadoPago.APROBADO, LocalDateTime.now().minusDays(1));
            creados++;
        }
        // Pendiente de pago recién creado (el limpiador lo expira a los 5 minutos, como en producción).
        turno(medico, pacientes.get(2), habil(hoy.plusDays(5)), LocalTime.of(18, 0), modalidad(medico),
                EstadoTurno.PENDIENTE_PAGO, EstadoAsistencia.ESPERANDO, precio, null, LocalDateTime.now());
        return creados + 2;
    }

    private Modalidad modalidad(Usuario medico) {
        return medico.isOfreceOnline() ? Modalidad.ONLINE : Modalidad.PRESENCIAL;
    }

    private LocalDate habil(LocalDate fecha) {
        while (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fecha = fecha.plusDays(1);
        }
        return fecha;
    }

    private void turno(Usuario medico, Usuario paciente, LocalDate fecha, LocalTime hora, Modalidad modalidad,
                       EstadoTurno estado, EstadoAsistencia asistencia, int precio, EstadoPago estadoPago, LocalDateTime creado) {
        Turno t = Turno.builder().medico(medico).paciente(paciente).fecha(fecha).horaInicio(hora).horaFin(hora.plusMinutes(50))
                .tipo(TipoTurno.PARTICULAR).modalidad(modalidad).estado(estado).asistencia(asistencia)
                .precio(BigDecimal.valueOf(precio)).servicioId("particular").ocupaAgenda(true)
                .telemedicinaUrl(modalidad == Modalidad.ONLINE && estado == EstadoTurno.CONFIRMADO ? meet() : null)
                .fechaCreacion(creado)
                .build();
        t = turnoRepository.save(t);
        if (estadoPago != null) {
            pagoRepository.save(Pago.builder().turno(t).transactionId("demo-" + t.getId()).estado(estadoPago)
                    .monto(BigDecimal.valueOf(precio)).fechaPago(creado.plusMinutes(3)).build());
        }
    }

    // Certificado pedido y pagado (no ocupa agenda); "enviado" lo marca como ya entregado.
    private void documento(Usuario medico, Usuario paciente, boolean enviado) {
        LocalDateTime creado = LocalDateTime.now().minusHours(enviado ? 50 : 3);
        Turno t = Turno.builder().medico(medico).paciente(paciente).fecha(creado.toLocalDate())
                .horaInicio(creado.toLocalTime().withNano(0)).horaFin(creado.toLocalTime().withNano(0))
                .tipo(TipoTurno.CERTIFICADO).estado(EstadoTurno.CONFIRMADO).asistencia(EstadoAsistencia.ESPERANDO)
                .precio(BigDecimal.valueOf(30000)).servicioId("certificado").ocupaAgenda(false).documentoEnviado(enviado)
                .fechaCreacion(creado)
                .build();
        t = turnoRepository.save(t);
        pagoRepository.save(Pago.builder().turno(t).transactionId("demo-" + t.getId()).estado(EstadoPago.APROBADO)
                .monto(BigDecimal.valueOf(30000)).fechaPago(creado.plusMinutes(2)).build());
    }

    // Alterna paciente/profesional empezando por el paciente; el último mensaje queda sin leer si se pide.
    private void chat(Usuario medico, Usuario paciente, List<String> mensajes, boolean ultimoLeido) {
        LocalDateTime t = LocalDateTime.now().minusHours(mensajes.size() * 2L);
        for (int i = 0; i < mensajes.size(); i++) {
            boolean dePaciente = i % 2 == 0;
            boolean ultimo = i == mensajes.size() - 1;
            mensajeRepository.save(Mensaje.builder()
                    .remitente(dePaciente ? paciente : medico).destinatario(dePaciente ? medico : paciente)
                    .contenido(mensajes.get(i)).fechaEnvio(t.plusHours(i * 2L)).leido(!ultimo || ultimoLeido)
                    .build());
        }
    }

    private void notificacion(Usuario usuario, String titulo, String mensaje, String tipo, boolean leido) {
        notificacionRepository.save(Notificacion.builder().usuario(usuario).titulo(titulo).mensaje(mensaje)
                .tipo(tipo).leido(leido).fechaCreacion(LocalDateTime.now().minusHours(random.nextInt(20) + 1)).build());
    }

    private void ticket(Usuario creador, String asunto, String mensaje, Usuario respondedor, String respuesta, TicketEstado estado) {
        Ticket ticket = ticketRepository.save(Ticket.builder().creador(creador).asunto(asunto).estado(estado)
                .fechaCreacion(LocalDateTime.now().minusDays(2)).fechaActualizacion(LocalDateTime.now().minusDays(1)).build());
        ticketMensajeRepository.save(TicketMensaje.builder().ticket(ticket).autor(creador).contenido(mensaje)
                .fechaEnvio(LocalDateTime.now().minusDays(2)).build());
        if (respondedor != null && respuesta != null) {
            ticketMensajeRepository.save(TicketMensaje.builder().ticket(ticket).autor(respondedor).contenido(respuesta)
                    .fechaEnvio(LocalDateTime.now().minusDays(1)).build());
        }
    }

    private String meet() {
        String letras = "abcdefghijkmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder("https://meet.google.com/");
        int[] partes = {3, 4, 3};
        for (int p = 0; p < partes.length; p++) {
            if (p > 0) sb.append('-');
            for (int i = 0; i < partes[p]; i++) sb.append(letras.charAt(random.nextInt(letras.length())));
        }
        return sb.toString();
    }
}
