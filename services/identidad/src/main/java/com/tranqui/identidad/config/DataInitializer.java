package com.tranqui.identidad.config;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Semilla de cuentas base y de prueba de identidad (Fase 9, I2b).
 * Espera a que termine la carga inicial antes de correr.
 * Publica identidad.usuario.registrado.v1 por cada usuario creado.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final Usuarios usuarios;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final ObjectProvider<EsperaAntesDeConsumir> esperas;
    private final boolean seedTestAccounts;
    private final String adminInitialPassword;

    public DataInitializer(
            Usuarios usuarios,
            PasswordEncoder passwordEncoder,
            OutboxPublisher outboxPublisher,
            ObjectProvider<EsperaAntesDeConsumir> esperas,
            @Value("${app.seed-test-accounts:false}") boolean seedTestAccounts,
            @Value("${app.admin-initial-password:}") String adminInitialPassword) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.esperas = esperas;
        this.seedTestAccounts = seedTestAccounts;
        this.adminInitialPassword = adminInitialPassword;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        esperarCargaInicial();
        inicializarCuentas();
    }

    private void esperarCargaInicial() {
        List<EsperaAntesDeConsumir> lista = esperas.orderedStream().toList();
        while (!lista.stream().allMatch(EsperaAntesDeConsumir::listo)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void inicializarCuentas() {
        // 1. Cuenta de Admin
        Optional<Usuario> adminExistente = usuarios.buscarPorEmail("admin@tranqui.com");
        if (adminExistente.isEmpty()) {
            String passwordInicial = adminInitialPassword != null && !adminInitialPassword.isBlank()
                    ? adminInitialPassword
                    : (seedTestAccounts ? "admin123" : null);

            if (passwordInicial == null) {
                log.warn("admin@tranqui.com no existe y ADMIN_INITIAL_PASSWORD no está configurada — no se crea la cuenta de admin.");
            } else {
                Usuario admin = Usuario.builder()
                        .nombre("Administrador")
                        .email("admin@tranqui.com")
                        .password(passwordEncoder.encode(passwordInicial))
                        .rol("ADMIN")
                        .verificadoAdmin(true)
                        .fechaRegistro(LocalDateTime.now())
                        .emailVerificado(false)
                        .perfilCompleto(true)
                        .cuentaEliminada(false)
                        .notificacionesEmailHabilitadas(true)
                        .recibirNovedades(true)
                        .build();

                crearYPublicar(admin);
                log.info("Default admin account created: admin@tranqui.com");
            }
        }

        if (!seedTestAccounts) {
            log.info("app.seed-test-accounts=false — no se siembran cuentas de prueba.");
            return;
        }
        log.warn("app.seed-test-accounts=true — sembrando cuentas de prueba de identidad.");

        // 2. Médico sin verificar
        Optional<Usuario> existingMedico = usuarios.buscarPorEmail("medico.sinverificar@gmail.com");
        if (existingMedico.isEmpty()) {
            Usuario medico = Usuario.builder()
                    .nombre("Medico")
                    .email("medico.sinverificar@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PSIQUIATRA")
                    .emailVerificado(false)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(medico);
        } else if (existingMedico.get().password() == null) {
            Usuario medico = existingMedico.get().toBuilder()
                    .password(passwordEncoder.encode("admin123"))
                    .build();
            usuarios.actualizar(medico);
        }

        // 3. Médico verificado
        if (usuarios.buscarPorEmail("medico.verificado@gmail.com").isEmpty()) {
            Usuario medicoVerificado = Usuario.builder()
                    .nombre("Carlos")
                    .apellido("Perez")
                    .email("medico.verificado@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PSIQUIATRA")
                    .sexo("M")
                    .fechaNacimiento(LocalDate.of(1985, 8, 20))
                    .cuil(20321234567L)
                    .tipoDocumento("DNI")
                    .numeroDocumento(32123456)
                    .verificadoAdmin(true)
                    .emailVerificado(true)
                    .profession("psiquiatra")
                    .licenseType("MN")
                    .licenseNumber("49281")
                    .licenseJurisdiction("CABA")
                    .taxIdType("CUIT")
                    .taxId("20321234567")
                    .legalName("Carlos Perez")
                    .ivaConditionId(6)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(medicoVerificado);
        }

        // 4. Paciente sin datos
        Optional<Usuario> existingPaciente = usuarios.buscarPorEmail("paciente.sindatos@gmail.com");
        if (existingPaciente.isEmpty()) {
            Usuario paciente = Usuario.builder()
                    .nombre("Paciente")
                    .email("paciente.sindatos@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PACIENTE")
                    .emailVerificado(false)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(paciente);
        } else if (existingPaciente.get().password() == null) {
            Usuario paciente = existingPaciente.get().toBuilder()
                    .password(passwordEncoder.encode("admin123"))
                    .build();
            usuarios.actualizar(paciente);
        }

        // 5. Paciente completo
        if (usuarios.buscarPorEmail("paciente.completo@gmail.com").isEmpty()) {
            Usuario pacienteCompleto = Usuario.builder()
                    .nombre("Juan")
                    .apellido("Paciente")
                    .email("paciente.completo@gmail.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PACIENTE")
                    .dni("40123456")
                    .tipoDocumento("DNI")
                    .numeroDocumento(40123456)
                    .fechaNacimiento(LocalDate.of(1992, 3, 15))
                    .sexo("M")
                    .cuil(20401234560L)
                    .telefono("+541165432109")
                    .direccion("Av. Corrientes 1234, CABA")
                    .emailVerificado(true)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(pacienteCompleto);
        }

        // 6. Admin de prueba
        Optional<Usuario> existingAdminTest = usuarios.buscarPorEmail("admin.test@tranqui.com");
        if (existingAdminTest.isEmpty()) {
            Usuario adminTest = Usuario.builder()
                    .nombre("Administrador")
                    .apellido("Prueba")
                    .email("admin.test@tranqui.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("ADMIN")
                    .verificadoAdmin(true)
                    .emailVerificado(true)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(adminTest);
        } else {
            Usuario adminTest = existingAdminTest.get().toBuilder()
                    .password(passwordEncoder.encode("admin123"))
                    .rol("ADMIN")
                    .verificadoAdmin(true)
                    .emailVerificado(true)
                    .build();
            usuarios.actualizar(adminTest);
        }

        // 7. Profesional de prueba
        Optional<Usuario> existingProfTest = usuarios.buscarPorEmail("profesional.test@tranqui.com");
        if (existingProfTest.isEmpty()) {
            Usuario profTest = Usuario.builder()
                    .nombre("Carlos")
                    .apellido("Profesional Prueba")
                    .email("profesional.test@tranqui.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PSIQUIATRA")
                    .sexo("M")
                    .fechaNacimiento(LocalDate.of(1985, 8, 20))
                    .cuil(20321234567L)
                    .tipoDocumento("DNI")
                    .numeroDocumento(32123456)
                    .verificadoAdmin(true)
                    .emailVerificado(true)
                    .profession("psiquiatra")
                    .licenseType("MN")
                    .licenseNumber("49281")
                    .licenseJurisdiction("CABA")
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(profTest);
        } else {
            Usuario profTest = existingProfTest.get().toBuilder()
                    .password(passwordEncoder.encode("admin123"))
                    .verificadoAdmin(true)
                    .emailVerificado(true)
                    .rol("PSIQUIATRA")
                    .build();
            usuarios.actualizar(profTest);
        }

        // 8. Paciente de prueba
        Optional<Usuario> existingPacienteTest = usuarios.buscarPorEmail("paciente.test@tranqui.com");
        if (existingPacienteTest.isEmpty()) {
            Usuario pacienteTest = Usuario.builder()
                    .nombre("Juan")
                    .apellido("Paciente Prueba")
                    .email("paciente.test@tranqui.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol("PACIENTE")
                    .dni("40123456")
                    .tipoDocumento("DNI")
                    .numeroDocumento(40123456)
                    .fechaNacimiento(LocalDate.of(1992, 3, 15))
                    .sexo("M")
                    .cuil(20401234560L)
                    .telefono("+541165432109")
                    .direccion("Av. Corrientes 1234, CABA")
                    .emailVerificado(true)
                    .perfilCompleto(true)
                    .build();
            crearYPublicar(pacienteTest);
        } else {
            Usuario pacienteTest = existingPacienteTest.get().toBuilder()
                    .password(passwordEncoder.encode("admin123"))
                    .emailVerificado(true)
                    .build();
            usuarios.actualizar(pacienteTest);
        }
    }

    private void crearYPublicar(Usuario u) {
        Long id = usuarios.insertarNuevo(u);
        Usuario guardado = usuarios.buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("Usuario creado no encontrado: " + id));

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1,
                "usuario",
                String.valueOf(guardado.id()),
                guardado.aggregateVersion(),
                new UsuarioRegistradoData(
                        guardado.id(),
                        guardado.email(),
                        guardado.nombre(),
                        guardado.apellido(),
                        guardado.rol()
                )
        );
    }
}
