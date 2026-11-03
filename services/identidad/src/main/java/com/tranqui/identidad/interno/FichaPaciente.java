package com.tranqui.identidad.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.web.NoEncontradoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * La ficha del paciente (datos personales), portada de {@code FichaPaciente} del monolito en la Fase 9. La edita el
 * servicio de profesionales por {@code PUT /internal/identidad/pacientes/{id}}: las mismas validaciones (todas los
 * errores juntos) y el mismo "borrar datos cargados", que manda casi todo en null y sin credencial.
 */
@Component
public class FichaPaciente {

    private static final Set<String> TIPOS_DOCUMENTO_VALIDOS = Set.of("DNI", "LC", "LE", "Pasaporte");

    /** El cuerpo que manda profesionales: el mismo JSON que {@code model/dto/PacienteDto} del monolito. */
    public record Ficha(String nombre, String apellido, String telefono, String dni, String direccion,
                        String obraSocial, String numAfiliado, String sexo, String fechaNacimiento, Long cuil,
                        String tipoDocumento, Integer numeroDocumento, String datosOfuscado,
                        Credencial credencial, Domicilio domicilio) {}

    public record Credencial(Integer codEntidad, String pan, String plan, String token) {}

    public record Domicilio(String calle, String numero, String piso, String dpto, String codigoPostal,
                            String localidad, String provincia, String pais) {}

    private final Usuarios usuarios;
    private final OutboxPublisher outboxPublisher;

    public FichaPaciente(Usuarios usuarios, OutboxPublisher outboxPublisher) {
        this.usuarios = usuarios;
        this.outboxPublisher = outboxPublisher;
    }

    public void validar(Ficha dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Datos del paciente inválidos: falta el cuerpo.");
        }
        List<String> errores = new ArrayList<>();

        if (dto.nombre() == null || dto.nombre().trim().isEmpty()) {
            errores.add("el nombre del paciente es obligatorio");
        }
        if (dto.tipoDocumento() != null && !dto.tipoDocumento().trim().isEmpty()
                && !TIPOS_DOCUMENTO_VALIDOS.contains(dto.tipoDocumento().trim())) {
            errores.add("el tipo de documento debe ser DNI, LC, LE o Pasaporte");
        }
        if (dto.numeroDocumento() != null) {
            int digitos = String.valueOf(Math.abs(dto.numeroDocumento())).length();
            if (dto.numeroDocumento() <= 0 || digitos < 6 || digitos > 9) {
                errores.add("el número de documento debe tener entre 6 y 9 dígitos");
            }
        }
        if (dto.fechaNacimiento() != null && !dto.fechaNacimiento().trim().isEmpty()) {
            try {
                LocalDate fecha = LocalDate.parse(dto.fechaNacimiento().trim());
                if (fecha.isAfter(LocalDate.now())) {
                    errores.add("la fecha de nacimiento no puede ser futura");
                } else if (fecha.isBefore(LocalDate.of(1900, 1, 1))) {
                    errores.add("la fecha de nacimiento no es válida");
                }
            } catch (java.time.format.DateTimeParseException e) {
                errores.add("la fecha de nacimiento no tiene un formato válido");
            }
        }
        if (dto.telefono() != null && !dto.telefono().trim().isEmpty() && !Validaciones.telefonoValido(dto.telefono())) {
            errores.add("el teléfono debe contener solo números, entre 6 y 15 dígitos");
        }
        if (dto.domicilio() != null) {
            Domicilio d = dto.domicilio();
            List<String> partes = List.of(
                    d.calle() == null ? "" : d.calle().trim(),
                    d.numero() == null ? "" : d.numero().trim(),
                    d.localidad() == null ? "" : d.localidad().trim(),
                    d.provincia() == null ? "" : d.provincia().trim());
            boolean algunaCompleta = partes.stream().anyMatch(p -> !p.isEmpty());
            boolean todasCompletas = partes.stream().allMatch(p -> !p.isEmpty());
            if (algunaCompleta && !todasCompletas) {
                errores.add("el domicilio requiere calle, número, localidad y provincia completos");
            }
        }
        if (dto.credencial() != null) {
            Credencial c = dto.credencial();
            if (c.codEntidad() == null) {
                errores.add("falta seleccionar la obra social");
            }
            if (c.pan() == null || c.pan().trim().isEmpty()) {
                errores.add("el número de afiliado es obligatorio cuando hay obra social");
            } else if (!c.pan().trim().matches("[A-Za-z0-9\\-/. ]{6,30}")) {
                // QBI2 rechaza la receta recién al emitir si el número de afiliado no cumple el rango.
                errores.add("el número de afiliado debe tener entre 6 y 30 caracteres válidos (letras, números, guiones, barras o puntos)");
            }
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Datos del paciente inválidos: " + String.join("; ", errores) + ".");
        }
    }

    @Transactional
    public Usuario actualizar(Long pacienteId, Ficha dto) {
        Usuario paciente = usuarios.buscarPorId(pacienteId)
                .orElseThrow(() -> new NoEncontradoException("Paciente no encontrado: " + pacienteId));
        validar(dto);

        Usuario.Builder b = paciente.toBuilder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .numAfiliado(dto.numAfiliado())
                .dni(dto.dni())
                .obraSocial(dto.obraSocial())
                .direccion(dto.direccion())
                .telefono(Validaciones.conPrefijo(dto.telefono()))
                .sexo(dto.sexo())
                .cuil(dto.cuil())
                .tipoDocumento(dto.tipoDocumento())
                .numeroDocumento(dto.numeroDocumento())
                .datosOfuscado(dto.datosOfuscado());
        if (dto.fechaNacimiento() != null && !dto.fechaNacimiento().trim().isEmpty()) {
            b.fechaNacimiento(LocalDate.parse(dto.fechaNacimiento().trim()));
        }
        if (dto.domicilio() != null) {
            Domicilio d = dto.domicilio();
            b.domicilioCalle(d.calle()).domicilioNumero(d.numero()).domicilioPiso(d.piso()).domicilioDpto(d.dpto())
                    .domicilioCodigoPostal(d.codigoPostal()).domicilioLocalidad(d.localidad())
                    .domicilioProvincia(d.provincia()).domicilioPais(d.pais());
        }
        if (dto.credencial() != null) {
            Credencial c = dto.credencial();
            b.credencialCodEntidad(c.codEntidad()).credencialPan(c.pan()).credencialPlan(c.plan()).credencialToken(c.token());
            if (c.plan() != null && !c.plan().trim().isEmpty()) {
                String baseOs = (dto.obraSocial() != null && !dto.obraSocial().isBlank()) ? dto.obraSocial() : "Obra Social";
                b.obraSocial(baseOs + " " + c.plan());
            } else {
                b.obraSocial(dto.obraSocial());
            }
        } else {
            // "Borrar datos cargados": sin credencial se borran la credencial y la obra social, como hoy.
            b.credencialCodEntidad(null).credencialPan(null).credencialPlan(null).credencialToken(null).obraSocial(null);
        }

        Usuario guardado = usuarios.actualizar(b.build());
        publicarActualizado(guardado);
        return guardado;
    }

    void publicarActualizado(Usuario u) {
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                "usuario",
                String.valueOf(u.id()),
                u.aggregateVersion(),
                new UsuarioActualizadoData(u.id(), u.email(), u.nombre(), u.apellido(), u.telefono(), u.rol(),
                        u.isNotificacionesEmailHabilitadas()));
    }
}
