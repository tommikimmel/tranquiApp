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
        log.info("Initializing clean simulation accounts...");

        // Unverified Doctor
        if (usuarioRepository.findByEmail("medico.sinverificar@gmail.com").isEmpty()) {
            Usuario medicoUnverified = Usuario.builder()
                    .nombre("Medico")
                    .email("medico.sinverificar@gmail.com")
                    .rol(Rol.PSIQUIATRA)
                    .build();
            usuarioRepository.save(medicoUnverified);
            log.info("Unverified doctor account created: medico.sinverificar@gmail.com");
        }

        // Data-less Patient
        if (usuarioRepository.findByEmail("paciente.sindatos@gmail.com").isEmpty()) {
            Usuario pacienteSinDatos = Usuario.builder()
                    .nombre("Paciente")
                    .email("paciente.sindatos@gmail.com")
                    .rol(Rol.PACIENTE)
                    .build();
            usuarioRepository.save(pacienteSinDatos);
            log.info("Data-less patient account created: paciente.sindatos@gmail.com");
        }
    }
}
