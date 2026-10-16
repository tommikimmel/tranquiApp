package com.tranqui.app.service;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.MensajeEnviadoData;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class MensajeService {

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Transactional
    public Mensaje guardarMensaje(MensajeDto dto, String remitenteEmail) {
        Usuario remitente = usuarioRepository.findByEmail(remitenteEmail)
                .orElseThrow(() -> new EntityNotFoundException("Remitente no encontrado"));

        Usuario destinatario = usuarioRepository.findById(dto.getDestinatarioId())
                .orElseThrow(() -> new EntityNotFoundException("Destinatario no encontrado"));

        Mensaje mensaje = Mensaje.builder()
                .remitente(remitente)
                .destinatario(destinatario)
                .contenido(dto.getContenido())
                .build();

        mensaje = mensajeRepository.save(mensaje);

        Long uMin = Math.min(remitente.getId(), destinatario.getId());
        Long uMax = Math.max(remitente.getId(), destinatario.getId());
        Long conversacionId = (uMin * 31) + uMax;
        outboxPublisher.publicar(
                Topicos.CHAT_MENSAJE_ENVIADO_V1,
                String.valueOf(mensaje.getId()),
                mensaje.getAggregateVersion(),
                new MensajeEnviadoData(
                        mensaje.getId(),
                        conversacionId,
                        remitente.getId(),
                        destinatario.getId(),
                        java.time.OffsetDateTime.now()
                )
        );

        return mensaje;
    }

    @Transactional(readOnly = true)
    public Page<Mensaje> obtenerHistorial(Long u1, Long u2, Pageable pageable) {
        return mensajeRepository.findChatHistory(u1, u2, pageable);
    }

    @Transactional(readOnly = true)
    public List<CanalPrioritarioDto> obtenerCanalesPrioritarios(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        LocalDate fechaActual = LocalDate.now();
        LocalTime horaActual = LocalTime.now();
        LocalDate fechaLimite = fechaActual.plusDays(3);
        LocalTime horaLimite = horaActual;

        return mensajeRepository.findPrioritizedChannels(
                medico.getId(),
                fechaActual,
                horaActual,
                fechaLimite,
                horaLimite
        );
    }

    @Transactional(readOnly = true)
    public List<CanalPrioritarioDto> obtenerCanalesVisitadores(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        return mensajeRepository.findVisitorChannels(medico.getId());
    }

    @Transactional(readOnly = true)
    public List<CanalPrioritarioDto> obtenerCanalesPaciente(String pacienteEmail) {
        Usuario paciente = usuarioRepository.findByEmail(pacienteEmail)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        return mensajeRepository.findPatientChannels(paciente.getId());
    }

    @Transactional
    public void marcarMensajesComoLeidos(Long remitenteId, String destinatarioEmail) {
        Usuario destinatario = usuarioRepository.findByEmail(destinatarioEmail)
                .orElseThrow(() -> new EntityNotFoundException("Destinatario no encontrado"));
        mensajeRepository.markAsRead(remitenteId, destinatario.getId());
    }

    @Transactional(readOnly = true)
    public boolean tieneMensajesSinLeer(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return mensajeRepository.hasUnreadMessages(usuario.getId());
    }
}
