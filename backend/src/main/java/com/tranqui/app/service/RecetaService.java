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

    @Transactional
    public RecetaResponseDto emitirReceta(String medicoEmail, RecetaDto dto) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        Usuario paciente = usuarioRepository.findById(dto.getPacienteId())
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        // Format medication list to single string representation
        String medsFormatted = dto.getMedications().stream()
                .map(m -> String.format("- %s (%s, %s, %s)", m.getName(), m.getDosage(), m.getFrequency(), m.getDuration()))
                .collect(Collectors.joining("\n"));

        Receta receta = Receta.builder()
                .medico(medico)
                .paciente(paciente)
                .medicamentos(medsFormatted)
                .diagnostico(dto.getDiagnosis())
                .indicaciones(dto.getNotes())
                .build();

        receta = recetaRepository.save(receta);

        String pdfUrl = "https://tranquiapp.com/api/recetas/pdf/" + receta.getId();
        receta.setPdfUrl(pdfUrl);
        receta = recetaRepository.save(receta);

        // Construct message for patient
        String text = String.format(
            "Hola %s, %s (Matrícula: %s) registró una prescripción para vos en Tranqui.\n\n" +
            "Medicación:\n%s\n\n" +
            "Indicaciones: %s\n\n" +
            "Podés ver el resumen acá: %s\n\n" +
            "Este resumen es una constancia interna. Para uso en farmacias, confirmá los detalles con tu médico.",
            paciente.getNombre(),
            medico.getNombre(),
            medico.getMatricula() != null ? medico.getMatricula() : "S/N",
            medsFormatted,
            receta.getIndicaciones() != null ? receta.getIndicaciones() : "Sin indicaciones extra",
            pdfUrl
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
                    .especialidad(receta.getMedico().getEspecialidad())
                    .email(receta.getMedico().getEmail())
                    .build();
        }

        RecetaResponseDto.PacienteSimpleDto pacienteDto = null;
        if (receta.getPaciente() != null) {
            pacienteDto = RecetaResponseDto.PacienteSimpleDto.builder()
                    .id(receta.getPaciente().getId())
                    .nombre(receta.getPaciente().getNombre())
                    .apellido(receta.getPaciente().getApellido())
                    .dni(receta.getPaciente().getDni() != null ? receta.getPaciente().getDni() : receta.getPaciente().getNumeroDocumento())
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
                .fechaEmision(receta.getFechaEmision())
                .build();
    }
}
