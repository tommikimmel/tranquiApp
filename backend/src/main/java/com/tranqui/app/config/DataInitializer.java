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
        log.info("Data initialization skipped: clean slate mode enabled.");
    }
}
