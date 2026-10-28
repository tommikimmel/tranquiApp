package com.tranqui.app.service;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validaciones y actualización de los datos personales (ficha) del paciente.
 * Compartido entre la ruta de la interfaz (ClinicalService /api/pacientes/{id}) y la ruta
 * interna para el servicio de profesionales (PUT /internal/monolito/pacientes/{id}).
 * Publica identidad.usuario.actualizado para que el servicio de turnos refresque su copia.
 */
@Component
public class FichaPaciente {

    private static final Set<String> TIPOS_DOCUMENTO_VALIDOS = Set.of("DNI", "LC", "LE", "Pasaporte");

    private final UsuarioRepository usuarioRepository;
    private final OutboxPublisher outboxPublisher;

    public FichaPaciente(UsuarioRepository usuarioRepository, OutboxPublisher outboxPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.outboxPublisher = outboxPublisher;
    }

    // Same manual accumulate-and-throw convention as RecetaService.emitirReceta's
    // datosFaltantes check: collect every problem instead of failing on the first one, so a
    // psiquiatra correcting a form sees all the issues at once instead of one per submit.
    // Fields are only validated when present — this endpoint doubles as the "borrar datos
    // cargados" action (Historia Clínica), which intentionally sends most fields as null.
    public void validar(PacienteDto dto) {
        List<String> errores = new ArrayList<>();

        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            errores.add("el nombre del paciente es obligatorio");
        }

        if (dto.getTipoDocumento() != null && !dto.getTipoDocumento().trim().isEmpty()
                && !TIPOS_DOCUMENTO_VALIDOS.contains(dto.getTipoDocumento().trim())) {
            errores.add("el tipo de documento debe ser DNI, LC, LE o Pasaporte");
        }

        if (dto.getNumeroDocumento() != null) {
            int digits = String.valueOf(Math.abs(dto.getNumeroDocumento())).length();
            if (dto.getNumeroDocumento() <= 0 || digits < 6 || digits > 9) {
                errores.add("el número de documento debe tener entre 6 y 9 dígitos");
            }
        }

        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().trim().isEmpty()) {
            try {
                LocalDate fecha = LocalDate.parse(dto.getFechaNacimiento().trim());
                if (fecha.isAfter(LocalDate.now())) {
                    errores.add("la fecha de nacimiento no puede ser futura");
                } else if (fecha.isBefore(LocalDate.of(1900, 1, 1))) {
                    errores.add("la fecha de nacimiento no es válida");
                }
            } catch (java.time.format.DateTimeParseException e) {
                errores.add("la fecha de nacimiento no tiene un formato válido");
            }
        }

        if (dto.getTelefono() != null && !dto.getTelefono().trim().isEmpty()) {
            String soloDigitos = dto.getTelefono().trim().replaceFirst("^\\+54\\s*", "").replaceAll("\\D", "");
            if (!soloDigitos.matches("\\d{6,15}")) {
                errores.add("el teléfono debe contener solo números, entre 6 y 15 dígitos");
            }
        }

        if (dto.getDomicilio() != null) {
            PacienteDto.DomicilioDto d = dto.getDomicilio();
            List<String> partes = List.of(
                    d.getCalle() == null ? "" : d.getCalle().trim(),
                    d.getNumero() == null ? "" : d.getNumero().trim(),
                    d.getLocalidad() == null ? "" : d.getLocalidad().trim(),
                    d.getProvincia() == null ? "" : d.getProvincia().trim());
            boolean algunaCompleta = partes.stream().anyMatch(p -> !p.isEmpty());
            boolean todasCompletas = partes.stream().allMatch(p -> !p.isEmpty());
            if (algunaCompleta && !todasCompletas) {
                errores.add("el domicilio requiere calle, número, localidad y provincia completos");
            }
        }

        if (dto.getCredencial() != null) {
            PacienteDto.CredencialInfoDto c = dto.getCredencial();
            if (c.getCodEntidad() == null) {
                errores.add("falta seleccionar la obra social");
            }
            if (c.getPan() == null || c.getPan().trim().isEmpty()) {
                errores.add("el número de afiliado es obligatorio cuando hay obra social");
            } else if (!c.getPan().trim().matches("[A-Za-z0-9\\-/. ]{6,30}")) {
                // QBI2 rechaza la receta con QBI124 "LA CANTIDAD DE CARACTERES NO CUMPLE EL RANGO
                // MINIMO O MAXIMO PARA EL FINANCIADOR SELECCIONADO" recién al emitir — sin este
                // chequeo, un número de afiliado de 1-2 caracteres pasaba esta validación y llegaba
                // hasta QBI2 antes de ser rechazado.
                errores.add("el número de afiliado debe tener entre 6 y 30 caracteres válidos (letras, números, guiones, barras o puntos)");
            }
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Datos del paciente inválidos: " + String.join("; ", errores) + ".");
        }
    }

    @Transactional
    public Usuario actualizar(Long pacienteId, PacienteDto dto) {
        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado: " + pacienteId));
        return aplicarCambiosYGuardar(paciente, dto);
    }

    @Transactional
    public Usuario aplicarCambiosYGuardar(Usuario paciente, PacienteDto dto) {
        validar(dto);

        paciente.setNombre(dto.getNombre());
        paciente.setApellido(dto.getApellido());
        paciente.setNumAfiliado(dto.getNumAfiliado());
        paciente.setDni(dto.getDni());
        paciente.setObraSocial(dto.getObraSocial());
        paciente.setDireccion(dto.getDireccion());
        String patientTel = dto.getTelefono();
        if (patientTel != null && !patientTel.trim().isEmpty() && !patientTel.trim().startsWith("+54")) {
            patientTel = "+54 " + patientTel.trim();
        }
        paciente.setTelefono(patientTel);
        paciente.setSexo(dto.getSexo());
        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().trim().isEmpty()) {
            paciente.setFechaNacimiento(LocalDate.parse(dto.getFechaNacimiento().trim()));
        }
        paciente.setCuil(dto.getCuil());
        paciente.setTipoDocumento(dto.getTipoDocumento());
        paciente.setNumeroDocumento(dto.getNumeroDocumento());
        paciente.setDatosOfuscado(dto.getDatosOfuscado());

        if (dto.getDomicilio() != null) {
            paciente.setDomicilioCalle(dto.getDomicilio().getCalle());
            paciente.setDomicilioNumero(dto.getDomicilio().getNumero());
            paciente.setDomicilioPiso(dto.getDomicilio().getPiso());
            paciente.setDomicilioDpto(dto.getDomicilio().getDpto());
            paciente.setDomicilioCodigoPostal(dto.getDomicilio().getCodigoPostal());
            paciente.setDomicilioLocalidad(dto.getDomicilio().getLocalidad());
            paciente.setDomicilioProvincia(dto.getDomicilio().getProvincia());
            paciente.setDomicilioPais(dto.getDomicilio().getPais());
        }

        if (dto.getCredencial() != null) {
            paciente.setCredencialCodEntidad(dto.getCredencial().getCodEntidad());
            paciente.setCredencialPan(dto.getCredencial().getPan());
            paciente.setCredencialPlan(dto.getCredencial().getPlan());
            paciente.setCredencialToken(dto.getCredencial().getToken());
            if (dto.getCredencial().getPlan() != null && !dto.getCredencial().getPlan().trim().isEmpty()) {
                String baseOs = (dto.getObraSocial() != null && !dto.getObraSocial().isBlank()) ? dto.getObraSocial() : "Obra Social";
                paciente.setObraSocial(baseOs + " " + dto.getCredencial().getPlan());
            } else {
                paciente.setObraSocial(dto.getObraSocial());
            }
        } else {
            paciente.setCredencialCodEntidad(null);
            paciente.setCredencialPan(null);
            paciente.setCredencialPlan(null);
            paciente.setCredencialToken(null);
            paciente.setObraSocial(null);
        }

        Usuario guardado = usuarioRepository.saveAndFlush(paciente);

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                String.valueOf(guardado.getId()),
                guardado.getAggregateVersion(),
                new UsuarioActualizadoData(guardado.getId(), guardado.getEmail(), guardado.getNombre(),
                        guardado.getApellido(), guardado.getTelefono(),
                        guardado.getRol() != null ? guardado.getRol().name() : null,
                        guardado.isNotificacionesEmailHabilitadas())
        );
        return guardado;
    }
}
