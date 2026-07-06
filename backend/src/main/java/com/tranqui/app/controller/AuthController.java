package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Usuario;
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

    @org.springframework.beans.factory.annotation.Value("${google.client-id:dummy-client-id}")
    private String clientId;

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

        return ResponseEntity.ok(new UserResponseDto(usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(new UserResponseDto(usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
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
