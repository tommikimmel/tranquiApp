package com.tranqui.app.config;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.Mensaje;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    @Override
    public void run(String... args) throws Exception {
        // Ensure María Paula Rossi is updated/verified
        Optional<Usuario> RossiOpt = usuarioRepository.findByEmail("paula@tranqui.com");
        Usuario medica;
        if (RossiOpt.isPresent()) {
            medica = RossiOpt.get();
            medica.setNombre("María Paula");
            medica.setApellido("Rossi");
            medica.setSexo("F");
            medica.setFechaNacimiento(LocalDate.of(1985, 5, 20));
            medica.setCuil(27312345679L);
            medica.setTipoDocumento("DN");
            medica.setNumeroDocumento(31234567);
            medica.setDomicilioAtencion("Av. General Paz 120, Córdoba");
            medica.setCodigoReFeps(120000000000L);
            medica.setMatriculaTipo("MN");
            medica.setMatriculaProvincia("C");
            medica.setMatriculaNumero(49281);
            medica.setMatriculaEspecialidad("Terapia Cognitivo Conductual (TCC)");
            medica.setMatriculaAsocTipo("MN");
            medica.setMatriculaAsocProvincia("C");
            medica.setMatriculaAsocNumero(49281);
            medica.setFotoUrl("https://images.unsplash.com/photo-1559839734-2b71ea197ec2?auto=format&fit=crop&q=80&w=300");
            medica = usuarioRepository.save(medica);
            log.info("Perfil de María Paula Rossi verificado/actualizado.");
        } else {
            medica = Usuario.builder()
                    .nombre("María Paula")
                    .apellido("Rossi")
                    .email("paula@tranqui.com")
                    .rol(Rol.PSIQUIATRA)
                    .matricula("49281")
                    .titulo("Psicóloga")
                    .specialty("Terapia Cognitivo Conductual (TCC)")
                    .cuit("27-12345678-9")
                    .precio(new java.math.BigDecimal("35000"))
                    .tags("Ansiedad,Estrés,Burnout,Ataques de pánico")
                    .color("#E8F5EE")
                    .telefono("3519999999")
                    .sexo("F")
                    .fechaNacimiento(LocalDate.of(1985, 5, 20))
                    .cuil(27312345679L)
                    .tipoDocumento("DN")
                    .numeroDocumento(31234567)
                    .domicilioAtencion("Av. General Paz 120, Córdoba")
                    .codigoReFeps(120000000000L)
                    .matriculaTipo("MN")
                    .matriculaProvincia("C")
                    .matriculaNumero(49281)
                    .matriculaEspecialidad("Terapia Cognitivo Conductual (TCC)")
                    .matriculaAsocTipo("MN")
                    .matriculaAsocProvincia("C")
                    .matriculaAsocNumero(49281)
                    .fotoUrl("https://images.unsplash.com/photo-1559839734-2b71ea197ec2?auto=format&fit=crop&q=80&w=300")
                    .build();
            medica = usuarioRepository.save(medica);
            log.info("Médica demo María Paula Rossi creada.");
            
            // Create availabilities for Rossi
            for (int i = 1; i <= 5; i++) {
                disponibilidadRepository.save(Disponibilidad.builder()
                        .medico(medica)
                        .diaSemana(i)
                        .horaInicio(LocalTime.of(9, 0))
                        .horaFin(LocalTime.of(12, 0))
                        .build());
                disponibilidadRepository.save(Disponibilidad.builder()
                        .medico(medica)
                        .diaSemana(i)
                        .horaInicio(LocalTime.of(14, 0))
                        .horaFin(LocalTime.of(17, 0))
                        .build());
            }
        }

        // Ensure Demo@gmail.com exists and is verified
        Optional<Usuario> demoOpt = usuarioRepository.findByEmail("Demo@gmail.com");
        if (demoOpt.isEmpty()) {
            Usuario demoMedico = Usuario.builder()
                    .nombre("Demo")
                    .apellido("Demo")
                    .email("Demo@gmail.com")
                    .rol(Rol.PSIQUIATRA)
                    .matricula("11111")
                    .titulo("Médico Clínico")
                    .specialty("Medicina Clínica")
                    .cuit("27-12345678-0")
                    .precio(new java.math.BigDecimal("45000"))
                    .tags("Clínica,Consulta general")
                    .color("#E0F2FE")
                    .telefono("1111111111")
                    .sexo("M")
                    .fechaNacimiento(LocalDate.of(1980, 1, 1))
                    .cuil(27123456780L)
                    .tipoDocumento("DN")
                    .numeroDocumento(12345678)
                    .domicilioAtencion("Consultorio particular CALLE 11 Nro 2300")
                    .codigoReFeps(123456789012L)
                    .matriculaTipo("MN")
                    .matriculaProvincia("S")
                    .matriculaNumero(11111)
                    .matriculaEspecialidad("MedicoClinico")
                    .matriculaAsocTipo("MN")
                    .matriculaAsocProvincia("C")
                    .matriculaAsocNumero(11111)
                    .fotoUrl("https://images.unsplash.com/photo-1622253692010-333f2da6031d?auto=format&fit=crop&q=80&w=300")
                    .build();
            demoMedico = usuarioRepository.save(demoMedico);
            log.info("Médico demo Demo Demo creado y verificado.");

            // Create availabilities for Demo doctor
            for (int i = 1; i <= 5; i++) {
                disponibilidadRepository.save(Disponibilidad.builder()
                        .medico(demoMedico)
                        .diaSemana(i)
                        .horaInicio(LocalTime.of(9, 0))
                        .horaFin(LocalTime.of(12, 0))
                        .build());
                disponibilidadRepository.save(Disponibilidad.builder()
                        .medico(demoMedico)
                        .diaSemana(i)
                        .horaInicio(LocalTime.of(14, 0))
                        .horaFin(LocalTime.of(17, 0))
                        .build());
            }
        }

        // Ensure patient demo Mateo Benítez exists
        Optional<Usuario> patientOpt = usuarioRepository.findByEmail("mateo.b@gmail.com");
        if (patientOpt.isEmpty()) {
            Usuario paciente = Usuario.builder()
                    .nombre("Mateo Benítez")
                    .email("mateo.b@gmail.com")
                    .telefono("+5493517654321")
                    .rol(Rol.PACIENTE)
                    .dni("41.234.567")
                    .direccion("Av. General Paz 456, Córdoba")
                    .obraSocial("OSDE 410")
                    .numAfiliado("1-987654-3")
                    .build();
            usuarioRepository.save(paciente);
            log.info("Paciente demo Mateo Benítez creado.");
        }
    }
}
