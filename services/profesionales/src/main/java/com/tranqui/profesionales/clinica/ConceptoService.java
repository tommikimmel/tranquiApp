package com.tranqui.profesionales.clinica;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.ConceptoSolicitadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Personas.Persona;
import com.tranqui.profesionales.datos.Solicitudes;
import com.tranqui.profesionales.perfil.NoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Solicitud y emisión de documentos clínicos pagos (conceptos).
 * Con pagos.externo (Fase 4): la preferencia la crea el servicio de pagos tras el evento
 * profesionales.concepto.solicitado.v1; checkoutUrl responde vacío.
 */
@Service
public class ConceptoService {

    private final Solicitudes solicitudes;
    private final Personas personas;
    private final OutboxPublisher outbox;

    public ConceptoService(Solicitudes solicitudes, Personas personas, OutboxPublisher outbox) {
        this.solicitudes = solicitudes;
        this.personas = personas;
        this.outbox = outbox;
    }

    @Transactional
    public Dtos.PagoResponseDto crearSolicitud(Dtos.SolicitarConceptoDto dto, String pacienteEmail) {
        Persona paciente = personas.porEmail(pacienteEmail)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado"));
        Persona medico = personas.porId(dto.medicoId())
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        BigDecimal precio = dto.precio() != null ? dto.precio() : BigDecimal.ZERO;
        LocalDateTime ahora = LocalDateTime.now();

        Long id = solicitudes.insertarNuevo(new Solicitudes.Solicitud(
                null, paciente.id(), medico.id(), dto.tipoConcepto(), precio,
                "PENDIENTE", null, false, null, null, ahora));

        String titulo = dto.tipoConcepto() + " - " + medico.nombre();

        outbox.publicar(
                Topicos.PROFESIONALES_CONCEPTO_SOLICITADO_V1,
                "solicitud",
                String.valueOf(id),
                1,
                new ConceptoSolicitadoData(id, paciente.id(), medico.id(), dto.tipoConcepto(), precio, titulo)
        );

        return new Dtos.PagoResponseDto(id, "");
    }

    @Transactional(readOnly = true)
    public List<Dtos.SolicitudResponse> obtenerDocumentosPendientes(String medicoEmail) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));

        List<Solicitudes.Solicitud> lista = solicitudes.porMedicoYEstadoYEmitido(medico.id(), "APROBADO", false);
        Map<Long, Persona> personasMap = personas.porIds(lista.stream().map(Solicitudes.Solicitud::pacienteId).toList());

        Dtos.UsuarioResumen medicoResumen = new Dtos.UsuarioResumen(
                medico.id(), medico.nombre(), medico.apellido(), medico.email(), medico.telefono(), medico.dni());

        return lista.stream().map(s -> {
            Persona pac = personasMap.get(s.pacienteId());
            Dtos.UsuarioResumen pacResumen = pac != null ? new Dtos.UsuarioResumen(
                    pac.id(), pac.nombre(), pac.apellido(), pac.email(), pac.telefono(), pac.dni()) : null;
            return new Dtos.SolicitudResponse(
                    s.id(), pacResumen, medicoResumen, s.tipoConcepto(), s.precio(), s.estado(),
                    s.transactionId(), s.emitido(), s.urlDescarga(), s.fechaEmision(), s.fechaCreacion());
        }).toList();
    }

    @Transactional
    public Dtos.SolicitudResponse emitirDocumento(Long id, String urlDescarga, String medicoEmail) {
        Persona medico = personas.porEmail(medicoEmail)
                .orElseThrow(() -> new NoEncontradoException("Médico no encontrado"));
        Solicitudes.Solicitud solicitud = solicitudes.porId(id)
                .orElseThrow(() -> new NoEncontradoException("Solicitud no encontrada"));

        if (!solicitud.medicoId().equals(medico.id())) {
            throw new ProhibidoException("No está autorizado para emitir este documento");
        }

        if (!"APROBADO".equals(solicitud.estado())) {
            throw new IllegalStateException("El documento no ha sido abonado");
        }

        LocalDateTime ahora = LocalDateTime.now();
        solicitudes.actualizarEmitido(id, true, urlDescarga, ahora);

        Persona pac = personas.porId(solicitud.pacienteId()).orElse(null);
        Dtos.UsuarioResumen pacResumen = pac != null ? new Dtos.UsuarioResumen(
                pac.id(), pac.nombre(), pac.apellido(), pac.email(), pac.telefono(), pac.dni()) : null;
        Dtos.UsuarioResumen medicoResumen = new Dtos.UsuarioResumen(
                medico.id(), medico.nombre(), medico.apellido(), medico.email(), medico.telefono(), medico.dni());

        return new Dtos.SolicitudResponse(
                id, pacResumen, medicoResumen, solicitud.tipoConcepto(), solicitud.precio(),
                solicitud.estado(), solicitud.transactionId(), true, urlDescarga, ahora, solicitud.fechaCreacion());
    }
}
