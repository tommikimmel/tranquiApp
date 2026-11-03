package com.tranqui.calendario.google;

import com.tranqui.calendario.agenda.Canales;
import com.tranqui.calendario.datos.CuentasGoogle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OAuth de Google (conexión y renovación del access token) y los canales de notificación del calendario. */
class TokensYCanalesTest {

    private final Cifrado cifrado = new Cifrado("clave-de-prueba-de-32-caracteres");
    private final CuentasGoogle cuentas = mock(CuentasGoogle.class);
    private final HttpClient http = mock(HttpClient.class);
    private final GoogleCalendarApi google = mock(GoogleCalendarApi.class);
    private TokensGoogle tokens;

    @BeforeEach
    void setUp() {
        tokens = new TokensGoogle(cuentas, cifrado, "cliente-id", "secreto", "https://tranqui.test");
        ReflectionTestUtils.setField(tokens, "http", http);
    }

    @SuppressWarnings("unchecked")
    private void google(int status, String cuerpo) throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(cuerpo);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);
    }

    private CuentasGoogle.Cuenta cuenta(String access, LocalDateTime vence, String canal) {
        return new CuentasGoogle.Cuenta(7L, access != null ? cifrado.cifrar(access) : null, cifrado.cifrar("refresh-1"), vence,
                null, null, canal, canal != null ? "recurso" : null, null, canal != null);
    }

    @Test
    void urlDeConexion_llevaElEstadoFirmado() {
        String url = tokens.urlDeConexion(7L);
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?client_id=cliente-id")
                .contains("redirect_uri=https%3A%2F%2Ftranqui.test%2Fapi%2Fmedicos%2Fgoogle-calendar%2Fcallback", "access_type=offline");
        String estado = java.net.URLDecoder.decode(url.substring(url.indexOf("&state=") + 7), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(cifrado.verificar(estado)).isEqualTo(7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void conectar_canjeaElCodigoYGuardaLosTokensCifrados() throws Exception {
        google(200, "{\"access_token\":\"acc\",\"refresh_token\":\"ref\",\"expires_in\":3600}");
        tokens.conectar(7L, "codigo/1");
        ArgumentCaptor<HttpRequest> pedido = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(pedido.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(pedido.getValue().uri().toString()).isEqualTo("https://oauth2.googleapis.com/token");
        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(cuentas).guardarTokens(eq(7L), access.capture(), refresh.capture(), any());
        assertThat(cifrado.descifrar(access.getValue())).isEqualTo("acc");
        assertThat(cifrado.descifrar(refresh.getValue())).isEqualTo("ref");
    }

    @Test
    void conectar_googleRechaza_fallaSinElCuerpo() throws Exception {
        google(400, "{\"error\":\"invalid_grant\",\"email\":\"lucia@mail.com\"}");
        assertThatThrownBy(() -> tokens.conectar(7L, "x")).hasMessage("Google respondió 400 al pedir el token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessToken_vigenteRenovadoOImposible() throws Exception {
        assertThat(tokens.accessToken(null)).isNull();
        assertThat(tokens.accessToken(cuenta("acc", LocalDateTime.now().plusHours(1), null))).isEqualTo("acc");
        verify(http, never()).send(any(), any());

        google(200, "{\"access_token\":\"nuevo\",\"expires_in\":3600}");
        assertThat(tokens.accessToken(cuenta("acc", LocalDateTime.now().plusMinutes(1), null))).isEqualTo("nuevo");
        verify(cuentas).guardarTokens(eq(7L), anyString(), isNull(), any());

        google(401, "{}");
        assertThat(tokens.accessToken(cuenta(null, null, null))).isNull();

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            assertThat(tokens.accessToken(cuenta(null, null, null))).isNull();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    // ---------------------------------------------------------------- canales

    private Canales canales(boolean habilitados) {
        TokensGoogle mockTokens = mock(TokensGoogle.class);
        when(mockTokens.accessToken(any())).thenAnswer(inv -> inv.getArgument(0) != null ? "acc" : null);
        return new Canales(cuentas, mockTokens, google, cifrado, habilitados, habilitados, "https://tranqui.test");
    }

    @Test
    void registrar_guardaElCanalConSuVencimiento() throws Exception {
        when(cuentas.buscar(7L)).thenReturn(java.util.Optional.of(cuenta("acc", null, null)));
        when(google.registrarCanal(eq("acc"), anyString(), eq("https://tranqui.test/api/medicos/google-calendar/webhook"), anyString(), anyLong()))
                .thenReturn(new GoogleCalendarApi.Canal("canal-1", "recurso-1", System.currentTimeMillis() + 86_400_000L))
                .thenReturn(new GoogleCalendarApi.Canal("canal-2", "recurso-2", null));
        Canales c = canales(true);
        assertThat(c.habilitados()).isTrue();
        c.registrar(7L);
        c.registrar(7L);
        verify(cuentas).guardarCanal(eq(7L), eq("canal-1"), eq("recurso-1"), any(), eq(true));
        verify(cuentas).guardarCanal(eq(7L), eq("canal-2"), eq("recurso-2"), any(), eq(true));
    }

    @Test
    void registrar_deshabilitadoSinTokenOConError_noRompe() throws Exception {
        canales(false).registrar(7L);
        verify(cuentas, never()).buscar(any());

        when(cuentas.buscar(8L)).thenReturn(java.util.Optional.empty());
        canales(true).registrar(8L);

        when(cuentas.buscar(7L)).thenReturn(java.util.Optional.of(cuenta("acc", null, null)));
        when(google.registrarCanal(anyString(), anyString(), anyString(), anyString(), anyLong())).thenThrow(new java.io.IOException("403"));
        canales(true).registrar(7L);
        verify(cuentas, never()).guardarCanal(any(), any(), any(), any(), eq(true));
    }

    @Test
    void detenerYRenovar() throws Exception {
        when(cuentas.buscar(7L)).thenReturn(java.util.Optional.of(cuenta("acc", null, "canal-1")));
        doThrow(new java.io.IOException("ya vencido")).when(google).detenerCanal("acc", "canal-1", "recurso");
        Canales c = canales(true);
        c.detener(7L);
        verify(cuentas).guardarCanal(7L, null, null, null, false);

        when(cuentas.buscar(9L)).thenReturn(java.util.Optional.of(cuenta("acc", null, null)));
        c.detener(9L);
        verify(cuentas, never()).guardarCanal(eq(9L), any(), any(), any(), eq(false));

        when(google.registrarCanal(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new GoogleCalendarApi.Canal("canal-2", "recurso-2", null));
        c.renovar(7L);
        verify(cuentas).guardarCanal(eq(7L), eq("canal-2"), eq("recurso-2"), any(), eq(true));
        assertThat(c.profesionalDelToken(cifrado.firmar(7L))).isEqualTo(7L);
    }
}
