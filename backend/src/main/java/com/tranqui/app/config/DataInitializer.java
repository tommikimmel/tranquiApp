package com.tranqui.app.config;

import com.tranqui.app.model.BillingSource;
import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.SubscriptionStatus;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.Mensaje;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.PlanRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.tranqui.app.util.EncryptionUtil encryptionUtil;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing clean simulation accounts...");

        // Default Admin account
        if (usuarioRepository.findByEmail("admin@tranqui.com").isEmpty()) {
            Usuario admin = Usuario.builder()
                    .nombre("Administrador")
                    .email("admin@tranqui.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.ADMIN)
                    .verificadoAdmin(true)
                    .build();
            usuarioRepository.save(admin);
            log.info("Default admin account created: admin@tranqui.com / admin123");
        }

        // Unverified Doctor
        Optional<Usuario> existingMedico = usuarioRepository.findByEmail("medico.sinverificar@gmail.com");
        if (existingMedico.isEmpty()) {
            Usuario medicoUnverified = Usuario.builder()
                    .nombre("Medico")
                    .email("medico.sinverificar@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.PSIQUIATRA)
                    .build();
            usuarioRepository.save(medicoUnverified);
            log.info("Unverified doctor account created: medico.sinverificar@gmail.com / admin123");
        } else if (existingMedico.get().getPassword() == null) {
            Usuario medico = existingMedico.get();
            medico.setPassword(passwordEncoder.encode("admin123"));
            usuarioRepository.save(medico);
            log.info("Updated password for unverified doctor: medico.sinverificar@gmail.com / admin123");
        }

        // Fully Verified Doctor
        if (usuarioRepository.findByEmail("medico.verificado@gmail.com").isEmpty()) {
            // isMedicoVerificado() (MedicoService) also requires a non-null mpAccessTokenEncrypted
            // whenever mercadopago.enabled=true (this env has real MP credentials configured) — a
            // professional can't be listed publicly if they could never actually get paid. Seeding
            // an encrypted "dummy-token" satisfies that null-check while still safely tripping
            // MercadoPagoService's own dummy-token guard (falls back to the payment simulator
            // instead of ever calling the real Mercado Pago API) — see crearPreferenciaPago.
            String dummyMpToken = encryptionUtil.encrypt("dummy-token");
            Usuario medicoVerificado = Usuario.builder()
                    .nombre("Carlos")
                    .apellido("Perez")
                    .email("medico.verificado@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.PSIQUIATRA)
                    .sexo("M")
                    .fechaNacimiento(LocalDate.of(1985, 8, 20))
                    .cuil(20321234567L)
                    .tipoDocumento("DNI")
                    .numeroDocumento(32123456)
                    .domicilioAtencion("Av. Santa Fe 1234, CABA")
                    .domicilioLat(-34.5956)
                    .domicilioLng(-58.4234)
                    .matriculaTipo("MN")
                    .matriculaProvincia("CABA")
                    .matriculaNumero(49281)
                    .matricula("49281")
                    // RecetaService.emitirReceta rechaza con 422 si falta este código (QBI2 lo
                    // exige) — sin esto, esta cuenta "demo" nunca puede emitir recetas.
                    .codigoRefeps("123456789012")
                    .fotoUrl("https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?auto=format&fit=crop&q=80&w=200")
                    .specialty("Psiquiatría de Adultos")
                    .titulo("Médico Psiquiatra")
                    .precio(java.math.BigDecimal.valueOf(60000))
                    .ofreceOnline(true)
                    .ofrecePresencial(true)
                    .verificadoAdmin(true)
                    // isMedicoVerificado() (MedicoService) exige todos estos campos además de los
                    // de arriba para listar al médico públicamente — sin ellos, esta cuenta "demo"
                    // queda invisible en el sitio (landing/búsqueda) aunque verificadoAdmin=true.
                    .descripcionPerfil("Médico especialista con enfoque integral combinando psicoterapia y abordaje farmacológico de forma personalizada.")
                    .pacientesAtiende("Adultos,Adultos mayores")
                    .institucionFormacion("UBA")
                    .aniosExperiencia(12)
                    .tags("Ansiedad,Depresión")
                    .experiencia("Residencia en Hospital Italiano de Buenos Aires")
                    .mpAccessTokenEncrypted(dummyMpToken)
                    .build();
            Usuario savedMedico = usuarioRepository.save(medicoVerificado);
            log.info("Fully verified doctor account created: medico.verificado@gmail.com / admin123");

            if (savedMedico != null) {
                // MedicoService.obtenerMedicosActivos() también exige suscripción activa
                // (paywall) — sin esto, el médico demo queda igual de invisible pese al perfil
                // completo de arriba.
                Optional<Plan> planClinico = planRepository.findByCode("clinico");
                if (planClinico.isPresent()) {
                    Subscription subscription = Subscription.builder()
                            .professional(savedMedico)
                            .plan(planClinico.get())
                            .status(SubscriptionStatus.ACTIVE)
                            .seats(1)
                            .billingSource(BillingSource.MANUAL_TRANSFER)
                            .amountArs(planClinico.get().getPriceArs())
                            .currentPeriodStart(LocalDateTime.now())
                            .currentPeriodEnd(LocalDateTime.now().plusYears(1))
                            .build();
                    subscriptionRepository.save(subscription);
                    log.info("Active subscription seeded for medico.verificado@gmail.com");
                } else {
                    log.warn("Plan 'clinico' not found — medico.verificado@gmail.com won't pass the subscription paywall until one is created.");
                }

                // Initialize availability slots for this doctor
                for (int day = 1; day <= 5; day++) {
                    Disponibilidad slot1 = Disponibilidad.builder()
                            .medico(savedMedico)
                            .diaSemana(day)
                            .horaInicio(LocalTime.of(9, 0))
                            .horaFin(LocalTime.of(13, 0))
                            .build();
                    Disponibilidad slot2 = Disponibilidad.builder()
                            .medico(savedMedico)
                            .diaSemana(day)
                            .horaInicio(LocalTime.of(14, 0))
                            .horaFin(LocalTime.of(18, 0))
                            .build();
                    disponibilidadRepository.save(slot1);
                    disponibilidadRepository.save(slot2);
                }
                log.info("Availability slots initialized for medico.verificado@gmail.com");
            }
        }

        // Data-less Patient
        Optional<Usuario> existingPaciente = usuarioRepository.findByEmail("paciente.sindatos@gmail.com");
        if (existingPaciente.isEmpty()) {
            Usuario pacienteSinDatos = Usuario.builder()
                    .nombre("Paciente")
                    .email("paciente.sindatos@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.PACIENTE)
                    .build();
            usuarioRepository.save(pacienteSinDatos);
            log.info("Data-less patient account created: paciente.sindatos@gmail.com / admin123");
        } else if (existingPaciente.get().getPassword() == null) {
            Usuario paciente = existingPaciente.get();
            paciente.setPassword(passwordEncoder.encode("admin123"));
            usuarioRepository.save(paciente);
            log.info("Updated password for data-less patient: paciente.sindatos@gmail.com / admin123");
        }

        // Fully Complete Patient — has every field RecetaService.emitirReceta requires/sends to
        // QBI2 (DNI, fecha de nacimiento, sexo) so it can be used to test receta emission without
        // hitting the "faltan datos" block or QBI2 silently defaulting sexo to "M".
        if (usuarioRepository.findByEmail("paciente.completo@gmail.com").isEmpty()) {
            Usuario pacienteCompleto = Usuario.builder()
                    .nombre("Juan")
                    .apellido("Paciente")
                    .email("paciente.completo@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.PACIENTE)
                    .dni("40123456")
                    .tipoDocumento("DNI")
                    .numeroDocumento(40123456)
                    .fechaNacimiento(LocalDate.of(1992, 3, 15))
                    .sexo("M")
                    .cuil(20401234560L)
                    .telefono("+541165432109")
                    // getMissingPatientFields() (frontend) also requires `direccion` non-blank
                    // before a médico can emit a receta for this patient — sin esto, esta cuenta
                    // "completa" bloqueaba justamente el flujo de recetas que dice habilitar.
                    .direccion("Av. Corrientes 1234, CABA")
                    .build();
            usuarioRepository.save(pacienteCompleto);
            log.info("Fully complete patient account created: paciente.completo@gmail.com / admin123");
        }
    }
}
