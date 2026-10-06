package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.Qbi2RecetaDtos;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.model.dto.RecetaResponseDto;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class RecetaServiceTest {

    @Autowired
    private RecetaService recetaService;

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private com.tranqui.app.repository.NotificacionRepository notificacionRepository;

    // Replaces the real Qbi2RecipeClientMock (production default, active whenever
    // qbi2.recipe.enabled is unset/false — see Qbi2RecipeClientMock) with a Mockito mock we fully
    // control, so tests can (a) capture the exact request RecetaService builds — needed to verify
    // the hardcoded defaults (diagnóstico/provincia/especialidad) only kick in when the real field
    // is empty, never overriding a real value — and (b) simulate QBI2 failures deterministically.
    @MockBean
    private Qbi2RecipeClient qbi2RecipeClient;

    @MockBean
    private SubscriptionService subscriptionService;

    private Usuario medico;
    private Usuario paciente;
    private Usuario otroPaciente;

    private Qbi2RecetaDtos.RecetaResponse mockQbiResponse(String s3Link) {
        Qbi2RecetaDtos.RecetaResult result = Qbi2RecetaDtos.RecetaResult.builder()
                .id("TEST-ID")
                .idReceta("TEST-ID-RECETA")
                .nroCUIR(Collections.emptyList())
                .s3Link(s3Link)
                .verificador("TEST-VERIFICADOR")
                .build();
        Qbi2RecetaDtos.RecetaDetalle detalle = Qbi2RecetaDtos.RecetaDetalle.builder()
                .fechavencimiento("2027-01-01")
                .status("OK")
                .build();
        return Qbi2RecetaDtos.RecetaResponse.builder()
                .recetas(List.of(result))
                .response(List.of(detalle))
                .errores(Collections.emptyList())
                .idTransaccion("TEST-TX")
                .build();
    }

    // RecetaService.emitirReceta blocks on a specific set of "datos faltantes" before ever
    // calling QBI2 — every field below is required for the happy-path tests to actually reach
    // qbi2RecipeClient instead of failing validation first.
    @BeforeEach
    void setUp() {
        // Doble Gate (§7) permitido por defecto — solo se sobreescribe en el test que
        // explícitamente prueba el rechazo de SubscriptionService. validarAccesoRecetas es void,
        // así que Mockito ya no-opea (permite) por default sin necesidad de stub explícito acá,
        // pero SubscriptionService no participa de ninguna otra verificación en este archivo.
        lenient().when(qbi2RecipeClient.generarReceta(any()))
                .thenReturn(mockQbiResponse("https://mock-qbi2.test/receta.pdf"));
        medico = Usuario.builder()
                .nombre("Medico Marta")
                .apellido("Rossi")
                .email("marta.medico." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("12345")
                .tipoDocumento("DNI")
                .numeroDocumento(20123456)
                .codigoRefeps("123456789012")
                .domicilioAtencion("Av. Siempre Viva 742, Córdoba")
                .build();
        paciente = Usuario.builder()
                .nombre("Paciente Pedro")
                .apellido("Gómez")
                .email("pedro.paciente." + System.nanoTime() + "@gmail.com")
                .telefono("+5491100001111")
                .rol(Rol.PACIENTE)
                .tipoDocumento("DNI")
                .numeroDocumento(30123456)
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .build();
        otroPaciente = Usuario.builder()
                .nombre("Otro Paciente")
                .email("otro.paciente." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        usuarioRepository.save(medico);
        usuarioRepository.save(paciente);
        usuarioRepository.save(otroPaciente);
    }

    @AfterEach
    void tearDown() {
        List<Receta> recetas = recetaRepository.findAll();
        recetaRepository.deleteAll(recetas);
        // emitirReceta puede disparar TurnoService#marcarRecetasEnviadasParaPaciente (bean real,
        // no mockeado acá), que a su vez crea una Notificacion real para el paciente/médico — hay
        // que limpiarlas antes de borrar los usuarios o el DELETE choca con la FK de NOTIFICACION.
        notificacionRepository.deleteAll(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(medico.getId()));
        notificacionRepository.deleteAll(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(paciente.getId()));
        notificacionRepository.deleteAll(notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(otroPaciente.getId()));
        usuarioRepository.delete(medico);
        usuarioRepository.delete(paciente);
        usuarioRepository.delete(otroPaciente);
    }

    private RecetaDto.MedicamentoDto medicamento(String nombre, String laboratorio) {
        RecetaDto.MedicamentoDto med = new RecetaDto.MedicamentoDto();
        med.setName(nombre);
        med.setDosage("400mg");
        med.setFrequency("Cada 8hs");
        med.setDuration("3 dias");
        med.setLaboratorio(laboratorio);
        return med;
    }

    @Test
    void testEmitirReceta() {
        RecetaDto.MedicamentoDto med1 = medicamento("Ibuprofeno", null);

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Dolor")
                .notes("Tomar con comida")
                .medications(Arrays.asList(med1))
                .build();

        RecetaResponseDto result = recetaService.emitirReceta(medico.getEmail(), dto);
        assertNotNull(result);
        assertEquals(paciente.getId(), result.getPaciente().getId());
        assertEquals(medico.getId(), result.getMedico().getId());
        // El pdfUrl viene directo del s3Link que QBI2 devuelve — acá mockeado explícitamente vía
        // @MockBean, a diferencia de Qbi2RecipeClientMock (el bean real que corre en dev/producción
        // sin credenciales QBI2 configuradas), que a propósito devuelve s3Link null: nunca fabrica
        // un link falso hacia un PDF que no existe. Mockear la respuesta acá deja probar que ese
        // campo realmente se propaga de punta a punta cuando QBI2 sí lo manda.
        assertEquals("https://mock-qbi2.test/receta.pdf", result.getPdfUrl());
        assertNotNull(result.getQbi2IdReceta());
        assertNotNull(result.getQbi2Verificador());
        assertNotNull(result.getFechaEmision());
    }

    @Test
    void testEmitirReceta_persisteLaboratoriosCargadosPorElMedico() {
        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Ansiedad")
                .notes("Control en 30 días")
                .medications(Arrays.asList(
                        medicamento("Escitalopram", "Bagó"),
                        medicamento("Clonazepam", null),
                        medicamento("Sertralina", "Gador")))
                .build();

        RecetaResponseDto result = recetaService.emitirReceta(medico.getEmail(), dto);

        assertNotNull(result.getLaboratorios());
        assertEquals(2, result.getLaboratorios().size());
        assertTrue(result.getLaboratorios().contains("Bagó"));
        assertTrue(result.getLaboratorios().contains("Gador"));
        assertTrue(result.getMedicamentos().contains("Laboratorio: Bagó"));
    }

    @Test
    void testEmitirReceta_faltaDniDelPaciente_lanzaRecetaElectronicaException() {
        Usuario pacienteSinDni = usuarioRepository.save(Usuario.builder()
                .nombre("Sin Dni")
                .email("sin.dni." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(pacienteSinDni.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medico.getEmail(), dto));
            assertTrue(ex.getMessage().contains("DNI del paciente"));
        } finally {
            usuarioRepository.delete(pacienteSinDni);
        }
    }

    @Test
    void testObtenerMisRecetas_devuelveLoEmitidoParaElMedico() {
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        List<RecetaResponseDto> misRecetas = recetaService.obtenerMisRecetas(medico.getEmail());

        assertFalse(misRecetas.isEmpty());
        assertTrue(misRecetas.stream().anyMatch(r -> r.getPaciente().getId().equals(paciente.getId())));
    }

    @Test
    void testObtenerRecetaPorId_usuarioAjeno_lanzaAccessDenied() {
        RecetaResponseDto receta = recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        assertThrows(AccessDeniedException.class,
                () -> recetaService.obtenerRecetaPorId(receta.getId(), otroPaciente.getEmail()));
    }

    // ── validaciones de "datos faltantes" (branches de datosFaltantes) ──────────────

    @Test
    void testEmitirReceta_faltaFechaNacimientoDelPaciente_lanzaRecetaElectronicaException() {
        Usuario pacienteSinFecha = usuarioRepository.save(Usuario.builder()
                .nombre("Sin Fecha")
                .email("sin.fecha." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .numeroDocumento(30999999)
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(pacienteSinFecha.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medico.getEmail(), dto));
            assertTrue(ex.getMessage().contains("fecha de nacimiento del paciente"));
        } finally {
            usuarioRepository.delete(pacienteSinFecha);
        }
    }

    @Test
    void testEmitirReceta_faltaDniDelMedico_lanzaRecetaElectronicaException() {
        Usuario medicoSinDni = usuarioRepository.save(Usuario.builder()
                .nombre("Medico Sin Dni")
                .email("medico.sindni." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("54321")
                .codigoRefeps("123456789012")
                .domicilioAtencion("Calle Falsa 123")
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(paciente.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medicoSinDni.getEmail(), dto));
            assertTrue(ex.getMessage().contains("tu DNI"));
        } finally {
            usuarioRepository.delete(medicoSinDni);
        }
    }

    @Test
    void testEmitirReceta_faltaMatriculaDelMedico_lanzaRecetaElectronicaException() {
        Usuario medicoSinMatricula = usuarioRepository.save(Usuario.builder()
                .nombre("Medico Sin Matricula")
                .email("medico.sinmatricula." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .numeroDocumento(20999999)
                .codigoRefeps("123456789012")
                .domicilioAtencion("Calle Falsa 123")
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(paciente.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medicoSinMatricula.getEmail(), dto));
            assertTrue(ex.getMessage().contains("número de matrícula"));
        } finally {
            usuarioRepository.delete(medicoSinMatricula);
        }
    }

    @Test
    void testEmitirReceta_refepsConFormatoInvalido_lanzaRecetaElectronicaException() {
        // MedicoService.actualizarPerfil ya exige 12 dígitos exactos para guardados nuevos, pero un
        // REFEPS viejo (cargado antes de esa validación) puede tener el largo incorrecto — ver
        // comentario en RecetaService sobre por qué esto se valida acá y no se deja pasar a QBI2.
        Usuario medicoRefepsInvalido = usuarioRepository.save(Usuario.builder()
                .nombre("Medico Refeps Invalido")
                .email("medico.refepsinvalido." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("11111")
                .numeroDocumento(20999998)
                .codigoRefeps("12345")
                .domicilioAtencion("Calle Falsa 123")
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(paciente.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medicoRefepsInvalido.getEmail(), dto));
            assertTrue(ex.getMessage().contains("código REFEPS"));
            assertTrue(ex.getMessage().contains("12 dígitos"));
        } finally {
            usuarioRepository.delete(medicoRefepsInvalido);
        }
    }

    @Test
    void testEmitirReceta_faltaDomicilioDelMedico_lanzaRecetaElectronicaException() {
        Usuario medicoSinDomicilio = usuarioRepository.save(Usuario.builder()
                .nombre("Medico Sin Domicilio")
                .email("medico.sindomicilio." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PSIQUIATRA)
                .matricula("22222")
                .numeroDocumento(20999997)
                .codigoRefeps("123456789012")
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(paciente.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medicoSinDomicilio.getEmail(), dto));
            assertTrue(ex.getMessage().contains("dirección profesional"));
        } finally {
            usuarioRepository.delete(medicoSinDomicilio);
        }
    }

    @Test
    void testEmitirReceta_numeroDeAfiliadoConFormatoInvalido_lanzaRecetaElectronicaException() {
        // ClinicalService.actualizarPaciente ya exige 6-30 caracteres para guardados nuevos, pero
        // un valor viejo (cargado antes de esa validación) puede ser demasiado corto — ver
        // comentario en RecetaService.
        Usuario pacienteAfiliadoInvalido = usuarioRepository.save(Usuario.builder()
                .nombre("Paciente Afiliado Invalido")
                .email("paciente.afiliadoinvalido." + System.nanoTime() + "@gmail.com")
                .rol(Rol.PACIENTE)
                .numeroDocumento(30999998)
                .fechaNacimiento(LocalDate.of(1985, 5, 5))
                .credencialCodEntidad(100)
                .credencialPan("ab")
                .build());
        try {
            RecetaDto dto = RecetaDto.builder()
                    .pacienteId(pacienteAfiliadoInvalido.getId())
                    .diagnosis("Dolor")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build();

            RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                    () -> recetaService.emitirReceta(medico.getEmail(), dto));
            assertTrue(ex.getMessage().contains("número de afiliado del paciente"));
        } finally {
            usuarioRepository.delete(pacienteAfiliadoInvalido);
        }
    }

    // ── defaults hardcodeados: solo deben aplicarse cuando el campo real viene vacío ──

    @Test
    void testEmitirReceta_diagnosticoReal_noEsPisadoPorElDefault() {
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Trastorno de pánico")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("Trastorno de pánico", captor.getValue().getDiagnostico());
    }

    @Test
    void testEmitirReceta_diagnosticoVacio_usaDefaultTrastornoDeAnsiedad() {
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis(null)
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("TRASTORNO DE ANSIEDAD GENERALIZADA", captor.getValue().getDiagnostico());
    }

    @Test
    void testEmitirReceta_provinciaDeMatriculaReal_noEsPisadaPorElDefault() {
        medico.setMatriculaProvincia("Buenos Aires");
        usuarioRepository.save(medico);

        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("Buenos Aires", captor.getValue().getMedico().getMatricula().getProvincia());
    }

    @Test
    void testEmitirReceta_provinciaDeMatriculaVacia_usaDefaultCordoba() {
        // medico de setUp no tiene matriculaProvincia cargada -> null
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("Córdoba", captor.getValue().getMedico().getMatricula().getProvincia());
    }

    @Test
    void testEmitirReceta_especialidadReal_noEsPisadaPorElDefault() {
        medico.setSpecialty("Cardiología");
        usuarioRepository.save(medico);

        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("Cardiología", captor.getValue().getMedico().getEspecialidad());
        assertEquals("Cardiología", captor.getValue().getMedico().getMatricula().getEspecialidad());
    }

    @Test
    void testEmitirReceta_especialidadVacia_usaDefaultPsiquiatria() {
        // medico de setUp no tiene specialty cargada -> null
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        ArgumentCaptor<Qbi2RecetaDtos.RecetaRequest> captor = ArgumentCaptor.forClass(Qbi2RecetaDtos.RecetaRequest.class);
        verify(qbi2RecipeClient).generarReceta(captor.capture());
        assertEquals("Psiquiatría", captor.getValue().getMedico().getEspecialidad());
        assertEquals("Psiquiatría", captor.getValue().getMedico().getMatricula().getEspecialidad());
    }

    // ── errores de QBI2 ────────────────────────────────────────────────────────────

    @Test
    void testEmitirReceta_qbi2LanzaExcepcion_elMensajeSeTruncaA240Caracteres() {
        String mensajeLargoDeQbi2 = "X".repeat(300);
        String body = "{\"error\":\"QBI235\",\"mensaje\":\"" + mensajeLargoDeQbi2 + "\"}";
        when(qbi2RecipeClient.generarReceta(any()))
                .thenThrow(new Qbi2RecipeException(422, body, "QBI2 Recipe respondió 422"));

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Dolor")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build();

        RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                () -> recetaService.emitirReceta(medico.getEmail(), dto));

        // El frontend (sanitizeErrorMessage en api.ts) reemplaza cualquier mensaje de más de 250
        // caracteres por uno genérico — RecetaService recorta a 240 con margen para que el detalle
        // real de QBI2 (qué campo corregir) siga llegándole al médico en vez de perderse.
        assertTrue(ex.getMessage().length() <= 240,
                "El mensaje debería estar recortado a <= 240 caracteres, tiene " + ex.getMessage().length());
        assertTrue(ex.getMessage().endsWith("..."));
        assertTrue(ex.getMessage().contains("QBI235"));
    }

    @Test
    void testEmitirReceta_qbi2DevuelveListaDeRecetasVacia_lanzaRecetaElectronicaException() {
        Qbi2RecetaDtos.MedicamentoError error = Qbi2RecetaDtos.MedicamentoError.builder()
                .error("QBI999").mensaje("Sin cobertura disponible para el financiador indicado").build();
        Qbi2RecetaDtos.RecetaResponse respuestaVacia = Qbi2RecetaDtos.RecetaResponse.builder()
                .recetas(Collections.emptyList())
                .errores(List.of(error))
                .build();
        when(qbi2RecipeClient.generarReceta(any())).thenReturn(respuestaVacia);

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Dolor")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build();

        RecetaElectronicaException ex = assertThrows(RecetaElectronicaException.class,
                () -> recetaService.emitirReceta(medico.getEmail(), dto));
        assertTrue(ex.getMessage().contains("no la validó"));
        assertTrue(ex.getMessage().contains("Sin cobertura disponible"));
    }

    // ── acceso denegado por el Doble Gate (SubscriptionService) ─────────────────────

    @Test
    void testEmitirReceta_subscriptionServiceDeniegaAcceso_propagaLaExcepcion() {
        doThrow(new IllegalStateException("Tu plan actual no incluye recetas electrónicas oficiales."))
                .when(subscriptionService).validarAccesoRecetas(any());

        RecetaDto dto = RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> recetaService.emitirReceta(medico.getEmail(), dto));
        assertTrue(ex.getMessage().contains("recetas electrónicas"));
    }

    // ── obtenerMisRecetas desde el lado paciente ─────────────────────────────────────

    @Test
    void testObtenerMisRecetas_devuelveLoEmitidoParaElPaciente() {
        recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                .pacienteId(paciente.getId())
                .diagnosis("Control")
                .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                .build());

        List<RecetaResponseDto> misRecetas = recetaService.obtenerMisRecetas(paciente.getEmail());

        assertFalse(misRecetas.isEmpty());
        assertTrue(misRecetas.stream().anyMatch(r -> r.getMedico().getId().equals(medico.getId())));
    }

    // ── side-effect: marca como enviados los turnos de "receta fuera de turno" pendientes ──

    @Test
    void testEmitirReceta_marcaComoEnviadosLosTurnosDeRecetaFueraDeTurnoPendientes() {
        Turno turnoRecetaFueraDeTurno = turnoRepository.save(Turno.builder()
                .medico(medico).paciente(paciente)
                .fecha(LocalDate.now()).horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(9, 0))
                .tipo(TipoTurno.RECETA).estado(EstadoTurno.CONFIRMADO)
                .servicioId("receta-fuera")
                .ocupaAgenda(false)
                .documentoEnviado(false)
                .precio(BigDecimal.TEN)
                .build());
        try {
            recetaService.emitirReceta(medico.getEmail(), RecetaDto.builder()
                    .pacienteId(paciente.getId())
                    .diagnosis("Control")
                    .medications(Arrays.asList(medicamento("Ibuprofeno", null)))
                    .build());

            Turno actualizado = turnoRepository.findById(turnoRecetaFueraDeTurno.getId()).orElseThrow();
            assertTrue(actualizado.isDocumentoEnviado());
        } finally {
            turnoRepository.delete(turnoRecetaFueraDeTurno);
        }
    }
}
