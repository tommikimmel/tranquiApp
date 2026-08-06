package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.dto.GoogleLoginDto;
import com.tranqui.app.model.dto.UserResponseDto;
import com.tranqui.app.service.GoogleAuthService;
import com.tranqui.app.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.core.env.Environment env;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.tranqui.app.service.ResendEmailService resendEmailService;

    @org.springframework.beans.factory.annotation.Value("${google.client-id:dummy-client-id}")
    private String clientId;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody com.tranqui.app.model.dto.RegisterRequestDto registerRequestDto) {
        String emailClean = registerRequestDto.getEmail() != null ? registerRequestDto.getEmail().trim().toLowerCase() : "";
        String emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
        if (emailClean.isEmpty() || !emailClean.matches(emailRegex)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El formato del email es inválido.");
        }

        String rawPass = registerRequestDto.getPassword();
        if (rawPass == null || rawPass.length() < 8 || !rawPass.matches(".*[A-Z].*") || !rawPass.matches(".*[a-z].*") || !rawPass.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La contraseña debe tener al menos 8 caracteres, incluir al menos una letra mayúscula, una minúscula y un número.");
        }

        if (usuarioRepository.findByEmail(emailClean).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El email ya está registrado");
        }

        if (registerRequestDto.getFotoUrl() != null
                && com.tranqui.app.util.ImageUtils.decodedByteSize(registerRequestDto.getFotoUrl()) > 3L * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La foto de perfil es demasiado grande (máx. 3MB). Elegí una imagen más liviana.");
        }

        String codigoVerificacion = String.format("%06d", new java.util.Random().nextInt(1000000));
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusMinutes(15);

        String tel = registerRequestDto.getTelefono();
        if (tel != null && !tel.trim().isEmpty() && !tel.trim().startsWith("+54")) {
            tel = "+54 " + tel.trim();
        }

        Usuario usuario = Usuario.builder()
                .email(emailClean)
                .password(passwordEncoder.encode(registerRequestDto.getPassword()))
                .rol(registerRequestDto.getRol())
                .nombre(registerRequestDto.getNombre())
                .apellido(registerRequestDto.getApellido())
                .sexo(registerRequestDto.getSexo())
                .fechaNacimiento(registerRequestDto.getFechaNacimiento())
                .tipoDocumento(registerRequestDto.getTipoDocumento())
                .numeroDocumento(registerRequestDto.getNumeroDocumento())
                .telefono(tel)
                .obraSocial(registerRequestDto.getObraSocial())
                .numAfiliado(registerRequestDto.getNumAfiliado())
                .matricula(registerRequestDto.getMatricula())
                .titulo(registerRequestDto.getTitulo())
                .specialty(registerRequestDto.getSpecialty())
                .cuit(registerRequestDto.getCuit())
                .cuil(registerRequestDto.getCuil())
                .domicilioAtencion(registerRequestDto.getDomicilioAtencion())
                .domicilioLat(registerRequestDto.getDomicilioLat())
                .domicilioLng(registerRequestDto.getDomicilioLng())
                .matriculaTipo(registerRequestDto.getMatriculaTipo())
                .matriculaProvincia(registerRequestDto.getMatriculaProvincia())
                .matriculaNumero(registerRequestDto.getMatriculaNumero())
                .ofreceOnline(registerRequestDto.getOfreceOnline())
                .ofrecePresencial(registerRequestDto.getOfrecePresencial())
                .fotoUrl(registerRequestDto.getFotoUrl())
                .verificadoAdmin(registerRequestDto.getRol() == Rol.PACIENTE ? true : null)
                .emailVerificado(false)
                .codigoVerificacion(codigoVerificacion)
                .codigoVerificacionExpiresAt(expiresAt)
                .build();

        usuarioRepository.save(usuario);
        resendEmailService.enviarCodigoVerificacion(usuario.getEmail(), usuario.getNombre(), codigoVerificacion);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Registro exitoso. Se envió un código de verificación a tu correo.");
        response.put("email", emailClean);
        response.put("requiresVerification", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody com.tranqui.app.model.dto.VerifyEmailDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarioRepository.findByEmail(cleanEmail)
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        if (Boolean.TRUE.equals(usuario.getEmailVerificado())) {
            return ResponseEntity.ok("Tu email ya está verificado.");
        }

        if (usuario.getCodigoVerificacion() == null || !usuario.getCodigoVerificacion().equals(dto.getCodigo() != null ? dto.getCodigo().trim() : "")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de verificación incorrecto.");
        }

        if (usuario.getCodigoVerificacionExpiresAt() != null && java.time.LocalDateTime.now().isAfter(usuario.getCodigoVerificacionExpiresAt())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El código ha expirado. Solicitá uno nuevo.");
        }

        usuario.setEmailVerificado(true);
        usuario.setCodigoVerificacion(null);
        usuario.setCodigoVerificacionExpiresAt(null);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Email verificado correctamente. Ya podés iniciar sesión.");
    }

    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@RequestBody com.tranqui.app.model.dto.VerifyEmailDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarioRepository.findByEmail(cleanEmail)
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        if (Boolean.TRUE.equals(usuario.getEmailVerificado())) {
            return ResponseEntity.ok("Tu email ya está verificado.");
        }

        String nuevoCodigo = String.format("%06d", new java.util.Random().nextInt(1000000));
        usuario.setCodigoVerificacion(nuevoCodigo);
        usuario.setCodigoVerificacionExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        usuarioRepository.save(usuario);

        resendEmailService.enviarCodigoVerificacion(usuario.getEmail(), usuario.getNombre(), nuevoCodigo);
        return ResponseEntity.ok("Código reenviado a tu correo.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody com.tranqui.app.model.dto.ForgotPasswordDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarioRepository.findByEmail(cleanEmail).orElse(null);

        // Retornar mensaje estándar aun si no existe para evitar enumeración de emails
        if (usuario == null) {
            return ResponseEntity.ok("Si el correo está registrado, recibirás las instrucciones en tu bandeja de entrada.");
        }

        String resetCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        usuario.setResetPasswordCode(resetCode);
        usuario.setResetPasswordExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        usuarioRepository.save(usuario);

        resendEmailService.enviarCodigoRecuperacion(usuario.getEmail(), usuario.getNombre(), resetCode);
        return ResponseEntity.ok("Si el correo está registrado, recibirás las instrucciones en tu bandeja de entrada.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody com.tranqui.app.model.dto.ResetPasswordDto dto) {
        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        String rawPass = dto.getNewPassword();

        if (rawPass == null || rawPass.length() < 8 || !rawPass.matches(".*[A-Z].*") || !rawPass.matches(".*[a-z].*") || !rawPass.matches(".*[0-9].*")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La nueva contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, una minúscula y un número.");
        }

        Usuario usuario = usuarioRepository.findByEmail(cleanEmail).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de recuperación inválido o expirado.");
        }

        if (usuario.getResetPasswordCode() == null || !usuario.getResetPasswordCode().equals(dto.getCodigo() != null ? dto.getCodigo().trim() : "")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Código de recuperación incorrecto.");
        }

        if (usuario.getResetPasswordExpiresAt() != null && java.time.LocalDateTime.now().isAfter(usuario.getResetPasswordExpiresAt())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El código de recuperación ha expirado. Solicitá uno nuevo.");
        }

        usuario.setPassword(passwordEncoder.encode(rawPass));
        usuario.setResetPasswordCode(null);
        usuario.setResetPasswordExpiresAt(null);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Tu contraseña ha sido restablecida con éxito. Ya podés iniciar sesión.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody com.tranqui.app.model.dto.LoginRequestDto loginRequestDto, HttpServletResponse response) {
        String cleanEmail = loginRequestDto.getEmail() != null ? loginRequestDto.getEmail().trim().toLowerCase() : "";
        Usuario usuario = usuarioRepository.findByEmail(cleanEmail)
                .orElse(null);

        if (usuario == null || usuario.getPassword() == null || !passwordEncoder.matches(loginRequestDto.getPassword(), usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales incorrectas");
        }

        if (Boolean.FALSE.equals(usuario.getEmailVerificado()) && usuario.getRol() != Rol.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Debés verificar tu correo electrónico antes de ingresar. Te enviamos un código al registrarte.");
        }

        String jwtToken = jwtService.generateToken(usuario);

        boolean secureCookie = true;
        String sameSiteVal = "Strict";
        boolean isDev = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");
        if (isDev || "dummy-client-id".equals(clientId)) {
            secureCookie = false;
            sameSiteVal = "Lax";
        }

        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", jwtToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .sameSite(sameSiteVal)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new UserResponseDto(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.getTelefono()));
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody GoogleLoginDto googleLoginDto, HttpServletResponse response) {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken(googleLoginDto.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
        }

        Usuario usuario = googleAuthService.getOrCreateUsuario(payload);
        String jwtToken = jwtService.generateToken(usuario);

        boolean secureCookie = true;
        String sameSiteVal = "Strict";

        // Check if dev profile is active
        boolean isDev = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");

        // Disable secure cookie and set SameSite to Lax in development/simulation mode
        if (isDev || "dummy-client-id".equals(clientId) || (googleLoginDto.getIdToken() != null && googleLoginDto.getIdToken().startsWith("mock-"))) {
            secureCookie = false;
            sameSiteVal = "Lax";
        }

        // Configurar cookie de sesión HttpOnly
        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", jwtToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .sameSite(sameSiteVal)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new UserResponseDto(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.getTelefono()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(new UserResponseDto(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.getTelefono()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        boolean secureCookie = true;
        String sameSiteVal = "Strict";

        boolean isDev = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");
        if (isDev || "dummy-client-id".equals(clientId)) {
            secureCookie = false;
            sameSiteVal = "Lax";
        }

        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite(sameSiteVal)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok("Sesión cerrada");
    }
}
