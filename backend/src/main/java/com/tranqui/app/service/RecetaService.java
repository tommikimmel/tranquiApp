package com.tranqui.app.service;

import com.tranqui.app.model.Receta;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Receta emitirReceta(String medicoEmail, RecetaDto dto) {
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

        // NOTE: this is a placeholder, internal-only record — there is no real PDF, no digital
        // signature, and no legal validity yet. Real electronic prescriptions require the QBI2
        // Recipe integration (pending); until that's wired in, do not present this as a signed
        // document anywhere (WhatsApp copy below, frontend, etc).
        String pdfUrl = "https://tranquiapp.com/api/recetas/pdf/" + receta.getId();
        receta.setPdfUrl(pdfUrl);
        receta = recetaRepository.save(receta);

        // Construct message for patient
        String text = String.format(
            "Hola %s, %s (Matrícula: %s) registró una prescripción para vos en Tranqui.\n\n" +
            "Medicación:\n%s\n\n" +
            "Indicaciones: %s\n\n" +
            "Podés ver el resumen acá: %s\n\n" +
            "Este resumen es una constancia interna, no un documento firmado digitalmente. Para uso en farmacias, confirmá los detalles con tu médico.",
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

        return receta;
    }

    @Transactional(readOnly = true)
    public List<Receta> obtenerMisRecetas(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        if (usuario.getRol() == com.tranqui.app.model.Rol.PACIENTE) {
            return recetaRepository.findByPacienteId(usuario.getId());
        } else {
            return recetaRepository.findByMedicoId(usuario.getId());
        }
    }
}
