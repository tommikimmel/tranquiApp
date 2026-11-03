package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.tranqui.app.model.InformeClinico;
import com.tranqui.app.model.Receta;
import com.tranqui.app.model.SeguimientoDiario;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.repository.InformeClinicoRepository;
import com.tranqui.app.repository.RecetaRepository;
import com.tranqui.app.repository.SeguimientoDiarioRepository;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase 7: rutas internas del monolito para la carga inicial del servicio de profesionales.
 * Devuelve la historia clínica (seguimientos e informes), las recetas y las solicitudes de documentos,
 * paginados por id.
 * Protegidas por {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito/clinica")
public class InternoClinicaController {

    private static final int TAMANIO_MAXIMO = 1000;

    public record SeguimientoDiarioDto(
            Long id,
            Long pacienteId,
            Long medicoId,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate fecha,
            String estadoAnimo,
            String sintomas,
            String notas,
            LocalDateTime fechaCreacion,
            String estado,
            String motivo,
            String hashIntegridad
    ) {}

    public record InformeClinicoDto(
            Long id,
            Long pacienteId,
            Long medicoId,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate fecha,
            String tipoInforme,
            String planTrabajo,
            String contenido,
            String nombreArchivo,
            LocalDateTime fechaCreacion,
            String estado,
            Long informeOriginalId,
            String motivo,
            String hashIntegridad
    ) {}

    public record RecetaDto(
            Long id,
            Long medicoId,
            Long pacienteId,
            String medicamentos,
            String laboratorios,
            String diagnostico,
            String indicaciones,
            String pdfUrl,
            String qbi2IdReceta,
            String qbi2Verificador,
            String qbi2NroCuir,
            String qbi2FechaVencimiento,
            String qbi2IdTransaccion,
            LocalDateTime fechaEmision
    ) {}

    public record SolicitudDocumentoDto(
            Long id,
            Long pacienteId,
            Long medicoId,
            String tipoConcepto,
            BigDecimal precio,
            String estado,
            String transactionId,
            boolean emitido,
            String urlDescarga,
            LocalDateTime fechaEmision,
            LocalDateTime fechaCreacion
    ) {}

    private final SeguimientoDiarioRepository seguimientoDiarioRepository;
    private final InformeClinicoRepository informeClinicoRepository;
    private final RecetaRepository recetaRepository;
    private final SolicitudDocumentoRepository solicitudDocumentoRepository;

    public InternoClinicaController(SeguimientoDiarioRepository seguimientoDiarioRepository,
                                    InformeClinicoRepository informeClinicoRepository,
                                    RecetaRepository recetaRepository,
                                    SolicitudDocumentoRepository solicitudDocumentoRepository) {
        this.seguimientoDiarioRepository = seguimientoDiarioRepository;
        this.informeClinicoRepository = informeClinicoRepository;
        this.recetaRepository = recetaRepository;
        this.solicitudDocumentoRepository = solicitudDocumentoRepository;
    }

    @GetMapping("/seguimientos")
    @Transactional(readOnly = true)
    public List<SeguimientoDiarioDto> seguimientos(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                                   @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return seguimientoDiarioRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoClinicaController::seguimiento)
                .toList();
    }

    @GetMapping("/informes")
    @Transactional(readOnly = true)
    public List<InformeClinicoDto> informes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return informeClinicoRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoClinicaController::informe)
                .toList();
    }

    @GetMapping("/recetas")
    @Transactional(readOnly = true)
    public List<RecetaDto> recetas(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                   @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return recetaRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoClinicaController::receta)
                .toList();
    }

    @GetMapping("/solicitudes")
    @Transactional(readOnly = true)
    public List<SolicitudDocumentoDto> solicitudes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                                   @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return solicitudDocumentoRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoClinicaController::solicitud)
                .toList();
    }

    private static SeguimientoDiarioDto seguimiento(SeguimientoDiario s) {
        return new SeguimientoDiarioDto(
                s.getId(),
                s.getPaciente() != null ? s.getPaciente().getId() : null,
                s.getMedico() != null ? s.getMedico().getId() : null,
                s.getFecha(),
                s.getEstadoAnimo(),
                s.getSintomas(),
                s.getNotas(),
                s.getFechaCreacion(),
                s.getEstado(),
                s.getMotivo(),
                s.getHashIntegridad()
        );
    }

    private static InformeClinicoDto informe(InformeClinico i) {
        return new InformeClinicoDto(
                i.getId(),
                i.getPaciente() != null ? i.getPaciente().getId() : null,
                i.getMedico() != null ? i.getMedico().getId() : null,
                i.getFecha(),
                i.getTipoInforme(),
                i.getPlanTrabajo(),
                i.getContenido(),
                i.getNombreArchivo(),
                i.getFechaCreacion(),
                i.getEstado(),
                i.getInformeOriginalId(),
                i.getMotivo(),
                i.getHashIntegridad()
        );
    }

    private static RecetaDto receta(Receta r) {
        return new RecetaDto(
                r.getId(),
                r.getMedico() != null ? r.getMedico().getId() : null,
                r.getPaciente() != null ? r.getPaciente().getId() : null,
                r.getMedicamentos(),
                r.getLaboratorios(),
                r.getDiagnostico(),
                r.getIndicaciones(),
                r.getPdfUrl(),
                r.getQbi2IdReceta(),
                r.getQbi2Verificador(),
                r.getQbi2NroCuir(),
                r.getQbi2FechaVencimiento(),
                r.getQbi2IdTransaccion(),
                r.getFechaEmision()
        );
    }

    private static SolicitudDocumentoDto solicitud(SolicitudDocumento s) {
        return new SolicitudDocumentoDto(
                s.getId(),
                s.getPaciente() != null ? s.getPaciente().getId() : null,
                s.getMedico() != null ? s.getMedico().getId() : null,
                s.getTipoConcepto() != null ? s.getTipoConcepto().name() : null,
                s.getPrecio(),
                s.getEstado() != null ? s.getEstado().name() : null,
                s.getTransactionId(),
                s.isEmitido(),
                s.getUrlDescarga(),
                s.getFechaEmision(),
                s.getFechaCreacion()
        );
    }
}
