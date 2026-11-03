package com.tranqui.profesionales.recetas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.RecetaEmitidaData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.profesionales.clinica.AccesoPacientes;
import com.tranqui.profesionales.clinica.ProhibidoException;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.interno.ClienteInterno;
import com.tranqui.profesionales.perfil.NoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio de emisión y consulta de recetas electrónicas (QBI2 / Innovamed) en el servicio de profesionales.
 * Mismo comportamiento, validaciones y reglas de negocio que {@code RecetaService} del monolito.
 */
@Service
public class RecetaService {

    private static final Logger log = LoggerFactory.getLogger(RecetaService.class);

    private final Recetas recetas;
    private final Personas personas;
    private final Profesionales profesionales;
    private final AccesoPacientes accesoPacientes;
    private final Qbi2RecipeClient qbi2RecipeClient;
    private final OutboxPublisher outbox;
    private final ClienteInterno clienteInterno;
    private final ObjectMapper objectMapper = EventObjectMapperFactory.create();

    public RecetaService(Recetas recetas,
                         Personas personas,
                         Profesionales profesionales,
                         AccesoPacientes accesoPacientes,
                         Qbi2RecipeClient qbi2RecipeClient,
                         OutboxPublisher outbox,
                         ClienteInterno clienteInterno) {
        this.recetas = recetas;
        this.personas = personas;
        this.profesionales = profesionales;
        this.accesoPacientes = accesoPacientes;
        this.qbi2RecipeClient = qbi2RecipeClient;
        this.outbox = outbox;
        this.clienteInterno = clienteInterno;
    }

    @Transactional
    public RecetaResponseDto emitirReceta(String medicoEmail, RecetaDto dto) {
        // Mapeo explícito de getters de Usuario (monolito) a los campos del servicio de profesionales:
        // - medico.getId() -> profesional.id()
        // - medico.getNumeroDocumento() / getDni() -> personaMedico.numeroDocumento() / personaMedico.dni() (fallback profesional)
        // - medico.getMatriculaNumero() / getMatricula() -> perfil.matriculaNumero() / perfil.matricula()
        // - medico.getMatriculaTipo() / getMatriculaProvincia() -> perfil.matriculaTipo() / perfil.matriculaProvincia()
        // - medico.getCodigoRefeps() -> perfil.codigoRefeps()
        // - medico.getDomicilioAtencion() -> perfil.domicilioAtencion()
        // - medico.getDireccion() / getDomicilioCalle().. -> personaMedico.direccion() / personaMedico.domicilioCalle()..
        // - medico.getNombre() / getApellido() -> personaMedico.nombre() / personaMedico.apellido() (fallback profesional)
        // - medico.getSpecialty() / getTitulo() -> perfil.especialidad() / perfil.titulo()
        // - medico.getSelloLinea1..3() -> perfil.selloLinea1..3()
        // - medico.getSexo() / getTipoDocumento() / getFechaNacimiento() -> personaMedico.sexo() / tipoDocumento() / fechaNacimiento()
        // - medico.getEmail() / getTelefono() -> personaMedico.email() / telefono() (fallback profesional)
        // - medico.getCuit() -> perfil.cuit()
        // - paciente.* -> personaPaciente.* (DNI, fechaNacimiento, sexo, documento, CUIL, domicilio estructurado, credencial, emailHabilitado)
        Profesional profesional = profesionales.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        Persona personaMedico = personas.porEmail(medicoEmail).orElse(null);
        PerfilProfesionalData perfil = profesional.perfil();

        // Gate 1: Plan Entitlement (§7). En el monolito valida subscriptionService.hasFeature("recetas_electronicas").
        // En el servicio de profesionales el plan está copiado en profesional.puede_recetar.
        if (!profesional.puedeRecetar()) {
            throw new IllegalStateException("Tu plan actual no incluye recetas electrónicas oficiales. Podés actualizar tu suscripción al plan Clínico.");
        }
        // Nota: El Gate 2 del monolito (rol != PSIQUIATRA y no canPrescribe) no aplica aquí porque la ruta
        // /api/recetas/enviar exige rol PSIQUIATRA a nivel de autenticación/controlador.

        Persona paciente = personas.porId(dto.getPacienteId())
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));

        // Acceso (decisión 2): después de buscar al paciente, exige que esté atendido por este médico.
        accesoPacientes.exigirAtendido(profesional.id(), paciente.id());

        // Validaciones de datos requeridos por QBI2 / Innovamed
        List<String> datosFaltantes = new ArrayList<>();
        boolean pacienteTieneDni = paciente.numeroDocumento() != null
                || (paciente.dni() != null && !paciente.dni().isBlank());
        if (!pacienteTieneDni) datosFaltantes.add("el DNI del paciente (en su ficha clínica)");
        if (paciente.fechaNacimiento() == null) datosFaltantes.add("la fecha de nacimiento del paciente (en su ficha clínica)");

        boolean medicoTieneDni = (personaMedico != null && personaMedico.numeroDocumento() != null)
                || (profesional.numeroDocumento() != null)
                || (personaMedico != null && personaMedico.dni() != null && !personaMedico.dni().isBlank());
        if (!medicoTieneDni) datosFaltantes.add("tu DNI (en Configuración > Perfil profesional)");

        boolean medicoTieneMatricula = (perfil != null && perfil.matriculaNumero() != null)
                || (perfil != null && perfil.matricula() != null && !perfil.matricula().isBlank());
        if (!medicoTieneMatricula) datosFaltantes.add("tu número de matrícula (en Configuración > Perfil profesional)");

        if (perfil == null || perfil.codigoRefeps() == null || perfil.codigoRefeps().isBlank()) {
            datosFaltantes.add("tu código REFEPS (en Configuración > Perfil profesional)");
        } else if (!perfil.codigoRefeps().matches("\\d{12}")) {
            datosFaltantes.add("tu código REFEPS (tiene que ser el número de 12 dígitos que te asignó SISA — corregilo en Configuración > Perfil profesional)");
        }

        boolean medicoTieneDomicilio = (perfil != null && perfil.domicilioAtencion() != null && !perfil.domicilioAtencion().isBlank())
                || (personaMedico != null && personaMedico.direccion() != null && !personaMedico.direccion().isBlank())
                || (personaMedico != null && personaMedico.domicilioCalle() != null && !personaMedico.domicilioCalle().isBlank());
        if (!medicoTieneDomicilio) datosFaltantes.add("tu dirección profesional (en Configuración > Perfil profesional — QBI2 la exige aunque atiendas 100% online)");

        if (paciente.credencialCodEntidad() != null
                && (paciente.credencialPan() == null || !paciente.credencialPan().trim().matches("[A-Za-z0-9\\-/. ]{6,30}"))) {
            datosFaltantes.add("el número de afiliado del paciente (tiene que tener entre 6 y 30 caracteres — corregilo desde \"Editar datos\" en la ficha del paciente)");
        }
        if (!datosFaltantes.isEmpty()) {
            throw new RecetaElectronicaException(
                    "No se pudo emitir la receta electrónica: falta completar " + String.join(", ", datosFaltantes)
                            + ". QBI2/Innovamed exige estos datos para validar la receta.", null);
        }

        // Format medication list to single string representation
        String medsFormatted = dto.getMedications().stream()
                .map(m -> {
                    String linea = String.format("- %s (%s, %s, %s)", m.getName(),
                            m.getDosage() != null ? m.getDosage() : "",
                            m.getFrequency() != null ? m.getFrequency() : "",
                            m.getDuration() != null ? m.getDuration() : "");
                    if (m.getLaboratorio() != null && !m.getLaboratorio().trim().isEmpty()) {
                        linea += " — Laboratorio: " + m.getLaboratorio().trim();
                    }
                    return linea;
                })
                .collect(Collectors.joining("\n"));

        // Laboratorios entrados a mano por el médico, uno por medicamento que lo tenga cargado —
        // se guardan aparte del texto de arriba para poder calcular la métrica "medicamentos por
        // laboratorio" del historial sin tener que parsear ese string de display.
        List<String> laboratoriosIngresados = dto.getMedications().stream()
                .map(RecetaDto.MedicamentoDto::getLaboratorio)
                .filter(lab -> lab != null && !lab.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
        String laboratoriosJson;
        try {
            laboratoriosJson = objectMapper.writeValueAsString(laboratoriosIngresados);
        } catch (Exception e) {
            laboratoriosJson = "[]";
        }

        // Build QBI2 / Innovamed Request
        Qbi2RecetaDtos.CoberturaDto coberturaPaciente = null;
        if (paciente.credencialCodEntidad() != null || (paciente.credencialPan() != null && !paciente.credencialPan().isBlank())) {
            coberturaPaciente = Qbi2RecetaDtos.CoberturaDto.builder()
                    .idFinanciador(paciente.credencialCodEntidad() != null ? String.valueOf(paciente.credencialCodEntidad()) : null)
                    .plan(paciente.credencialPlan())
                    .numero(paciente.credencialPan())
                    .build();
        }

        Qbi2RecetaDtos.DomicilioDto domicilioPaciente = null;
        if (paciente.domicilioCalle() != null && !paciente.domicilioCalle().isBlank()) {
            domicilioPaciente = Qbi2RecetaDtos.DomicilioDto.builder()
                    .calle(paciente.domicilioCalle())
                    .numero(paciente.domicilioNumero())
                    .piso(paciente.domicilioPiso())
                    .dpto(paciente.domicilioDpto())
                    .codigoPostal(paciente.domicilioCodigoPostal())
                    .localidad(paciente.domicilioLocalidad())
                    .provincia(paciente.domicilioProvincia())
                    .pais(paciente.domicilioPais() != null && !paciente.domicilioPais().isBlank() ? paciente.domicilioPais() : "Argentina")
                    .build();
        }

        Qbi2RecetaDtos.PacienteReceta pacienteReceta = Qbi2RecetaDtos.PacienteReceta.builder()
                .nombre(paciente.nombre())
                .apellido(paciente.apellido() != null && !paciente.apellido().isBlank() ? paciente.apellido() : paciente.nombre())
                .tipoDoc(paciente.tipoDocumento() != null && !paciente.tipoDocumento().isBlank() ? paciente.tipoDocumento() : "DNI")
                .nroDoc(paciente.numeroDocumento() != null ? String.valueOf(paciente.numeroDocumento()) : paciente.dni())
                .sexo(paciente.sexo() != null && !paciente.sexo().isBlank() ? paciente.sexo() : "M")
                .fechaNacimiento(paciente.fechaNacimiento().toString())
                .email(paciente.email())
                .telefono(paciente.telefono())
                .cuil(paciente.cuil() != null ? String.valueOf(paciente.cuil()) : null)
                .localidad(paciente.domicilioLocalidad())
                .provincia(paciente.domicilioProvincia())
                // QBI2's propio swagger (PacienteRecetaDto.pais) documenta este campo como
                // "Requerido si el tipo de documento es Pasaporte" — es un campo de nivel
                // superior, distinto del pais dentro de domicilio, y hasta ahora nunca se
                // mandaba: toda receta a nombre de un paciente con Pasaporte era rechazada
                // por QBI2 aunque el resto del payload fuera correcto.
                .pais(paciente.domicilioPais() != null && !paciente.domicilioPais().isBlank() ? paciente.domicilioPais() : "Argentina")
                .cobertura(coberturaPaciente)
                .domicilio(domicilioPaciente)
                .build();

        String matriculaTipo = perfil != null && perfil.matriculaTipo() != null && !perfil.matriculaTipo().isBlank()
                ? perfil.matriculaTipo() : "MP";
        String matriculaNumero = perfil != null && perfil.matriculaNumero() != null
                ? String.valueOf(perfil.matriculaNumero())
                : (perfil != null ? perfil.matricula() : null);
        String matriculaProvincia = perfil != null && perfil.matriculaProvincia() != null && !perfil.matriculaProvincia().isBlank()
                ? perfil.matriculaProvincia() : "Córdoba";
        String especialidad = perfil != null && perfil.especialidad() != null && !perfil.especialidad().isBlank()
                ? perfil.especialidad() : "Psiquiatría";

        Qbi2RecetaDtos.MatriculaDto matriculaDto = Qbi2RecetaDtos.MatriculaDto.builder()
                .tipo(matriculaTipo)
                .numero(matriculaNumero)
                .provincia(matriculaProvincia)
                .especialidad(especialidad)
                .build();

        String medicoNombre = personaMedico != null && personaMedico.nombre() != null
                ? personaMedico.nombre() : profesional.nombre();
        String medicoApellido = personaMedico != null && personaMedico.apellido() != null
                ? personaMedico.apellido() : profesional.apellido();

        String linea1 = perfil != null && perfil.selloLinea1() != null && !perfil.selloLinea1().isBlank()
                ? perfil.selloLinea1() : ("Dr. " + medicoNombre + " " + (medicoApellido != null ? medicoApellido : ""));
        String linea2 = perfil != null && perfil.selloLinea2() != null && !perfil.selloLinea2().isBlank()
                ? perfil.selloLinea2() : (especialidad != null && !especialidad.isBlank() ? especialidad : "Psiquiatría");
        String linea3 = perfil != null && perfil.selloLinea3() != null && !perfil.selloLinea3().isBlank()
                ? perfil.selloLinea3() : (matriculaTipo + " " + matriculaNumero);

        Qbi2RecetaDtos.SelloDto selloDto = Qbi2RecetaDtos.SelloDto.builder()
                .linea1(linea1)
                .linea2(linea2)
                .linea3(linea3)
                .build();

        String medicoDni = personaMedico != null && personaMedico.numeroDocumento() != null
                ? String.valueOf(personaMedico.numeroDocumento())
                : (personaMedico != null && personaMedico.dni() != null && !personaMedico.dni().isBlank()
                    ? personaMedico.dni()
                    : (profesional.numeroDocumento() != null ? String.valueOf(profesional.numeroDocumento()) : null));
        String medicoTipoDoc = personaMedico != null && personaMedico.tipoDocumento() != null && !personaMedico.tipoDocumento().isBlank()
                ? personaMedico.tipoDocumento()
                : (profesional.tipoDocumento() != null && !profesional.tipoDocumento().isBlank() ? profesional.tipoDocumento() : "DNI");
        String medicoSexo = personaMedico != null && personaMedico.sexo() != null && !personaMedico.sexo().isBlank()
                ? personaMedico.sexo()
                : (profesional.sexo() != null && !profesional.sexo().isBlank() ? profesional.sexo() : "M");
        LocalDate fechaNac = personaMedico != null && personaMedico.fechaNacimiento() != null
                ? personaMedico.fechaNacimiento()
                : profesional.fechaNacimiento();
        String medicoEmailVal = personaMedico != null && personaMedico.email() != null
                ? personaMedico.email() : profesional.email();
        String medicoTelefono = personaMedico != null && personaMedico.telefono() != null
                ? personaMedico.telefono() : profesional.telefono();

        Qbi2RecetaDtos.MedicoReceta medicoReceta = Qbi2RecetaDtos.MedicoReceta.builder()
                .nombre(medicoNombre)
                .apellido(medicoApellido != null && !medicoApellido.isBlank() ? medicoApellido : medicoNombre)
                .sexo(medicoSexo)
                .tipoDoc(medicoTipoDoc)
                .nroDoc(medicoDni)
                .fechaNacimiento(fechaNac != null ? fechaNac.toString() : null)
                .email(medicoEmailVal)
                .telefono(medicoTelefono)
                .especialidad(especialidad)
                .pais("Argentina")
                .matricula(matriculaDto)
                .sello(selloDto)
                .idTributario(perfil != null ? perfil.cuit() : null)
                .profesion("Médico")
                .idREFEPS(perfil != null ? perfil.codigoRefeps() : null)
                .build();

        // Not every médico has a physical consultorio — many work 100% online (ofrecePresencial=false),
        // so there's often no real address to send. QBI2's lugarAtencion.domicilio is entirely optional
        // (every field nullable, nothing in RecetaRequestDto.required references it), so instead of
        // inventing a placeholder address, we only include what the médico actually entered and omit
        // "domicilio" altogether when there's none — never send a fabricated address on a legal document.
        String consultorioNombre = "Consultorio Dr. " + medicoNombre + " " + (medicoApellido != null ? medicoApellido : "");
        String direccionStr = perfil != null && perfil.domicilioAtencion() != null && !perfil.domicilioAtencion().isBlank()
                ? perfil.domicilioAtencion()
                : (personaMedico != null && personaMedico.direccion() != null && !personaMedico.direccion().isBlank() ? personaMedico.direccion() : null);

        boolean tieneDomicilioEstructurado = personaMedico != null && personaMedico.domicilioCalle() != null && !personaMedico.domicilioCalle().isBlank();
        boolean tieneAlgunDomicilio = tieneDomicilioEstructurado || (direccionStr != null && !direccionStr.isBlank());

        Qbi2RecetaDtos.DomicilioDto domicilioConsultorio = null;
        if (tieneAlgunDomicilio) {
            domicilioConsultorio = Qbi2RecetaDtos.DomicilioDto.builder()
                    .calle(personaMedico != null ? personaMedico.domicilioCalle() : null)
                    .numero(personaMedico != null ? personaMedico.domicilioNumero() : null)
                    .localidad(personaMedico != null ? personaMedico.domicilioLocalidad() : null)
                    .provincia(personaMedico != null ? personaMedico.domicilioProvincia() : null)
                    .pais(tieneDomicilioEstructurado ? "Argentina" : null)
                    .direccion(direccionStr)
                    .build();
        }

        Qbi2RecetaDtos.LugarAtencionDto lugarAtencion = Qbi2RecetaDtos.LugarAtencionDto.builder()
                .nombreConsultorio(consultorioNombre)
                .domicilio(domicilioConsultorio)
                .build();

        List<Qbi2RecetaDtos.MedicamentoRequest> reqMedicamentos = dto.getMedications().stream()
                .map(m -> Qbi2RecetaDtos.MedicamentoRequest.builder()
                        .nombreProducto(m.getName())
                        .nombreDroga(m.getNombreDroga() != null && !m.getNombreDroga().isBlank() ? m.getNombreDroga() : m.getName())
                        .presentacion(m.getDosage() != null && !m.getDosage().isBlank() ? m.getDosage() : "Comprimidos")
                        .cantidad(1)
                        .posologia((m.getFrequency() != null ? m.getFrequency() : "") + (m.getDuration() != null ? ", " + m.getDuration() : ""))
                        // No fallback: a wrong regNo points to the wrong commercial product, which is
                        // worse than sending none. Only real matches from QBI2's own medicamento
                        // catalog (GetMedicamento, wired in the frontend's search) populate this.
                        .regNo(m.getRegNo() != null && !m.getRegNo().isBlank() ? m.getRegNo() : null)
                        // QBI2's GetRecipe/apirecipe/Receta swagger (Core.Dtos.MedicamentoDto.permiteSustitucion)
                        // only accepts "S" | "N" | null — NOT "SI"/"NO". Sending "SI"/"NO" made every
                        // receta fail with error QBI34 "REVISE LOS TIPOS DE DATO DE LOS CAMPOS INGRESADOS",
                        // confirmed 2026-08-06 against the real HML endpoint (see audit notes).
                        .permiteSustitucion(Boolean.TRUE.equals(m.getNoSustituible()) ? "N" : "S")
                        .build())
                .collect(Collectors.toList());

        Qbi2RecetaDtos.RecetaRequest qbiRequest = Qbi2RecetaDtos.RecetaRequest.builder()
                .diagnostico(dto.getDiagnosis() != null && !dto.getDiagnosis().isBlank() ? dto.getDiagnosis() : "TRASTORNO DE ANSIEDAD GENERALIZADA")
                .indicaciones(dto.getNotes())
                .observaciones("Emitida desde TranquiApp")
                .paciente(pacienteReceta)
                .medico(medicoReceta)
                .lugarAtencion(lugarAtencion)
                .medicamentos(reqMedicamentos)
                .build();

        Qbi2RecetaDtos.RecetaResponse qbiResponse;
        try {
            qbiResponse = qbi2RecipeClient.generarReceta(qbiRequest);
        } catch (Exception e) {
            log.error("QBI2/Innovamed rechazó la generación de la receta electrónica para médico ID {} / paciente ID {}. " +
                    "No se emitirá receta local de respaldo.", profesional.id(), paciente.id(), e);
            // El error/mensaje que QBI2 devuelve en el body (p.ej. "QBI235: EL CAMPO MEDICO IDREFEPS
            // NO CUMPLE EL RANGO MÍNIMO O MÁXIMO DE CARACTERES") es exactamente lo que le hace falta
            // al profesional para corregir el dato — antes se descartaba y solo quedaba en el log,
            // así que el 422 que veía el médico nunca decía qué campo estaba mal. El frontend
            // (sanitizeErrorMessage en api.ts) reemplaza cualquier mensaje de más de 250 caracteres
            // por uno genérico, así que el mensaje final se arma corto y se recorta con margen.
            String detalleQbi2 = extraerMensajeQbi2(e);
            String mensaje = "No se pudo emitir la receta: QBI2/Innovamed la rechazó"
                    + (detalleQbi2 != null ? " (" + detalleQbi2 + ")" : "")
                    + ". No se generó ningún documento.";
            if (mensaje.length() > 240) {
                mensaje = mensaje.substring(0, 237) + "...";
            }
            throw new RecetaElectronicaException(mensaje, e);
        }

        if (qbiResponse == null || qbiResponse.getRecetas() == null || qbiResponse.getRecetas().isEmpty()) {
            String erroresMsg = qbiResponse != null && qbiResponse.getErrores() != null
                    ? qbiResponse.getErrores().stream()
                    .map(err -> err.getMensaje() != null ? err.getMensaje() : String.valueOf(err.getError()))
                    .collect(Collectors.joining("; "))
                    : "respuesta vacía";
            log.error("QBI2/Innovamed respondió sin recetas para médico ID {} / paciente ID {}. Errores: {}",
                    profesional.id(), paciente.id(), erroresMsg);
            // Mismo límite de 240 caracteres que en el catch de arriba — ver ese comentario.
            String mensajeSinRecetas = "No se pudo emitir la receta: QBI2/Innovamed no la validó (" + erroresMsg + ").";
            if (mensajeSinRecetas.length() > 240) {
                mensajeSinRecetas = mensajeSinRecetas.substring(0, 237) + "...";
            }
            throw new RecetaElectronicaException(mensajeSinRecetas, null);
        }

        Qbi2RecetaDtos.RecetaResult recetaResult = qbiResponse.getRecetas().get(0);
        // fechavencimiento/status no viven en "recetas" (Core.Dtos.RecetaPdfResponseDto) sino en el
        // array separado "response" (Core.Dtos.RecetaResponseDto) — ver comentario en Qbi2RecetaDtos.
        String fechaVencimiento = qbiResponse.getResponse() != null && !qbiResponse.getResponse().isEmpty()
                ? qbiResponse.getResponse().get(0).getFechavencimiento()
                : null;
        log.info("Receta oficial generada en QBI2/Innovamed para médico ID {} / paciente ID {}. idReceta={} verificador={}",
                profesional.id(), paciente.id(), recetaResult.getIdReceta(), recetaResult.getVerificador());

        Recetas.Receta receta = new Recetas.Receta(
                null,
                profesional.id(),
                paciente.id(),
                medsFormatted,
                laboratoriosJson,
                dto.getDiagnosis(),
                dto.getNotes(),
                recetaResult.getS3Link(),
                recetaResult.getIdReceta(),
                recetaResult.getVerificador(),
                recetaResult.getNroCUIR() != null ? String.join(", ", recetaResult.getNroCUIR()) : null,
                fechaVencimiento,
                qbiResponse.getIdTransaccion(),
                LocalDateTime.now()
        );

        Long recetaId = recetas.insertarNuevo(receta);
        receta = new Recetas.Receta(
                recetaId,
                receta.medicoId(),
                receta.pacienteId(),
                receta.medicamentos(),
                receta.laboratorios(),
                receta.diagnostico(),
                receta.indicaciones(),
                receta.pdfUrl(),
                receta.qbi2IdReceta(),
                receta.qbi2Verificador(),
                receta.qbi2NroCuir(),
                receta.qbi2FechaVencimiento(),
                receta.qbi2IdTransaccion(),
                receta.fechaEmision()
        );
        String pdfUrl = receta.pdfUrl();

        // Best-effort: this receta just got generated for real, so any of this médico's pending
        // "receta fuera de turno" purchases for this exact paciente are assumed fulfilled.
        // Se publica profesionales.receta.emitida.v1 por la outbox; lo consume el servicio de turnos
        // (y el monolito en vuelta atrás). Ningún fallo posterior deshace la receta ya emitida.
        try {
            outbox.publicar(
                    Topicos.PROFESIONALES_RECETA_EMITIDA_V1,
                    "receta",
                    String.valueOf(recetaId),
                    1,
                    new RecetaEmitidaData(profesional.id(), paciente.id())
            );
        } catch (Exception e) {
            log.error("Error al publicar receta emitida en outbox para médico ID {} / paciente ID {}",
                    profesional.id(), paciente.id(), e);
        }

        // Aviso al paciente con el link a la receta por la ruta interna de notificaciones (decisión 4).
        // Best-effort, igual que lo anterior: un fallo del mail nunca deshace una receta ya emitida en QBI2.
        // Si falla, se loguea sin el link ni datos de salud.
        if (paciente.email() != null && !paciente.email().isBlank() && paciente.emailHabilitado()) {
            try {
                clienteInterno.enviarMailReceta(new ClienteInterno.NotificacionReceta(
                        paciente.email(),
                        paciente.nombre(),
                        medicoNombre,
                        perfil != null ? perfil.matricula() : null,
                        pdfUrl,
                        receta.qbi2IdReceta()
                ));
            } catch (Exception e) {
                log.error("Error al enviar mail de receta emitida al paciente ID: {}", paciente.id(), e);
            }
        }

        return mapToDto(receta);
    }

    @Transactional(readOnly = true)
    public List<RecetaResponseDto> obtenerMisRecetas(String email) {
        Optional<Persona> personaOpt = personas.porEmail(email);
        Long usuarioId;
        String rol;
        if (personaOpt.isPresent()) {
            usuarioId = personaOpt.get().id();
            rol = personaOpt.get().rol();
        } else {
            Profesional prof = profesionales.porEmail(email)
                    .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
            usuarioId = prof.id();
            rol = prof.rol();
        }

        List<Recetas.Receta> lista;
        if ("PACIENTE".equalsIgnoreCase(rol)) {
            lista = recetas.porPaciente(usuarioId);
        } else {
            lista = recetas.porMedico(usuarioId);
        }
        return lista.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecetaResponseDto obtenerRecetaPorId(Long id, String email) {
        Long usuarioId = resolverUsuarioId(email);
        Recetas.Receta receta = recetas.porId(id)
                .orElseThrow(() -> new NoEncontradoException("Receta no encontrada"));

        if (!receta.pacienteId().equals(usuarioId) && !receta.medicoId().equals(usuarioId)) {
            throw new ProhibidoException("No tiene permiso para ver esta receta");
        }
        return mapToDto(receta);
    }

    private Long resolverUsuarioId(String email) {
        Optional<Persona> personaOpt = personas.porEmail(email);
        if (personaOpt.isPresent()) {
            return personaOpt.get().id();
        }
        return profesionales.porEmail(email)
                .map(Profesional::id)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
    }

    private List<String> parseLaboratorios(String laboratoriosJson) {
        if (laboratoriosJson == null || laboratoriosJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(laboratoriosJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public RecetaResponseDto mapToDto(Recetas.Receta receta) {
        if (receta == null) return null;

        RecetaResponseDto.MedicoSimpleDto medicoDto = null;
        if (receta.medicoId() != null) {
            Profesional m = profesionales.porId(receta.medicoId()).orElse(null);
            Persona personaMedico = personas.porId(receta.medicoId()).orElse(null);
            PerfilProfesionalData perfil = m != null ? m.perfil() : null;

            String nombre = personaMedico != null && personaMedico.nombre() != null
                    ? personaMedico.nombre() : (m != null ? m.nombre() : null);
            String apellido = personaMedico != null && personaMedico.apellido() != null
                    ? personaMedico.apellido() : (m != null ? m.apellido() : null);
            String email = personaMedico != null && personaMedico.email() != null
                    ? personaMedico.email() : (m != null ? m.email() : null);
            String matricula = perfil != null ? perfil.matricula() : null;
            String especialidad = perfil != null && perfil.especialidad() != null && !perfil.especialidad().isBlank()
                    ? perfil.especialidad()
                    : (perfil != null ? perfil.titulo() : null);

            medicoDto = RecetaResponseDto.MedicoSimpleDto.builder()
                    .id(receta.medicoId())
                    .nombre(nombre)
                    .apellido(apellido)
                    .matricula(matricula)
                    .especialidad(especialidad)
                    .email(email)
                    .build();
        }

        RecetaResponseDto.PacienteSimpleDto pacienteDto = null;
        if (receta.pacienteId() != null) {
            Persona p = personas.porId(receta.pacienteId()).orElse(null);
            if (p != null) {
                String dniStr = p.dni() != null ? p.dni()
                        : (p.numeroDocumento() != null ? String.valueOf(p.numeroDocumento()) : null);
                pacienteDto = RecetaResponseDto.PacienteSimpleDto.builder()
                        .id(p.id())
                        .nombre(p.nombre())
                        .apellido(p.apellido())
                        .dni(dniStr)
                        .email(p.email())
                        .telefono(p.telefono())
                        .build();
            }
        }

        return RecetaResponseDto.builder()
                .id(receta.id())
                .medico(medicoDto)
                .paciente(pacienteDto)
                .medicamentos(receta.medicamentos())
                .laboratorios(parseLaboratorios(receta.laboratorios()))
                .diagnostico(receta.diagnostico())
                .indicaciones(receta.indicaciones())
                .pdfUrl(receta.pdfUrl())
                .qbi2IdReceta(receta.qbi2IdReceta())
                .qbi2Verificador(receta.qbi2Verificador())
                .qbi2NroCuir(receta.qbi2NroCuir())
                .qbi2FechaVencimiento(receta.qbi2FechaVencimiento())
                .fechaEmision(receta.fechaEmision())
                .build();
    }

    // QBI2 devuelve sus errores de validación como JSON {"error":"QBI235","mensaje":"...","requestId":"..."} —
    // misma forma que Qbi2RecetaDtos.MedicamentoError (alias de su MensajeInvalidoDto), así que lo reusamos
    // para parsear el body en vez de mostrarle al profesional un mensaje genérico que no dice qué corregir.
    private String extraerMensajeQbi2(Throwable e) {
        if (!(e instanceof Qbi2RecipeException qe) || qe.getResponseBody() == null || qe.getResponseBody().isBlank()) {
            return null;
        }
        try {
            Qbi2RecetaDtos.MedicamentoError err = objectMapper
                    .readValue(qe.getResponseBody(), Qbi2RecetaDtos.MedicamentoError.class);
            if (err.getMensaje() == null || err.getMensaje().isBlank()) return null;
            return err.getError() != null ? (err.getError() + ": " + err.getMensaje()) : err.getMensaje();
        } catch (Exception parseError) {
            return null;
        }
    }
}
