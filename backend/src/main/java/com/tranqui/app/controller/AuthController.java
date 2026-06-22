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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody GoogleLoginDto googleLoginDto, HttpServletResponse response) {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken(googleLoginDto.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
        }

        Usuario usuario = googleAuthService.getOrCreateUsuario(payload);
        String jwtToken = jwtService.generateToken(usuario);

        // Configurar cookie de sesión HttpOnly
        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", jwtToken)
                .httpOnly(true)
                .secure(true) // true triggers Secure cookie (must be HTTPS or localhost bypass)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new UserResponseDto(usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }
}
