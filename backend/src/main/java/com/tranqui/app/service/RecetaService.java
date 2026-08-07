package com.tranqui.app.service;

import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.model.dto.RecetaResponseDto;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecetaService {

    private static final Logger log = LoggerFactory.getLogger(RecetaService.class);

    @Autowired
    private RecetaRepository recetaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private Qbi2RecipeClient qbi2RecipeClient;

    @Transactional
    public RecetaResponseDto emitirReceta(String medicoEmail, RecetaDto dto) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        Usuario paciente = usuarioRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        // These used to silently fall back to hardcoded placeholder values (a fake DNI, a fake
        // birth date...) whenever the real data was missing, so a receta could go out to QBI2
        // carrying identity data that belonged to nobody. Block instead, with a message that
        // says exactly what to fill in and where.
        List<String> datosFaltantes = new ArrayList<>();
        boolean pacienteTieneDni = paciente.getNumeroDocumento() != null
                || (paciente.getDni() != null && !paciente.getDni().isBlank());
        if (!pacienteTieneDni) datosFaltantes.add("el DNI del paciente (en su ficha clínica)");
        if (paciente.getFechaNacimiento() == null) datosFaltantes.add("la fecha de nacimiento del paciente (en su ficha clínica)");
        boolean medicoTieneDni = medico.getNumeroDocumento() != null
                || (medico.getDni() != null && !medico.getDni().isBlank());
        if (!medicoTieneDni) datosFaltantes.add("tu DNI (en Configuración > Perfil profesional)");
        boolean medicoTieneMatricula = medico.getMatriculaNumero() != null
                || (medico.getMatricula() != null && !medico.getMatricula().isBlank());
        if (!medicoTieneMatricula) datosFaltantes.add("tu número de matrícula (en Configuración > Perfil profesional)");
        if (medico.getCodigoRefeps() == null || medico.getCodigoRefeps().isBlank()) datosFaltantes.add("tu código REFEPS (en Configuración > Perfil profesional)");
        // QBI2 rechaza la receta con QBI248 "DEBE INFORMAR EL DOMICILIO DONDE SE REALIZÓ LA ATENCIÓN" si
        // no se manda ningún domicilio — esto aplica también a médicos 100% online (confirmado contra hml
        // el 2026-08-06): no hace falta un consultorio físico, pero sí algún domicilio profesional
        // declarado (puede ser el domicilio particular). No hay excepción por modalidad de atención.
        boolean medicoTieneDomicilio = (medico.getDomicilioAtencion() != null && !medico.getDomicilioAtencion().isBlank())
                || (medico.getDireccion() != null && !medico.getDireccion().isBlank())
                || (medico.getDomicilioCalle() != null && !medico.getDomicilioCalle().isBlank());
        if (!medicoTieneDomicilio) datosFaltantes.add("tu dirección profesional (en Configuración > Perfil profesional — QBI2 la exige aunque atiendas 100% online)");
        if (!datosFaltantes.isEmpty()) {
            throw new RecetaElectronicaException(
                    "No se pudo emitir la receta electrónica: falta completar " + String.join(", ", datosFaltantes)
                    + ". QBI2/Innovamed exige estos datos para validar la receta.", null);
        }

        // Format medication list to single string representation
        String medsFormatted = dto.getMedications().stream()
                .map(m -> String.format("- %s (%s, %s, %s)", m.getName(),
                        m.getDosage() != null ? m.getDosage() : "",
                        m.getFrequency() != null ? m.getFrequency() : "",
                        m.getDuration() != null ? m.getDuration() : ""))
                .collect(Collectors.joining("\n"));

        // Build QBI2 / Innovamed Request
        com.tranqui.app.model.dto.Qbi2RecetaDtos.CoberturaDto coberturaPaciente = null;
        if (paciente.getCredencialCodEntidad() != null || (paciente.getCredencialPan() != null && !paciente.getCredencialPan().isBlank())) {
            coberturaPaciente = com.tranqui.app.model.dto.Qbi2RecetaDtos.CoberturaDto.builder()
                    .idFinanciador(paciente.getCredencialCodEntidad() != null ? String.valueOf(paciente.getCredencialCodEntidad()) : null)
                    .plan(paciente.getCredencialPlan())
                    .numero(paciente.getCredencialPan())
                    .build();
        }

        com.tranqui.app.model.dto.Qbi2RecetaDtos.DomicilioDto domicilioPaciente = null;
        if (paciente.getDomicilioCalle() != null && !paciente.getDomicilioCalle().isBlank()) {
            domicilioPaciente = com.tranqui.app.model.dto.Qbi2RecetaDtos.DomicilioDto.builder()
                    .calle(paciente.getDomicilioCalle())
                    .numero(paciente.getDomicilioNumero())
                    .piso(paciente.getDomicilioPiso())
                    .dpto(paciente.getDomicilioDpto())
                    .codigoPostal(paciente.getDomicilioCodigoPostal())
                    .localidad(paciente.getDomicilioLocalidad())
                    .provincia(paciente.getDomicilioProvincia())
                    .pais(paciente.getDomicilioPais() != null && !paciente.getDomicilioPais().isBlank() ? paciente.getDomicilioPais() : "Argentina")
                    .build();
        }

        com.tranqui.app.model.dto.Qbi2RecetaDtos.PacienteReceta pacienteReceta = com.tranqui.app.model.dto.Qbi2RecetaDtos.PacienteReceta.builder()
                    .nombre(paciente.getNombre())
                    .apellido(paciente.getApellido() != null && !paciente.getApellido().isBlank() ? paciente.getApellido() : paciente.getNombre())
                    .tipoDoc(paciente.getTipoDocumento() != null && !paciente.getTipoDocumento().isBlank() ? paciente.getTipoDocumento() : "DNI")
                    .nroDoc(paciente.getNumeroDocumento() != null ? String.valueOf(paciente.getNumeroDocumento()) : paciente.getDni())
                    .sexo(paciente.getSexo() != null && !paciente.getSexo().isBlank() ? paciente.getSexo() : "M")
                    .fechaNacimiento(paciente.getFechaNacimiento().toString())
                    .email(paciente.getEmail())
                    .telefono(paciente.getTelefono())
                    .cuil(paciente.getCuil() != null ? String.valueOf(paciente.getCuil()) : null)
                    .localidad(paciente.getDomicilioLocalidad())
                    .provincia(paciente.getDomicilioProvincia())
                    // QBI2's propio swagger (PacienteRecetaDto.pais) documenta este campo como
                    // "Requerido si el tipo de documento es Pasaporte" — es un campo de nivel
                    // superior, distinto del pais dentro de domicilio, y hasta ahora nunca se
                    // mandaba: toda receta a nombre de un paciente con Pasaporte era rechazada
                    // por QBI2 aunque el resto del payload fuera correcto.
                    .pais(paciente.getDomicilioPais() != null && !paciente.getDomicilioPais().isBlank() ? paciente.getDomicilioPais() : "Argentina")
                    .cobertura(coberturaPaciente)
                    .domicilio(domicilioPaciente)
                    .build();

            com.tranqui.app.model.dto.Qbi2RecetaDtos.MatriculaDto matriculaDto = com.tranqui.app.model.dto.Qbi2RecetaDtos.MatriculaDto.builder()
                    .tipo(medico.getMatriculaTipo() != null && !medico.getMatriculaTipo().isBlank() ? medico.getMatriculaTipo() : "MP")
                    .numero(medico.getMatriculaNumero() != null ? String.valueOf(medico.getMatriculaNumero()) : medico.getMatricula())
                    .provincia(medico.getMatriculaProvincia() != null && !medico.getMatriculaProvincia().isBlank() ? medico.getMatriculaProvincia() : "Córdoba")
                    .especialidad(medico.getSpecialty() != null ? medico.getSpecialty() : "Psiquiatría")
                    .build();

            com.tranqui.app.model.dto.Qbi2RecetaDtos.SelloDto selloDto = com.tranqui.app.model.dto.Qbi2RecetaDtos.SelloDto.builder()
                    .linea1(medico.getSelloLinea1() != null && !medico.getSelloLinea1().isBlank() ? medico.getSelloLinea1() : ("Dr. " + medico.getNombre() + " " + (medico.getApellido() != null ? medico.getApellido() : "")))
                    .linea2(medico.getSelloLinea2() != null && !medico.getSelloLinea2().isBlank() ? medico.getSelloLinea2() : (medico.getSpecialty() != null ? medico.getSpecialty() : "Psiquiatría"))
                    .linea3(medico.getSelloLinea3() != null && !medico.getSelloLinea3().isBlank() ? medico.getSelloLinea3() : ((medico.getMatriculaTipo() != null && !medico.getMatriculaTipo().isBlank() ? medico.getMatriculaTipo() : "MP") + " " + (medico.getMatricula() != null ? medico.getMatricula() : "12345")))
                    .build();

            com.tranqui.app.model.dto.Qbi2RecetaDtos.MedicoReceta medicoReceta = com.tranqui.app.model.dto.Qbi2RecetaDtos.MedicoReceta.builder()
                    .nombre(medico.getNombre())
                    .apellido(medico.getApellido() != null && !medico.getApellido().isBlank() ? medico.getApellido() : medico.getNombre())
                    .sexo(medico.getSexo() != null && !medico.getSexo().isBlank() ? medico.getSexo() : "M")
                    .tipoDoc(medico.getTipoDocumento() != null && !medico.getTipoDocumento().isBlank() ? medico.getTipoDocumento() : "DNI")
                    .nroDoc(medico.getNumeroDocumento() != null ? String.valueOf(medico.getNumeroDocumento()) : medico.getDni())
                    .fechaNacimiento(medico.getFechaNacimiento() != null ? medico.getFechaNacimiento().toString() : null)
                    .email(medico.getEmail())
                    .telefono(medico.getTelefono())
                    .especialidad(medico.getSpecialty() != null ? medico.getSpecialty() : "Psiquiatría")
                    // Mismo campo requerido-si-Pasaporte que en PacienteReceta, ver comentario ahí.
                    .pais("Argentina")
                    .matricula(matriculaDto)
                    .sello(selloDto)
                    .idTributario(medico.getCuit())
                    .profesion("Médico")
                    .idREFEPS(medico.getCodigoRefeps())
                    .build();

            // Not every médico has a physical consultorio — many work 100% online (ofrecePresencial=false),
            // so there's often no real address to send. QBI2's lugarAtencion.domicilio is entirely optional
            // (every field nullable, nothing in RecetaRequestDto.required references it), so instead of
            // inventing a placeholder address, we only include what the médico actually entered and omit
            // "domicilio" altogether when there's none — never send a fabricated address on a legal document.
            String consultorioNombre = "Consultorio Dr. " + medico.getNombre() + " " + (medico.getApellido() != null ? medico.getApellido() : "");
            String direccionStr = medico.getDomicilioAtencion() != null && !medico.getDomicilioAtencion().isBlank() ? medico.getDomicilioAtencion() : (medico.getDireccion() != null && !medico.getDireccion().isBlank() ? medico.getDireccion() : null);

            boolean tieneDomicilioEstructurado = medico.getDomicilioCalle() != null && !medico.getDomicilioCalle().isBlank();
            boolean tieneAlgunDomicilio = tieneDomicilioEstructurado || (direccionStr != null && !direccionStr.isBlank());

            com.tranqui.app.model.dto.Qbi2RecetaDtos.DomicilioDto domicilioConsultorio = null;
            if (tieneAlgunDomicilio) {
                domicilioConsultorio = com.tranqui.app.model.dto.Qbi2RecetaDtos.DomicilioDto.builder()
                        .calle(medico.getDomicilioCalle())
                        .numero(medico.getDomicilioNumero())
                        .localidad(medico.getDomicilioLocalidad())
                        .provincia(medico.getDomicilioProvincia())
                        .pais(tieneDomicilioEstructurado ? "Argentina" : null)
                        .direccion(direccionStr)
                        .build();
            }

            com.tranqui.app.model.dto.Qbi2RecetaDtos.LugarAtencionDto lugarAtencion = com.tranqui.app.model.dto.Qbi2RecetaDtos.LugarAtencionDto.builder()
                    .nombreConsultorio(consultorioNombre)
                    .domicilio(domicilioConsultorio)
                    .build();

            List<com.tranqui.app.model.dto.Qbi2RecetaDtos.MedicamentoRequest> reqMedicamentos = dto.getMedications().stream()
                    .map(m -> com.tranqui.app.model.dto.Qbi2RecetaDtos.MedicamentoRequest.builder()
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

            com.tranqui.app.model.dto.Qbi2RecetaDtos.RecetaRequest qbiRequest = com.tranqui.app.model.dto.Qbi2RecetaDtos.RecetaRequest.builder()
                    .diagnostico(dto.getDiagnosis() != null && !dto.getDiagnosis().isBlank() ? dto.getDiagnosis() : "TRASTORNO DE ANSIEDAD GENERALIZADA")
                    .indicaciones(dto.getNotes())
                    .observaciones("Emitida desde TranquiApp")
                    .paciente(pacienteReceta)
                    .medico(medicoReceta)
                    .lugarAtencion(lugarAtencion)
                    .medicamentos(reqMedicamentos)
                    .build();

        com.tranqui.app.model.dto.Qbi2RecetaDtos.RecetaResponse qbiResponse;
        try {
            qbiResponse = qbi2RecipeClient.generarReceta(qbiRequest);
        } catch (Exception e) {
            log.error("QBI2/Innovamed rechazó la generación de la receta electrónica para médico ID {} / paciente ID {}. " +
                    "No se emitirá receta local de respaldo.", medico.getId(), paciente.getId(), e);
            throw new RecetaElectronicaException(
                    "No se pudo emitir la receta electrónica: el servicio de validación (QBI2/Innovamed) no la aceptó. " +
                    "No se generó ningún documento. Verificá la configuración con soporte antes de reintentar.", e);
        }

        if (qbiResponse == null || qbiResponse.getRecetas() == null || qbiResponse.getRecetas().isEmpty()) {
            String erroresMsg = qbiResponse != null && qbiResponse.getErrores() != null
                    ? qbiResponse.getErrores().stream()
                        .map(err -> err.getMensaje() != null ? err.getMensaje() : String.valueOf(err.getError()))
                        .collect(Collectors.joining("; "))
                    : "respuesta vacía";
            log.error("QBI2/Innovamed respondió sin recetas para médico ID {} / paciente ID {}. Errores: {}",
                    medico.getId(), paciente.getId(), erroresMsg);
            throw new RecetaElectronicaException(
                    "No se pudo emitir la receta electrónica: QBI2/Innovamed no devolvió una receta válida (" + erroresMsg + "). " +
                    "No se generó ningún documento.", null);
        }

        com.tranqui.app.model.dto.Qbi2RecetaDtos.RecetaResult recetaResult = qbiResponse.getRecetas().get(0);
        // fechavencimiento/status no viven en "recetas" (Core.Dtos.RecetaPdfResponseDto) sino en el
        // array separado "response" (Core.Dtos.RecetaResponseDto) — ver comentario en Qbi2RecetaDtos.
        String fechaVencimiento = qbiResponse.getResponse() != null && !qbiResponse.getResponse().isEmpty()
                ? qbiResponse.getResponse().get(0).getFechavencimiento()
                : null;
        log.info("Receta oficial generada en QBI2/Innovamed para médico ID {} / paciente ID {}. idReceta={} verificador={}",
                medico.getId(), paciente.getId(), recetaResult.getIdReceta(), recetaResult.getVerificador());

        Receta receta = Receta.builder()
                .medico(medico)
                .paciente(paciente)
                .medicamentos(medsFormatted)
                .diagnostico(dto.getDiagnosis())
                .indicaciones(dto.getNotes())
                .pdfUrl(recetaResult.getS3Link())
                .qbi2IdReceta(recetaResult.getIdReceta())
                .qbi2Verificador(recetaResult.getVerificador())
                .qbi2NroCuir(recetaResult.getNroCUIR() != null ? String.join(", ", recetaResult.getNroCUIR()) : null)
                .qbi2FechaVencimiento(fechaVencimiento)
                .qbi2IdTransaccion(qbiResponse.getIdTransaccion())
                .build();

        receta = recetaRepository.save(receta);
        String pdfUrl = receta.getPdfUrl();

        // Construct message for patient
        String text = String.format(
            "Hola %s, %s (Matrícula: %s) registró una prescripción para vos en Tranqui.\n\n" +
            "Medicación:\n%s\n\n" +
            "Indicaciones: %s\n\n" +
            "Descargá tu receta electrónica oficial acá: %s\n\n" +
            "N° de recetario: %s%s\n" +
            "Válida para presentar en cualquier farmacia adherida a Innovamed/QBI2.",
            paciente.getNombre(),
            medico.getNombre(),
            medico.getMatricula() != null ? medico.getMatricula() : "S/N",
            medsFormatted,
            receta.getIndicaciones() != null ? receta.getIndicaciones() : "Sin indicaciones extra",
            pdfUrl,
            receta.getQbi2IdReceta() != null ? receta.getQbi2IdReceta() : "S/N",
            receta.getQbi2Verificador() != null ? (" · Verificador: " + receta.getQbi2Verificador()) : ""
        );

        // Try to send WhatsApp notification
        try {
            if (paciente.getTelefono() != null && !paciente.getTelefono().trim().isEmpty()) {
                whatsAppService.enviarMensajeWhatsApp(paciente.getTelefono(), text);
            }
        } catch (Exception e) {
            log.error("Error al enviar WhatsApp de receta para paciente ID: {}", paciente.getId(), e);
        }

        return mapToDto(receta);
    }

    @Transactional(readOnly = true)
    public List<RecetaResponseDto> obtenerMisRecetas(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        List<Receta> recetas;
        if (usuario.getRol() == com.tranqui.app.model.Rol.PACIENTE) {
            recetas = recetaRepository.findByPacienteIdOrderByFechaEmisionDesc(usuario.getId());
        } else {
            recetas = recetaRepository.findByMedicoIdOrderByFechaEmisionDesc(usuario.getId());
        }
        return recetas.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecetaResponseDto obtenerRecetaPorId(Long id, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        Receta receta = recetaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Receta no encontrada"));

        if (!receta.getPaciente().getId().equals(usuario.getId()) && !receta.getMedico().getId().equals(usuario.getId())) {
            throw new AccessDeniedException("No tiene permiso para ver esta receta");
        }
        return mapToDto(receta);
    }

    public RecetaResponseDto mapToDto(Receta receta) {
        if (receta == null) return null;

        RecetaResponseDto.MedicoSimpleDto medicoDto = null;
        if (receta.getMedico() != null) {
            medicoDto = RecetaResponseDto.MedicoSimpleDto.builder()
                    .id(receta.getMedico().getId())
                    .nombre(receta.getMedico().getNombre())
                    .apellido(receta.getMedico().getApellido())
                    .matricula(receta.getMedico().getMatricula())
                    .especialidad(receta.getMedico().getSpecialty() != null ? receta.getMedico().getSpecialty() : receta.getMedico().getTitulo())
                    .email(receta.getMedico().getEmail())
                    .build();
        }

        RecetaResponseDto.PacienteSimpleDto pacienteDto = null;
        if (receta.getPaciente() != null) {
            String dniStr = receta.getPaciente().getDni() != null ? receta.getPaciente().getDni() :
                    (receta.getPaciente().getNumeroDocumento() != null ? String.valueOf(receta.getPaciente().getNumeroDocumento()) : null);
            pacienteDto = RecetaResponseDto.PacienteSimpleDto.builder()
                    .id(receta.getPaciente().getId())
                    .nombre(receta.getPaciente().getNombre())
                    .apellido(receta.getPaciente().getApellido())
                    .dni(dniStr)
                    .email(receta.getPaciente().getEmail())
                    .telefono(receta.getPaciente().getTelefono())
                    .build();
        }

        return RecetaResponseDto.builder()
                .id(receta.getId())
                .medico(medicoDto)
                .paciente(pacienteDto)
                .medicamentos(receta.getMedicamentos())
                .diagnostico(receta.getDiagnostico())
                .indicaciones(receta.getIndicaciones())
                .pdfUrl(receta.getPdfUrl())
                .qbi2IdReceta(receta.getQbi2IdReceta())
                .qbi2Verificador(receta.getQbi2Verificador())
                .qbi2NroCuir(receta.getQbi2NroCuir())
                .qbi2FechaVencimiento(receta.getQbi2FechaVencimiento())
                .fechaEmision(receta.getFechaEmision())
                .build();
    }
}
