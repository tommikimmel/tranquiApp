package com.tranqui.identidad.web;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.CodigoVerificacionGeneradoData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.cuenta.AccountService;
import com.tranqui.identidad.cuenta.ImageUtils;
import com.tranqui.identidad.datos.PerfilesRegistro;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.novedades.NovedadesService;
import com.tranqui.identidad.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.identidad.seguridad.GoogleAuthService;
import com.tranqui.identidad.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Usuarios usuarios;
    private final PerfilesRegistro perfilesRegistro;
    private final AccountService accountService;
    private final GoogleAuthService googleAuthService;
    private final EmisorJwtUsuario emisorJwtUsuario;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final Environment env;
    private final NovedadesService novedadesService;
    private final String clientId;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthController(
            Usuarios usuarios,
            PerfilesRegistro perfilesRegistro,
            AccountService accountService,
            GoogleAuthService googleAuthService,
            EmisorJwtUsuario emisorJwtUsuario,
            PasswordEncoder passwordEncoder,
            OutboxPublisher outboxPublisher,
            Environment env,
            NovedadesService novedadesService,
            @Value("${google.client-id:dummy-client-id}") String clientId) {
        this.usuarios = usuarios;
        this.perfilesRegistro = perfilesRegistro;
        this.accountService = accountService;
        this.googleAuthService = googleAuthService;
        this.emisorJwtUsuario = emisorJwtUsuario;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.env = env;
        this.novedadesService = novedadesService;
        this.clientId = clientId;
    }

    private static String trimToNull(String value, int maxLen) {
        String trimmed = value != null ? value.trim() : null;
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLen) {
            throw new IllegalArgumentException("Uno de los campos de la dirección de atención supera el largo máximo permitido (" + maxLen + " caracteres).");
        }
        return trimmed;
    }

    private Usuario usuarioAutenticado(HttpServletRequest request) {
        Object attr = request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (attr instanceof Usuario u) {
            return u;
        }
        return null;
    }

    private void agregarCookieSesion(HttpServletResponse response, String token, long maxAgeSegundos, boolean esMockGoogle) {
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        boolean lax = isDev || "dummy-client-id".equals(clientId) || esMockGoogle;
        boolean secure = !lax;
        String sameSite = lax ? "Lax" : "Strict";

        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", token != null ? token : "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSegundos)
                .sameSite(sameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static UserResponseDto toUserResponseDto(Usuario u) {
        UserResponseDto dto = new UserResponseDto(
                u.id(),
                u.nombre(),
                u.email(),
                u.rol(),
                u.telefono(),
                u.isPerfilCompleto(),
                u.terminosAceptadosEn() == null,
                u.mustChangePassword()
        );
        dto.setProfession(u.profession());
        dto.setCanPrescribe(u.isCanPrescribe());
        dto.setVerificadoAdmin(u.verificadoAdmin());
        dto.setLicenseVerifiedAt(u.licenseVerifiedAt());
        return dto;
    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDto dto) {
        String emailClean = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        String emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
        if (emailClean.isEmpty() || !emailClean.matches(emailRegex)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El formato del email es inválido.");
        }

        String rol = dto.getRol() != null ? dto.getRol().trim().toUpperCase() : "";
        if (!"PACIENTE".equals(rol) && !"PSIQUIATRA".equals(rol)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El rol debe ser PACIENTE o PSIQUIATRA.");
        }

        String rawPass = dto.getPassword();
        if (rawPass == null || rawPass.length() < 8 || !rawPass.matches(".*[A-Z].*") || !rawPass.matches(".*[a-z].*") || !rawPass.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La contraseña debe tener al menos 8 caracteres, incluir al menos una letra mayúscula, una minúscula y un número.");
        }

        if (usuarios.buscarPorEmail(emailClean).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El email ya está registrado");
        }

        if (!Boolean.TRUE.equals(dto.getAceptaTerminos())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Debés aceptar los términos y condiciones para registrarte.");
        }

        if (dto.getFotoUrl() != null && ImageUtils.decodedByteSize(dto.getFotoUrl()) > 3L * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La foto de perfil es demasiado grande (máx. 3MB). Elegí una imagen más liviana.");
        }

        String domicilioAtencionTrim = dto.getDomicilioAtencion() != null ? dto.getDomicilioAtencion().trim() : null;
        if (domicilioAtencionTrim != null && !domicilioAtencionTrim.isEmpty()
                && (domicilioAtencionTrim.length() < 8 || domicilioAtencionTrim.length() > 140)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    "El domicilio de atención debe tener entre 8 y 140 caracteres. Usá el buscador de direcciones y elegí una sugerencia en vez de pegar la dirección completa.");
        }

        String domicilioAtencionTorre = null;
        String domicilioAtencionPiso = null;
        String domicilioAtencionDepto = null;
        String domicilioAtencionBarrio = null;
        if (Boolean.TRUE.equals(dto.getOfrecePresencial())) {
            try {
                domicilioAtencionTorre = trimToNull(dto.getDomicilioAtencionTorre(), 50);
                domicilioAtencionPiso = trimToNull(dto.getDomicilioAtencionPiso(), 20);
                domicilioAtencionDepto = trimToNull(dto.getDomicilioAtencionDepto(), 20);
                domicilioAtencionBarrio = trimToNull(dto.getDomicilioAtencionBarrio(), 100);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
            }
        }

        String codigoVerificacion = String.format("%06d", secureRandom.nextInt(1000000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        String tel = dto.getTelefono();
        if (tel != null && !tel.trim().isEmpty() && !tel.trim().startsWith("+54")) {
            tel = "+54 " + tel.trim();
        }

        String profession = dto.getProfession() != null ? dto.getProfession() : ("PSIQUIATRA".equals(rol) ? "psiquiatra" : null);
        String licenseType = dto.getLicenseType() != null ? dto.getLicenseType() : dto.getMatriculaTipo();
        String licenseNumber = dto.getLicenseNumber() != null ? dto.getLicenseNumber()
                : (dto.getMatricula() != null ? dto.getMatricula() : (dto.getMatriculaNumero() != null ? String.valueOf(dto.getMatriculaNumero()) : null));
        String licenseJurisdiction = dto.getLicenseJurisdiction() != null ? dto.getLicenseJurisdiction() : dto.getMatriculaProvincia();
        String taxIdType = dto.getTaxIdType() != null ? dto.getTaxIdType() : "CUIT";
        String taxId = dto.getTaxId() != null ? dto.getTaxId() : dto.getCuit();
        String legalName = dto.getLegalName() != null ? dto.getLegalName()
                : (dto.getNombre() + " " + (dto.getApellido() != null ? dto.getApellido() : "")).trim();
        Integer ivaConditionId = dto.getIvaConditionId() != null ? dto.getIvaConditionId() : 6;
        String fiscalAddress = dto.getFiscalAddress() != null ? dto.getFiscalAddress() : domicilioAtencionTrim;
        Boolean verificadoAdmin = "PACIENTE".equals(rol) && profession == null ? true : null;

        Usuario usuario = Usuario.builder()
                .email(emailClean)
                .password(passwordEncoder.encode(dto.getPassword()))
                .rol(rol)
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .sexo(dto.getSexo())
                .fechaNacimiento(dto.getFechaNacimiento())
                .tipoDocumento(dto.getTipoDocumento())
                .numeroDocumento(dto.getNumeroDocumento())
                .telefono(tel)
                .obraSocial(dto.getObraSocial())
                .numAfiliado(dto.getNumAfiliado())
                .fotoUrl(dto.getFotoUrl())
                .profession(profession)
                .licenseType(licenseType)
                .licenseNumber(licenseNumber)
                .licenseJurisdiction(licenseJurisdiction)
                .licenseDocumentUrl(dto.getLicenseDocumentUrl())
                .taxIdType(taxIdType)
                .taxId(taxId)
                .legalName(legalName)
                .ivaConditionId(ivaConditionId)
                .fiscalAddress(fiscalAddress)
                .verificadoAdmin(verificadoAdmin)
                .emailVerificado(false)
                .codigoVerificacion(codigoVerificacion)
                .codigoVerificacionExpiresAt(expiresAt)
                .terminosAceptadosEn(LocalDateTime.now())
                .fechaRegistro(LocalDateTime.now())
                .perfilCompleto(true)
                .cuentaEliminada(false)
                .notificacionesEmailHabilitadas(true)
                .recibirNovedades(true)
                .build();

        Long id;
        try {
            id = usuarios.insertarNuevo(usuario);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El email ya está registrado");
        }

        if ("PSIQUIATRA".equals(rol)) {
            PerfilProfesionalData perfilData = new PerfilProfesionalData(
                    null,
                    domicilioAtencionTrim,
                    dto.getDomicilioLat(),
                    dto.getDomicilioLng(),
                    domicilioAtencionTorre,
                    domicilioAtencionPiso,
                    domicilioAtencionDepto,
                    domicilioAtencionBarrio,
                    dto.getMatricula(),
                    dto.getMatriculaTipo(),
                    dto.getMatriculaProvincia(),
                    dto.getMatriculaNumero(),
                    null,
                    null,
                    null,
                    null,
                    dto.getTitulo(),
                    dto.getSpecialty(),
                    dto.getCuit(),
                    null,
                    null,
                    Boolean.TRUE.equals(dto.getOfreceOnline()),
                    Boolean.TRUE.equals(dto.getOfrecePresencial()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            perfilesRegistro.guardar(id, perfilData);
        }

        Usuario guardado = usuarios.buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("Usuario registrado no encontrado: " + id));

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1,
                "usuario",
                String.valueOf(guardado.id()),
                guardado.aggregateVersion(),
                new UsuarioRegistradoData(
                        guardado.id(),
                        guardado.email(),
                        guardado.nombre(),
                        guardado.apellido(),
                        guardado.rol()
                )
        );

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
                "usuario",
                String.valueOf(guardado.id()),
                guardado.aggregateVersion(),
                new CodigoVerificacionGeneradoData(
                        guardado.id(),
                        guardado.email(),
                        codigoVerificacion,
                        "REGISTRO",
                        guardado.nombre()
                )
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registro exitoso. Se envió un código de verificación a tu correo.");
        response.put("email", emailClean);
        response.put("requiresVerification", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarios.buscarPorEmail(cleanEmail).orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        if (Boolean.TRUE.equals(usuario.emailVerificado())) {
            return ResponseEntity.ok("Tu email ya está verificado.");
        }

        if (usuario.codigoVerificacion() == null || !usuario.codigoVerificacion().equals(dto.getCodigo() != null ? dto.getCodigo().trim() : "")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de verificación incorrecto.");
        }

        if (usuario.codigoVerificacionExpiresAt() != null && LocalDateTime.now().isAfter(usuario.codigoVerificacionExpiresAt())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El código ha expirado. Solicitá uno nuevo.");
        }

        Usuario actualizado = usuario.toBuilder()
                .emailVerificado(true)
                .codigoVerificacion(null)
                .codigoVerificacionExpiresAt(null)
                .build();
        usuarios.actualizar(actualizado);

        return ResponseEntity.ok("Email verificado correctamente. Ya podés iniciar sesión.");
    }

    @Transactional
    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@RequestBody VerifyEmailDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarios.buscarPorEmail(cleanEmail).orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        if (Boolean.TRUE.equals(usuario.emailVerificado())) {
            return ResponseEntity.ok("Tu email ya está verificado.");
        }

        String nuevoCodigo = String.format("%06d", secureRandom.nextInt(1000000));
        Usuario actualizado = usuario.toBuilder()
                .codigoVerificacion(nuevoCodigo)
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        actualizado = usuarios.actualizar(actualizado);

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
                "usuario",
                String.valueOf(usuario.id()),
                actualizado.aggregateVersion(),
                new CodigoVerificacionGeneradoData(
                        usuario.id(),
                        usuario.email(),
                        nuevoCodigo,
                        "EMAIL_VERIFICATION",
                        usuario.nombre()
                )
        );

        return ResponseEntity.ok("Código reenviado a tu correo.");
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarios.buscarPorEmail(cleanEmail).orElse(null);

        if (usuario == null) {
            return ResponseEntity.ok("Si el correo está registrado, recibirás las instrucciones en tu bandeja de entrada.");
        }

        String resetCode = String.format("%06d", secureRandom.nextInt(1000000));
        Usuario actualizado = usuario.toBuilder()
                .resetPasswordCode(resetCode)
                .resetPasswordExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        actualizado = usuarios.actualizar(actualizado);

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_CODIGO_VERIFICACION_GENERADO_V1,
                "usuario",
                String.valueOf(usuario.id()),
                actualizado.aggregateVersion(),
                new CodigoVerificacionGeneradoData(
                        usuario.id(),
                        usuario.email(),
                        resetCode,
                        "PASSWORD_RESET",
                        usuario.nombre()
                )
        );

        return ResponseEntity.ok("Si el correo está registrado, recibirás las instrucciones en tu bandeja de entrada.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        String rawPass = dto.getNewPassword();

        if (rawPass == null || rawPass.length() < 8 || !rawPass.matches(".*[A-Z].*") || !rawPass.matches(".*[a-z].*") || !rawPass.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La nueva contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, una minúscula y un número.");
        }

        Usuario usuario = usuarios.buscarPorEmail(cleanEmail).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de recuperación inválido o expirado.");
        }

        if (usuario.resetPasswordCode() == null || !usuario.resetPasswordCode().equals(dto.getCodigo() != null ? dto.getCodigo().trim() : "")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de recuperación incorrecto.");
        }

        if (usuario.resetPasswordExpiresAt() != null && LocalDateTime.now().isAfter(usuario.resetPasswordExpiresAt())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El código de recuperación ha expirado. Solicitá uno nuevo.");
        }

        Usuario actualizado = usuario.toBuilder()
                .password(passwordEncoder.encode(rawPass))
                .resetPasswordCode(null)
                .resetPasswordExpiresAt(null)
                .build();
        usuarios.actualizar(actualizado);

        return ResponseEntity.ok("Tu contraseña ha sido restablecida con éxito. Ya podés iniciar sesión.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto loginRequestDto, HttpServletResponse response) {
        String cleanEmail = loginRequestDto.getEmail() != null ? loginRequestDto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarios.buscarPorEmail(cleanEmail).orElse(null);

        if (usuario == null || usuario.password() == null || !passwordEncoder.matches(loginRequestDto.getPassword(), usuario.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales incorrectas");
        }

        if (usuario.isCuentaEliminada()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Esta cuenta fue eliminada.");
        }

        if (Boolean.FALSE.equals(usuario.emailVerificado()) && !"ADMIN".equalsIgnoreCase(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Debés verificar tu correo electrónico antes de ingresar. Te enviamos un código al registrarte.");
        }

        String jwtToken = emisorJwtUsuario.emitir(usuario.email(), usuario.rol(), usuario.nombre(), usuario.id());
        agregarCookieSesion(response, jwtToken, 7 * 24 * 60 * 60, false);

        return ResponseEntity.ok(toUserResponseDto(usuario));
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody GoogleLoginDto googleLoginDto, HttpServletResponse response) {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken(googleLoginDto.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
        }

        Usuario usuario = googleAuthService.getOrCreateUsuario(payload);
        String jwtToken = emisorJwtUsuario.emitir(usuario.email(), usuario.rol(), usuario.nombre(), usuario.id());

        boolean esMock = googleLoginDto.getIdToken() != null && googleLoginDto.getIdToken().startsWith("mock-");
        agregarCookieSesion(response, jwtToken, 7 * 24 * 60 * 60, esMock);

        return ResponseEntity.ok(toUserResponseDto(usuario));
    }

    @PostMapping("/complete-profile")
    @Transactional
    public ResponseEntity<?> completeProfile(@RequestBody CompleteProfileDto dto, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()
                || dto.getApellido() == null || dto.getApellido().trim().isEmpty()
                || dto.getSexo() == null || dto.getSexo().trim().isEmpty()
                || dto.getFechaNacimiento() == null
                || dto.getTipoDocumento() == null || dto.getTipoDocumento().trim().isEmpty()
                || dto.getNumeroDocumento() == null
                || dto.getTelefono() == null || dto.getTelefono().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Completá todos los campos obligatorios.");
        }

        String tel = dto.getTelefono().trim();
        if (!tel.isEmpty() && !tel.startsWith("+54")) {
            tel = "+54 " + tel;
        }

        Usuario actualizado = usuario.toBuilder()
                .nombre(dto.getNombre().trim())
                .apellido(dto.getApellido().trim())
                .sexo(dto.getSexo())
                .fechaNacimiento(dto.getFechaNacimiento())
                .tipoDocumento(dto.getTipoDocumento())
                .numeroDocumento(dto.getNumeroDocumento())
                .telefono(tel)
                .obraSocial(dto.getObraSocial())
                .numAfiliado(dto.getNumAfiliado())
                .perfilCompleto(true)
                .build();
        actualizado = usuarios.actualizar(actualizado);

        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                "usuario",
                String.valueOf(usuario.id()),
                actualizado.aggregateVersion(),
                new com.tranqui.eventos.data.UsuarioActualizadoData(
                        usuario.id(),
                        usuario.email(),
                        actualizado.nombre(),
                        actualizado.apellido(),
                        actualizado.telefono(),
                        actualizado.rol(),
                        actualizado.isNotificacionesEmailHabilitadas()
                )
        );

        return ResponseEntity.ok(toUserResponseDto(actualizado));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toUserResponseDto(usuario));
    }

    @PostMapping("/aceptar-terminos")
    public ResponseEntity<?> aceptarTerminos(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario actualizado = usuario.toBuilder()
                .terminosAceptadosEn(LocalDateTime.now())
                .build();
        usuarios.actualizar(actualizado);
        return ResponseEntity.ok(toUserResponseDto(actualizado));
    }

    @GetMapping("/mi-cuenta")
    public ResponseEntity<?> obtenerMiCuenta(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(accountService.obtenerMiCuenta(usuario.email()));
    }

    @PutMapping("/mi-cuenta")
    public ResponseEntity<?> actualizarMiCuenta(@RequestBody MiCuentaDto dto, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(accountService.actualizarDatosPersonales(usuario.email(), dto));
    }

    @PutMapping("/mi-cuenta/notificaciones")
    public ResponseEntity<?> actualizarPreferenciasNotificacion(
            @RequestBody PreferenciasNotificacionDto dto,
            HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(accountService.actualizarPreferenciasNotificacion(usuario.email(), dto.isEmailHabilitado()));
    }

    // Novedades (mail masivo al terminar un mantenimiento): la preferencia vive en usuario.recibir_novedades y, en
    // producción, también en el contacto de Resend (ver NovedadesService). Mismo contrato que el monolito.
    @GetMapping("/mi-cuenta/novedades")
    public ResponseEntity<?> obtenerSuscripcionNovedades(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(java.util.Map.of("recibir", novedadesService.recibeNovedades(usuario)));
    }

    @PutMapping("/mi-cuenta/novedades")
    public ResponseEntity<?> actualizarSuscripcionNovedades(
            @RequestBody java.util.Map<String, Boolean> body,
            HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean recibir = body != null && Boolean.TRUE.equals(body.get("recibir"));
        novedadesService.cambiarSuscripcion(usuario, recibir);
        return ResponseEntity.ok(java.util.Map.of("recibir", recibir));
    }

    @PostMapping("/mi-cuenta/password")
    public ResponseEntity<?> cambiarPassword(@RequestBody CambiarPasswordDto dto, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            accountService.cambiarPassword(usuario.email(), dto.getCurrentPassword(), dto.getNewPassword());
            return ResponseEntity.ok("Contraseña actualizada correctamente.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/set-new-password")
    public ResponseEntity<?> setNewPassword(@RequestBody SetNewPasswordDto dto, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String rawPass = dto.getNewPassword();
        if (rawPass == null || rawPass.length() < 8 || !rawPass.matches(".*[A-Z].*") || !rawPass.matches(".*[a-z].*") || !rawPass.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La nueva contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, una minúscula y un número.");
        }

        Usuario actualizado = usuario.toBuilder()
                .password(passwordEncoder.encode(rawPass))
                .mustChangePassword(false)
                .build();
        usuarios.actualizar(actualizado);

        return ResponseEntity.ok(toUserResponseDto(actualizado));
    }

    @PostMapping("/mi-cuenta/eliminar")
    public ResponseEntity<?> eliminarCuenta(
            @RequestBody(required = false) EliminarCuentaDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            accountService.eliminarCuenta(usuario.email(), dto != null ? dto.getPassword() : null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        agregarCookieSesion(response, "", 0, false);
        return ResponseEntity.ok("Cuenta eliminada.");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        agregarCookieSesion(response, "", 0, false);
        return ResponseEntity.ok("Sesión cerrada");
    }
}
