package com.tranqui.app.service;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.MiCuentaDto;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Backs the "Mi Cuenta" page (today only wired up for pacientes in the frontend, see
// MiCuentaView.tsx) — editar datos personales, cambiar contraseña, preferencias de
// notificación, y eliminar cuenta. Kept separate from MedicoService, which owns the
// médico-only profile fields (tarifas, matrícula, Google Calendar, etc.).
@Service
public class AccountService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.tranqui.app.service.novedades.NovedadesService novedadesService;

    @Transactional(readOnly = true)
    public MiCuentaDto obtenerMiCuenta(String email) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return construirDto(u);
    }

    @Transactional
    public MiCuentaDto actualizarDatosPersonales(String email, MiCuentaDto dto) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        u.setNombre(dto.getNombre());
        u.setApellido(dto.getApellido());
        u.setSexo(dto.getSexo());
        u.setFechaNacimiento(dto.getFechaNacimiento());
        u.setTipoDocumento(dto.getTipoDocumento());
        u.setNumeroDocumento(dto.getNumeroDocumento());
        u.setDni(dto.getDni());
        u.setCuil(dto.getCuil());
        u.setObraSocial(dto.getObraSocial());
        u.setNumAfiliado(dto.getNumAfiliado());

        String tel = dto.getTelefono() != null ? dto.getTelefono().trim() : null;
        if (tel != null && !tel.isEmpty() && !tel.startsWith("+54")) {
            tel = "+54 " + tel;
        }
        u.setTelefono(tel);

        u.setDomicilioCalle(dto.getDomicilioCalle());
        u.setDomicilioNumero(dto.getDomicilioNumero());
        u.setDomicilioPiso(dto.getDomicilioPiso());
        u.setDomicilioDpto(dto.getDomicilioDpto());
        u.setDomicilioCodigoPostal(dto.getDomicilioCodigoPostal());
        u.setDomicilioLocalidad(dto.getDomicilioLocalidad());
        u.setDomicilioProvincia(dto.getDomicilioProvincia());
        u.setDomicilioPais(dto.getDomicilioPais());

        if (dto.getFotoUrl() != null
                && com.tranqui.app.util.ImageUtils.decodedByteSize(dto.getFotoUrl()) > 3L * 1024 * 1024) {
            throw new IllegalArgumentException("La foto de perfil es demasiado grande (máx. 3MB). Elegí una imagen más liviana.");
        }
        u.setFotoUrl(dto.getFotoUrl());

        usuarioRepository.save(u);
        return construirDto(u);
    }

    @Transactional
    public MiCuentaDto actualizarPreferenciasNotificacion(String email, boolean emailHabilitado) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        u.setNotificacionesEmailHabilitadas(emailHabilitado);
        usuarioRepository.save(u);
        return construirDto(u);
    }

    @Transactional
    public boolean recibeNovedades(String email) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return novedadesService.recibeNovedades(u);
    }

    @Transactional
    public void cambiarSuscripcionNovedades(String email, boolean recibir) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        novedadesService.cambiarSuscripcion(u, recibir);
    }

    @Transactional
    public void cambiarPassword(String email, String currentPassword, String newPassword) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        // A cuenta creada solo con Google no tiene password todavía — permitir fijarla por
        // primera vez sin pedir la actual. Si ya tiene una, currentPassword es obligatoria.
        if (u.getPassword() != null) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, u.getPassword())) {
                throw new IllegalArgumentException("La contraseña actual es incorrecta.");
            }
        }

        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*") || !newPassword.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, una minúscula y un número.");
        }

        u.setPassword(passwordEncoder.encode(newPassword));
        usuarioRepository.save(u);
    }

    // Anonimiza en vez de borrar la fila: Turno/InformeClinico/SeguimientoDiario apuntan a este
    // id sin cascada, y la historia clínica tiene guarda legal mínima de 10 años (Ley 26.529) —
    // un DELETE real rompería esas referencias o destruiría datos que no se pueden borrar.
    @Transactional
    public void eliminarCuenta(String email, String password) {
        Usuario u = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        if (u.getPassword() != null) {
            if (password == null || !passwordEncoder.matches(password, u.getPassword())) {
                throw new IllegalArgumentException("La contraseña ingresada es incorrecta.");
            }
        }

        // El contacto de novedades en Resend se borra recién cuando la eliminación quedó confirmada.
        final String emailOriginal = u.getEmail();
        if (novedadesService != null) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                novedadesService.eliminarContacto(emailOriginal);
                            }
                        });
            } else {
                novedadesService.eliminarContacto(emailOriginal);
            }
        }

        u.setNombre("Usuario eliminado");
        u.setApellido(null);
        u.setTelefono(null);
        u.setPassword(null);
        u.setFotoUrl(null);
        u.setDni(null);
        u.setCuil(null);
        u.setTipoDocumento(null);
        u.setNumeroDocumento(null);
        u.setSexo(null);
        u.setFechaNacimiento(null);
        u.setDireccion(null);
        u.setObraSocial(null);
        u.setNumAfiliado(null);
        u.setDomicilioCalle(null);
        u.setDomicilioNumero(null);
        u.setDomicilioPiso(null);
        u.setDomicilioDpto(null);
        u.setDomicilioCodigoPostal(null);
        u.setDomicilioLocalidad(null);
        u.setDomicilioProvincia(null);
        u.setDomicilioPais(null);
        // Único por construcción (misma PK que ya era única) — evita chocar con el constraint
        // unique=true de email y libera el email real para que la persona pueda registrarse de
        // nuevo si quiere "empezar de cero".
        u.setEmail("deleted-" + u.getId() + "@tranquiapp.local");
        u.setCuentaEliminada(true);
        u.setAnonimizadoEn(LocalDateTime.now());

        usuarioRepository.save(u);
    }

    private MiCuentaDto construirDto(Usuario u) {
        return MiCuentaDto.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .telefono(u.getTelefono())
                .sexo(u.getSexo())
                .fechaNacimiento(u.getFechaNacimiento())
                .tipoDocumento(u.getTipoDocumento())
                .numeroDocumento(u.getNumeroDocumento())
                .dni(u.getDni())
                .cuil(u.getCuil())
                .obraSocial(u.getObraSocial())
                .numAfiliado(u.getNumAfiliado())
                .fotoUrl(u.getFotoUrl())
                .domicilioCalle(u.getDomicilioCalle())
                .domicilioNumero(u.getDomicilioNumero())
                .domicilioPiso(u.getDomicilioPiso())
                .domicilioDpto(u.getDomicilioDpto())
                .domicilioCodigoPostal(u.getDomicilioCodigoPostal())
                .domicilioLocalidad(u.getDomicilioLocalidad())
                .domicilioProvincia(u.getDomicilioProvincia())
                .domicilioPais(u.getDomicilioPais())
                .tienePassword(u.getPassword() != null)
                .notificacionesEmailHabilitadas(u.isNotificacionesEmailHabilitadas())
                .recibirNovedades(u.isRecibirNovedades())
                .build();
    }
}
