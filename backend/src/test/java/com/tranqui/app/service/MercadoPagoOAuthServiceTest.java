package com.tranqui.app.service;

import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoOAuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EncryptionUtil encryptionUtil;

    private MercadoPagoOAuthService buildService() {
        MercadoPagoOAuthService service = new MercadoPagoOAuthService();
        ReflectionTestUtils.setField(service, "clientId", "test-client-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(service, "appPublicUrl", "https://backend.example.com");
        ReflectionTestUtils.setField(service, "stateSigningKey", "masterdecryptionkey32charspart12");
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(service, "encryptionUtil", encryptionUtil);
        return service;
    }

    /** Minimal fake so we can stub java.net.http.HttpClient#send without hitting the network. */
    private static class FakeHttpClient extends HttpClient {
        final int statusCode;
        final String body;
        HttpRequest lastRequest;

        FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.lastRequest = request;
            return (HttpResponse<T>) new FakeHttpResponse(statusCode, body);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private static class FakeHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        FakeHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://api.mercadopago.com/oauth/token"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private void stubHttp(MercadoPagoOAuthService service, int statusCode, String body) {
        ReflectionTestUtils.setField(service, "httpClient", new FakeHttpClient(statusCode, body));
    }

    // ── buildAuthorizationUrl / verificarState ──────────────────────

    @Test
    void buildAuthorizationUrl_shouldIncludeClientIdRedirectUriAndSignedState() {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(42L).build();

        String url = service.buildAuthorizationUrl(medico);

        assertTrue(url.startsWith("https://auth.mercadopago.com.ar/authorization"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fbackend.example.com%2Fapi%2Fmedicos%2Fmercadopago%2Fcallback"));
        assertTrue(url.contains("state=42."));
    }

    @Test
    void verificarState_shouldRoundTripForAValidlySignedState() {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(7L).build();

        String url = service.buildAuthorizationUrl(medico);
        String state = url.substring(url.indexOf("state=") + "state=".length());
        Long medicoId = service.verificarState(state);

        assertEquals(7L, medicoId);
    }

    @Test
    void verificarState_shouldRejectTamperedPayload() {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(7L).build();
        String url = service.buildAuthorizationUrl(medico);
        String state = url.substring(url.indexOf("state=") + "state=".length());

        String tampered = state.replaceFirst("^7", "8");

        assertNull(service.verificarState(tampered));
    }

    @Test
    void verificarState_shouldRejectMalformedOrMissingState() {
        MercadoPagoOAuthService service = buildService();

        assertNull(service.verificarState(null));
        assertNull(service.verificarState("no-dot-here"));
        assertNull(service.verificarState(""));
    }

    @Test
    void verificarState_shouldRejectNonNumericPayloadEvenIfSignatureMatches() {
        MercadoPagoOAuthService service = buildService();
        // Sign a non-numeric payload the same way the service would (mirrors signState()).
        String payload = "not-a-number";
        String signed = payload + "." + ReflectionTestUtils.invokeMethod(service, "hmac", payload);

        assertNull(service.verificarState(signed));
    }

    // ── obtenerAccessTokenValido ─────────────────────────────────────

    @Test
    void obtenerAccessTokenValido_shouldReturnNullWhenNotConnected() throws Exception {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(1L).build();

        assertNull(service.obtenerAccessTokenValido(medico));
    }

    @Test
    void obtenerAccessTokenValido_shouldReturnDecryptedTokenWhenNotExpiring() throws Exception {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(1L)
                .mpAccessTokenEncrypted("enc-token")
                .mpTokenExpiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(encryptionUtil.decrypt("enc-token")).thenReturn("plain-token");

        String token = service.obtenerAccessTokenValido(medico);

        assertEquals("plain-token", token);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerAccessTokenValido_shouldRefreshWhenExpiringSoon() throws Exception {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 200, "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":21600,\"user_id\":\"999\"}");

        Usuario medico = Usuario.builder().id(1L)
                .mpAccessTokenEncrypted("enc-old-access")
                .mpRefreshTokenEncrypted("enc-old-refresh")
                .mpTokenExpiresAt(LocalDateTime.now().plusSeconds(60)) // inside the 300s safety margin
                .build();
        when(encryptionUtil.decrypt("enc-old-refresh")).thenReturn("old-refresh");
        when(encryptionUtil.encrypt("new-access")).thenReturn("enc-new-access");
        when(encryptionUtil.encrypt("new-refresh")).thenReturn("enc-new-refresh");
        when(encryptionUtil.decrypt("enc-new-access")).thenReturn("new-access");

        String token = service.obtenerAccessTokenValido(medico);

        assertEquals("new-access", token);
        assertEquals("999", medico.getMpUserId());
        verify(usuarioRepository).save(medico);
    }

    @Test
    void obtenerAccessTokenValido_shouldKeepOldTokenWhenRefreshFails() throws Exception {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 400, "{\"message\":\"invalid_grant\"}");

        Usuario medico = Usuario.builder().id(1L)
                .mpAccessTokenEncrypted("enc-old-access")
                .mpRefreshTokenEncrypted("enc-old-refresh")
                .mpTokenExpiresAt(LocalDateTime.now().plusSeconds(60))
                .build();
        when(encryptionUtil.decrypt("enc-old-refresh")).thenReturn("old-refresh");
        when(encryptionUtil.decrypt("enc-old-access")).thenReturn("old-access");

        String token = service.obtenerAccessTokenValido(medico);

        assertEquals("old-access", token);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerAccessTokenValido_shouldNotRefreshWhenNoRefreshTokenStored() throws Exception {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(1L)
                .mpAccessTokenEncrypted("enc-access")
                .mpTokenExpiresAt(LocalDateTime.now().plusSeconds(60))
                .build();
        when(encryptionUtil.decrypt("enc-access")).thenReturn("access");

        String token = service.obtenerAccessTokenValido(medico);

        assertEquals("access", token);
    }

    // ── procesarCallback ─────────────────────────────────────────────

    @Test
    void procesarCallback_shouldThrowWhenMedicoNotFound() {
        MercadoPagoOAuthService service = buildService();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.procesarCallback(1L, "some-code"));
    }

    @Test
    void procesarCallback_shouldExchangeCodeAndPersistTokens() throws Exception {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 200, "{\"access_token\":\"tok-abc\",\"refresh_token\":\"ref-abc\",\"expires_in\":21600,\"user_id\":\"555\"}");

        Usuario medico = Usuario.builder().id(1L).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(encryptionUtil.encrypt("tok-abc")).thenReturn("enc-tok-abc");
        when(encryptionUtil.encrypt("ref-abc")).thenReturn("enc-ref-abc");
        when(usuarioRepository.save(medico)).thenReturn(medico);

        Usuario result = service.procesarCallback(1L, "auth-code");

        assertEquals("enc-tok-abc", result.getMpAccessTokenEncrypted());
        assertEquals("enc-ref-abc", result.getMpRefreshTokenEncrypted());
        assertEquals("555", result.getMpUserId());
        assertNotNull(result.getMpTokenExpiresAt());
    }

    @Test
    void procesarCallback_shouldThrowWhenMercadoPagoRejectsTheExchange() {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 400, "{\"message\":\"code already used\"}");

        Usuario medico = Usuario.builder().id(1L).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.procesarCallback(1L, "used-code"));
        assertTrue(ex.getMessage().contains("code already used"));
    }

    @Test
    void procesarCallback_shouldFallBackToRawBodyWhenErrorHasNoMessageField() {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 500, "{}");

        Usuario medico = Usuario.builder().id(1L).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));

        assertThrows(IllegalStateException.class, () -> service.procesarCallback(1L, "code"));
    }

    @Test
    void procesarCallback_shouldToleraTokenResponseWithoutRefreshTokenOrUserId() throws Exception {
        MercadoPagoOAuthService service = buildService();
        stubHttp(service, 200, "{\"access_token\":\"tok-only\"}");

        Usuario medico = Usuario.builder().id(1L).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(encryptionUtil.encrypt("tok-only")).thenReturn("enc-tok-only");
        when(usuarioRepository.save(medico)).thenReturn(medico);

        Usuario result = service.procesarCallback(1L, "code");

        assertEquals("enc-tok-only", result.getMpAccessTokenEncrypted());
        assertNull(result.getMpRefreshTokenEncrypted());
        assertNull(result.getMpUserId());
        assertNull(result.getMpTokenExpiresAt());
    }

    // ── desvincular ──────────────────────────────────────────────────

    @Test
    void desvincular_shouldClearAllMercadoPagoFields() {
        MercadoPagoOAuthService service = buildService();
        Usuario medico = Usuario.builder().id(1L)
                .mpAccessTokenEncrypted("a").mpRefreshTokenEncrypted("r")
                .mpTokenExpiresAt(LocalDateTime.now()).mpUserId("123")
                .build();
        when(usuarioRepository.save(medico)).thenReturn(medico);

        service.desvincular(medico);

        assertNull(medico.getMpAccessTokenEncrypted());
        assertNull(medico.getMpRefreshTokenEncrypted());
        assertNull(medico.getMpTokenExpiresAt());
        assertNull(medico.getMpUserId());
        verify(usuarioRepository).save(medico);
    }
}
