package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.SolicitarConceptoDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class ConceptoService {

    private static final Logger log = LoggerFactory.getLogger(ConceptoService.class);

    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Transactional
    public SolicitudDocumento crearSolicitud(SolicitarConceptoDto dto, String pacienteEmail) {
        Usuario paciente = usuarioRepository.findByEmail(pacienteEmail)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        Usuario medico = usuarioRepository.findById(dto.getMedicoId())
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        SolicitudDocumento solicitud = SolicitudDocumento.builder()
                .paciente(paciente)
                .medico(medico)
                .tipoConcepto(dto.getTipoConcepto())
                .precio(dto.getPrecio())
                .estado(EstadoPago.PENDIENTE)
                .build();

        return solicitudRepository.save(solicitud);
    }

    public String generarCheckoutUrl(SolicitudDocumento solicitud) {
        try {
            return mercadoPagoService.crearPreferenciaDocumento(solicitud);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar la preferencia de pago en Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Transactional
    public SolicitudDocumento emitirDocumento(Long solicitudId, String urlDescarga, String medicoEmail) {
        SolicitudDocumento solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));

        // Guard safety: ensure the authenticated doctor is the one who was assigned to the request
        if (!solicitud.getMedico().getEmail().equals(medicoEmail)) {
            throw new SecurityException("No está autorizado para emitir este documento");
        }

        if (solicitud.getEstado() != EstadoPago.APROBADO) {
            throw new IllegalStateException("El documento no ha sido abonado");
        }

        solicitud.setEmitido(true);
        solicitud.setUrlDescarga(urlDescarga);
        solicitud.setFechaEmision(LocalDateTime.now());
        
        enviarChatDocumento(solicitud);

        return solicitudRepository.save(solicitud);
    }

    private void enviarChatDocumento(SolicitudDocumento solicitud) {
        log.info("Enviando documento firmado vía chat seguro al paciente {}. Tipo: {}, Enlace: {}", 
                 solicitud.getPaciente().getNombre(), solicitud.getTipoConcepto(), solicitud.getUrlDescarga());
    }
}
