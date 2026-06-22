package com.tranqui.app.service;

import com.tranqui.app.model.Mensaje;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.CanalPrioritarioDto;
import com.tranqui.app.model.dto.MensajeDto;
import com.tranqui.app.repository.MensajeRepository;
import com.tranqui.app.repository.UsuarioRepository;
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

        return mensajeRepository.save(mensaje);
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
}
