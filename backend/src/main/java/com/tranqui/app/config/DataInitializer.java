package com.tranqui.app.config;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing base accounts...");

        // Only the admin account is seeded — no demo/simulation médico or paciente accounts.
        // Those used to be created here with a well-known password (admin123) on every startup,
        // including in production, which is both a security hole (public well-known credentials)
        // and unwanted seed data outside of local development.
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
    }
}
