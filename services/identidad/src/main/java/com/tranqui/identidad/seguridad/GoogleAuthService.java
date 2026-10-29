package com.tranqui.identidad.seguridad;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

@Service
public class GoogleAuthService {

    private final Usuarios usuarios;
    private final OutboxPublisher outboxPublisher;
    private final String clientId;
    private final boolean googleSimulado;
    private GoogleIdTokenVerifier verifier;

    public GoogleAuthService(
            Usuarios usuarios,
            OutboxPublisher outboxPublisher,
            @Value("${google.client-id:dummy-client-id}") String clientId,
            @Value("${identidad.google.simulado:${google.simulado:false}}") boolean googleSimulado) {
        this.usuarios = usuarios;
        this.outboxPublisher = outboxPublisher;
        this.clientId = clientId;
        this.googleSimulado = googleSimulado;
    }

    private synchronized GoogleIdTokenVerifier getVerifier() {
        if (this.verifier == null) {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
        return this.verifier;
    }

    public GoogleIdToken.Payload verifyGoogleToken(String tokenString) {
        if (googleSimulado && ("mock-token".equals(tokenString) || (tokenString != null && tokenString.startsWith("mock-")))) {
            GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
            String email = "paula@tranqui.com";
            String name = "Lic. María Paula Rossi";
            if (tokenString.startsWith("mock-") && tokenString.contains("@")) {
                email = tokenString.substring(5).trim().toLowerCase();
                Optional<Usuario> existing = usuarios.buscarPorEmail(email);
                if (existing.isPresent()) {
                    name = existing.get().nombre();
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
            // Token inválido o malformado
        }
        return null;
    }

    @Transactional
    public Usuario getOrCreateUsuario(GoogleIdToken.Payload payload) {
        String email = payload.getEmail() != null ? payload.getEmail().trim().toLowerCase() : null;
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El token de Google no contiene email.");
        }

        Optional<Usuario> existente = usuarios.buscarPorEmail(email);
        if (existente.isPresent()) {
            return existente.get();
        }

        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        Usuario nuevo = Usuario.builder()
                .nombre(name)
                .email(email)
                .rol("PACIENTE")
                .emailVerificado(true)
                .perfilCompleto(false)
                .terminosAceptadosEn(null)
                .fechaRegistro(LocalDateTime.now())
                .notificacionesEmailHabilitadas(true)
                .recibirNovedades(true)
                .build();

        Long id = usuarios.insertarNuevo(nuevo);
        Usuario guardado = usuarios.buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("Usuario recién creado no encontrado: " + id));

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

        return guardado;
    }
}
