package com.tranqui.identidad.cuenta;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.web.NoEncontradoException;
import com.tranqui.identidad.web.dto.MiCuentaDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class AccountService {

    private final Usuarios usuarios;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final BorradorContactoNovedades borradorContactoNovedades;

    public AccountService(Usuarios usuarios,
                          PasswordEncoder passwordEncoder,
                          OutboxPublisher outboxPublisher,
                          BorradorContactoNovedades borradorContactoNovedades) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.borradorContactoNovedades = borradorContactoNovedades;
    }

    public MiCuentaDto obtenerMiCuenta(String email) {
        Usuario u = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
        return construirDto(u);
    }

    @Transactional
    public MiCuentaDto actualizarDatosPersonales(String email, MiCuentaDto dto) {
        Usuario u = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        String tel = dto.getTelefono() != null ? dto.getTelefono().trim() : null;
        if (tel != null && !tel.isEmpty() && !tel.startsWith("+54")) {
            tel = "+54 " + tel;
        }

        if (dto.getFotoUrl() != null && ImageUtils.decodedByteSize(dto.getFotoUrl()) > 3L * 1024 * 1024) {
            throw new IllegalArgumentException("La foto de perfil es demasiado grande (máx. 3MB). Elegí una imagen más liviana.");
        }

        Usuario actualizado = u.toBuilder()
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .sexo(dto.getSexo())
                .fechaNacimiento(dto.getFechaNacimiento())
                .tipoDocumento(dto.getTipoDocumento())
                .numeroDocumento(dto.getNumeroDocumento())
                .dni(dto.getDni())
                .cuil(dto.getCuil())
                .obraSocial(dto.getObraSocial())
                .numAfiliado(dto.getNumAfiliado())
                .telefono(tel)
                .domicilioCalle(dto.getDomicilioCalle())
                .domicilioNumero(dto.getDomicilioNumero())
                .domicilioPiso(dto.getDomicilioPiso())
                .domicilioDpto(dto.getDomicilioDpto())
                .domicilioCodigoPostal(dto.getDomicilioCodigoPostal())
                .domicilioLocalidad(dto.getDomicilioLocalidad())
                .domicilioProvincia(dto.getDomicilioProvincia())
                .domicilioPais(dto.getDomicilioPais())
                .fotoUrl(dto.getFotoUrl())
                .build();

        usuarios.actualizar(actualizado);
        publicarUsuarioActualizado(u);
        return construirDto(actualizado);
    }

    @Transactional
    public MiCuentaDto actualizarPreferenciasNotificacion(String email, boolean emailHabilitado) {
        Usuario u = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        Usuario actualizado = u.toBuilder()
                .notificacionesEmailHabilitadas(emailHabilitado)
                .build();

        usuarios.actualizar(actualizado);
        publicarUsuarioActualizado(u);
        return construirDto(actualizado);
    }

    private void publicarUsuarioActualizado(Usuario u) {
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                "usuario",
                String.valueOf(u.id()),
                u.aggregateVersion(),
                new UsuarioActualizadoData(
                        u.id(),
                        u.email(),
                        u.nombre(),
                        u.apellido(),
                        u.telefono(),
                        u.rol(),
                        u.isNotificacionesEmailHabilitadas()
                )
        );
    }

    @Transactional
    public void cambiarPassword(String email, String currentPassword, String newPassword) {
        Usuario u = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        if (u.password() != null) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, u.password())) {
                throw new IllegalArgumentException("La contraseña actual es incorrecta.");
            }
        }

        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*") || !newPassword.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, una minúscula y un número.");
        }

        Usuario actualizado = u.toBuilder()
                .password(passwordEncoder.encode(newPassword))
                .build();
        usuarios.actualizar(actualizado);
    }

    @Transactional
    public void eliminarCuenta(String email, String password) {
        Usuario u = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        if (u.password() != null) {
            if (password == null || !passwordEncoder.matches(password, u.password())) {
                throw new IllegalArgumentException("La contraseña ingresada es incorrecta.");
            }
        }

        final String emailOriginal = u.email();
        if (borradorContactoNovedades != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        borradorContactoNovedades.eliminarContacto(emailOriginal);
                    }
                });
            } else {
                borradorContactoNovedades.eliminarContacto(emailOriginal);
            }
        }

        Usuario actualizado = u.toBuilder()
                .nombre("Usuario eliminado")
                .apellido(null)
                .telefono(null)
                .password(null)
                .fotoUrl(null)
                .dni(null)
                .cuil(null)
                .tipoDocumento(null)
                .numeroDocumento(null)
                .sexo(null)
                .fechaNacimiento(null)
                .direccion(null)
                .obraSocial(null)
                .numAfiliado(null)
                .domicilioCalle(null)
                .domicilioNumero(null)
                .domicilioPiso(null)
                .domicilioDpto(null)
                .domicilioCodigoPostal(null)
                .domicilioLocalidad(null)
                .domicilioProvincia(null)
                .domicilioPais(null)
                .email("deleted-" + u.id() + "@tranquiapp.local")
                .cuentaEliminada(true)
                .anonimizadoEn(LocalDateTime.now())
                .build();

        usuarios.actualizar(actualizado);
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1,
                "usuario",
                String.valueOf(u.id()),
                u.aggregateVersion(),
                new UsuarioEliminadoData(
                        u.id(),
                        "solicitud_usuario"
                )
        );
    }

    public MiCuentaDto construirDto(Usuario u) {
        MiCuentaDto dto = new MiCuentaDto();
        dto.setId(u.id());
        dto.setNombre(u.nombre());
        dto.setApellido(u.apellido());
        dto.setEmail(u.email());
        dto.setTelefono(u.telefono());
        dto.setSexo(u.sexo());
        dto.setFechaNacimiento(u.fechaNacimiento());
        dto.setTipoDocumento(u.tipoDocumento());
        dto.setNumeroDocumento(u.numeroDocumento());
        dto.setDni(u.dni());
        dto.setCuil(u.cuil());
        dto.setObraSocial(u.obraSocial());
        dto.setNumAfiliado(u.numAfiliado());
        dto.setFotoUrl(u.fotoUrl());
        dto.setDomicilioCalle(u.domicilioCalle());
        dto.setDomicilioNumero(u.domicilioNumero());
        dto.setDomicilioPiso(u.domicilioPiso());
        dto.setDomicilioDpto(u.domicilioDpto());
        dto.setDomicilioCodigoPostal(u.domicilioCodigoPostal());
        dto.setDomicilioLocalidad(u.domicilioLocalidad());
        dto.setDomicilioProvincia(u.domicilioProvincia());
        dto.setDomicilioPais(u.domicilioPais());
        dto.setTienePassword(u.password() != null);
        dto.setNotificacionesEmailHabilitadas(u.isNotificacionesEmailHabilitadas());
        dto.setRecibirNovedades(u.isRecibirNovedades());
        return dto;
    }
}
