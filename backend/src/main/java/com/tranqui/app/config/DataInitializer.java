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

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
    }
}
