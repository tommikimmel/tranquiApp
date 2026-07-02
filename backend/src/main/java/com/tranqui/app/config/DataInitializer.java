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

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

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
        if (usuarioRepository.count() == 0) {
            log.info("Base de datos vacía. Inicializando datos demo...");

            // 1. Crear Médica Demo
            Usuario medica = Usuario.builder()
                    .nombre("Lic. María Paula Rossi")
                    .email("paula@tranqui.com")
                    .rol(Rol.PSIQUIATRA) // use PSIQUIATRA since MEDICO doesn't exist in enum
                    .matricula("MN 49281")
                    .titulo("Psicóloga")
                    .specialty("Terapia Cognitivo Conductual (TCC)")
                    .cuit("27-12345678-9")
                    .precio(new java.math.BigDecimal("35000"))
                    .tags("Ansiedad,Estrés,Burnout,Ataques de pánico")
                    .color("#E8F5EE")
                    .telefono("3519999999")
                    .build();
            medica = usuarioRepository.save(medica);
            log.info("Médica demo creada con ID: {}", medica.getId());

            // 2. Crear Pacientes Demo
            List<Usuario> pacientes = Arrays.asList(
                    Usuario.builder().nombre("Valentina Moreno").email("valentina.m@gmail.com").telefono("+5493512345678").rol(Rol.PACIENTE).build(),
                    Usuario.builder().nombre("Matías Rodríguez").email("matias.r@gmail.com").telefono("+5493512345679").rol(Rol.PACIENTE).build(),
                    Usuario.builder().nombre("Lucía Fernández").email("lucia.f@gmail.com").telefono("+5493512345680").rol(Rol.PACIENTE).build(),
                    Usuario.builder().nombre("Santiago Torres").email("santiago.t@gmail.com").telefono("+5493512345681").rol(Rol.PACIENTE).build()
            );
            usuarioRepository.saveAll(pacientes);
            log.info("Pacientes demo creados.");

            // 3. Crear Disponibilidades por Defecto (Mon-Fri 09:00-12:00, 14:00-17:00)
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
            log.info("Disponibilidades demo creadas para la médica.");

            // 4. Crear Visitadores Demo
            List<Usuario> visitadores = Arrays.asList(
                    Usuario.builder().nombre("Martín Suárez").email("martin@gador.com").rol(Rol.VISITADOR).specialty("Gador").build(),
                    Usuario.builder().nombre("Lucía Páez").email("lucia@roemmers.com").rol(Rol.VISITADOR).specialty("Roemmers").build(),
                    Usuario.builder().nombre("Federico Romero").email("federico@bago.com").rol(Rol.VISITADOR).specialty("Bagó").build(),
                    Usuario.builder().nombre("Camila Vega").email("camila@raffo.com").rol(Rol.VISITADOR).specialty("Raffo").build()
            );
            visitadores = usuarioRepository.saveAll(visitadores);
            log.info("Visitadores demo creados.");

            // 5. Crear Mensajes de prueba de visitadores
            mensajeRepository.save(Mensaje.builder()
                    .remitente(visitadores.get(0))
                    .destinatario(medica)
                    .contenido("Estimado/a, le escribo para presentarle la nueva presentación de Escitalopram 20mg de Gador. Tenemos muestras disponibles y me gustaría coordinar una visita breve de 10 minutos.")
                    .build());
            mensajeRepository.save(Mensaje.builder()
                    .remitente(visitadores.get(1))
                    .destinatario(medica)
                    .contenido("Desde Roemmers queremos invitarlo/a al XII Congreso de Psiquiatría de Córdoba. Podemos cubrir la inscripción. ¿Le interesaría recibir más información?")
                    .build());
            mensajeRepository.save(Mensaje.builder()
                    .remitente(visitadores.get(2))
                    .destinatario(medica)
                    .contenido("Buenas tardes, tenemos muestras de Quetiapina 25mg para su consultorio. ¿Puedo pasar esta semana?")
                    .build());
            mensajeRepository.save(Mensaje.builder()
                    .remitente(visitadores.get(3))
                    .destinatario(medica)
                    .contenido("Le comparto un estudio reciente sobre eficacia de Pregabalina en Trastorno de Ansiedad Generalizada. ¿Le interesa que coordine una presentación?")
                    .build());
            log.info("Mensajes de visitadores creados.");
        }
    }
}
