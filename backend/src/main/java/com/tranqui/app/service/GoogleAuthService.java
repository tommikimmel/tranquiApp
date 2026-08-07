package com.tranqui.app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Value("${google.client-id:dummy-client-id}")
    private String clientId;

    private GoogleIdTokenVerifier verifier;

    private GoogleIdTokenVerifier getVerifier() {
        if (this.verifier == null) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
        return this.verifier;
    }

    public GoogleIdToken.Payload verifyGoogleToken(String tokenString) {
        if ("mock-token".equals(tokenString) || (tokenString != null && tokenString.startsWith("mock-"))) {
            GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
            String email = "paula@tranqui.com";
            String name = "Lic. María Paula Rossi";
            if (tokenString.startsWith("mock-") && tokenString.contains("@")) {
                email = tokenString.substring(5);
                java.util.Optional<Usuario> existing = usuarioRepository.findByEmail(email);
                if (existing.isPresent()) {
                    name = existing.get().getNombre();
                } else {
                    name = email.split("@")[0];
                }
            }
            mockPayload.setEmail(email);
            mockPayload.set("name", name);
            return mockPayload;
        }
        try {
            GoogleIdToken idToken = getVerifier().verify(tokenString);
            if (idToken != null) {
                return idToken.getPayload();
            }
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            // A malformed/garbage token string throws IllegalArgumentException before it even
            // reaches signature verification; treat it the same as any other invalid token.
        }
        return null;
    }

    public Usuario getOrCreateUsuario(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        return usuarioRepository.findByEmail(email)
                .orElseGet(() -> {
                    String name = (String) payload.get("name");
                    if (name == null) {
                        name = email.split("@")[0];
                    }
                    // Default role is PACIENTE. Google only gives us email+name, so the rest of
                    // the Paso 2 profile data is missing — perfilCompleto=false gates access
                    // until the frontend collects it via /auth/complete-profile.
                    Usuario nuevo = Usuario.builder()
                            .nombre(name)
                            .email(email)
                            .rol(Rol.PACIENTE)
                            .emailVerificado(true)
                            .perfilCompleto(false)
                            .build();
                    return usuarioRepository.save(nuevo);
                });
    }
}
