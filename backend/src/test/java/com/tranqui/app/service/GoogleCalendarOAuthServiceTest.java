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
class GoogleCalendarOAuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EncryptionUtil encryptionUtil;
    @Mock private com.tranqui.app.repository.GoogleCalendarEventoExternoRepository eventoExternoRepository;

    private GoogleCalendarOAuthService buildService() {
        GoogleCalendarOAuthService service = new GoogleCalendarOAuthService();
        ReflectionTestUtils.setField(service, "clientId", "google-client-id");
        ReflectionTestUtils.setField(service, "clientSecret", "google-client-secret");
        ReflectionTestUtils.setField(service, "appPublicUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(service, "stateSigningKey", "masterdecryptionkey32charspart12");
        ReflectionTestUtils.setField(service, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(service, "encryptionUtil", encryptionUtil);
        ReflectionTestUtils.setField(service, "eventoExternoRepository", eventoExternoRepository);
        return service;
    }

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
        @Override public URI uri() { return URI.create("https://oauth2.googleapis.com/token"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private void stubHttp(GoogleCalendarOAuthService service, int statusCode, String body) {
        ReflectionTestUtils.setField(service, "httpClient", new FakeHttpClient(statusCode, body));
    }

    @Test
    void testBuildAuthorizationUrl() {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);

        String url = service.buildAuthorizationUrl(medico);
        assertNotNull(url);
        assertTrue(url.contains("client_id=google-client-id"));
        assertTrue(url.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.events"));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("state=123."));
    }

    @Test
    void testVerificarStateValid() {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);

        String state = ReflectionTestUtils.invokeMethod(service, "signState", 123L);
        Long result = service.verificarState(state);
        assertEquals(123L, result);
    }

    @Test
    void testVerificarStateInvalid() {
        GoogleCalendarOAuthService service = buildService();
        assertNull(service.verificarState(null));
        assertNull(service.verificarState("invalidstate"));
        assertNull(service.verificarState("123.invalidhmac"));
    }

    @Test
    void testProcesarCallback() throws Exception {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);

        when(usuarioRepository.findById(123L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(encryptionUtil.encrypt("test_access_token")).thenReturn("enc_access");
        when(encryptionUtil.encrypt("test_refresh_token")).thenReturn("enc_refresh");

        stubHttp(service, 200, "{\n" +
                "  \"access_token\": \"test_access_token\",\n" +
                "  \"refresh_token\": \"test_refresh_token\",\n" +
                "  \"expires_in\": 3600\n" +
                "}");

        Usuario updated = service.procesarCallback(123L, "mock_code");
        assertNotNull(updated);
        assertTrue(updated.getGoogleCalendarConnected());
        assertEquals("enc_access", updated.getGoogleAccessTokenEncrypted());
        assertEquals("enc_refresh", updated.getGoogleRefreshTokenEncrypted());
        assertNotNull(updated.getGoogleTokenExpiresAt());
    }

    @Test
    void testObtenerAccessTokenStillValid() throws Exception {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleAccessTokenEncrypted("enc_access");
        medico.setGoogleRefreshTokenEncrypted("enc_refresh");
        medico.setGoogleTokenExpiresAt(LocalDateTime.now().plusHours(1));

        when(encryptionUtil.decrypt("enc_access")).thenReturn("valid_access_token");

        String token = service.obtenerAccessToken(medico);
        assertEquals("valid_access_token", token);
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void testObtenerAccessTokenExpiredAndRefreshed() throws Exception {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleAccessTokenEncrypted("enc_access");
        medico.setGoogleRefreshTokenEncrypted("enc_refresh");
        medico.setGoogleTokenExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(encryptionUtil.decrypt("enc_refresh")).thenReturn("valid_refresh_token");
        when(encryptionUtil.encrypt("new_access_token")).thenReturn("new_enc_access");
        when(encryptionUtil.decrypt("new_enc_access")).thenReturn("new_access_token");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        stubHttp(service, 200, "{\n" +
                "  \"access_token\": \"new_access_token\",\n" +
                "  \"expires_in\": 3600\n" +
                "}");

        String token = service.obtenerAccessToken(medico);
        assertEquals("new_access_token", token);
        assertEquals("new_enc_access", medico.getGoogleAccessTokenEncrypted());
        assertNotNull(medico.getGoogleTokenExpiresAt());
    }

    @Test
    void testObtenerAccessTokenNotLinked() {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);

        assertNull(service.obtenerAccessToken(medico));
    }

    @Test
    void testDesvincular() {
        GoogleCalendarOAuthService service = buildService();
        Usuario medico = new Usuario();
        medico.setId(123L);
        medico.setGoogleCalendarConnected(true);
        medico.setGoogleAccessTokenEncrypted("enc_access");
        medico.setGoogleRefreshTokenEncrypted("enc_refresh");
        medico.setGoogleTokenExpiresAt(LocalDateTime.now());

        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        service.desvincular(medico);

        assertFalse(medico.getGoogleCalendarConnected());
        assertNull(medico.getGoogleAccessTokenEncrypted());
        assertNull(medico.getGoogleRefreshTokenEncrypted());
        assertNull(medico.getGoogleTokenExpiresAt());
    }
}
