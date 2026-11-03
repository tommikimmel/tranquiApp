package com.tranqui.pagos.mp;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.CuentasMp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * La cuenta de Mercado Pago de cada profesional: OAuth, renovación de tokens, la conexión simulada de local
 * y la desvinculación. Lo mismo que MercadoPagoOAuthService del monolito; vincular y desvincular publican
 * {@code pagos.cuenta-mp.*} (el monolito lo usa para el buscador y la reserva).
 */
@Service
public class Vinculacion {

    private static final Logger log = LoggerFactory.getLogger(Vinculacion.class);
    private static final String AUTH_HOST = "https://auth.mercadopago.com.ar";
    // Se renueva un poco antes de que venza, para no usar un token que muere a mitad de un pedido.
    private static final long MARGEN_SEGUNDOS = 300;

    private final CuentasMp cuentas;
    private final Cifrado cifrado;
    private final MercadoPagoApi mercadoPago;
    private final OutboxPublisher outbox;
    private final String clientId;
    private final String clientSecret;
    private final String publicUrl;

    public Vinculacion(CuentasMp cuentas, Cifrado cifrado, MercadoPagoApi mercadoPago, OutboxPublisher outbox,
                       @Value("${mercadopago.client-id:}") String clientId,
                       @Value("${mercadopago.client-secret:}") String clientSecret,
                       @Value("${app.public-url}") String publicUrl) {
        this.cuentas = cuentas;
        this.cifrado = cifrado;
        this.mercadoPago = mercadoPago;
        this.outbox = outbox;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.publicUrl = publicUrl;
    }

    private String redirectUri() {
        return publicUrl + "/api/medicos/mercadopago/callback";
    }

    public String urlAutorizacion(Long profesionalId) {
        return AUTH_HOST + "/authorization"
                + "?client_id=" + codificar(clientId)
                + "&response_type=code"
                + "&platform_id=mp"
                + "&redirect_uri=" + codificar(redirectUri())
                + "&state=" + codificar(cifrado.firmar(profesionalId));
    }

    /** El profesional del {@code state} si la firma es válida; si no, {@code null}. */
    public Long verificarState(String state) {
        return cifrado.verificar(state);
    }

    @Transactional
    public void procesarCallback(Long profesionalId, String code) throws Exception {
        MercadoPagoApi.TokensOAuth tokens = mercadoPago.pedirTokens("grant_type=authorization_code"
                + "&client_id=" + codificar(clientId)
                + "&client_secret=" + codificar(clientSecret)
                + "&code=" + codificar(code)
                + "&redirect_uri=" + codificar(redirectUri()));
        guardar(profesionalId, tokens);
        publicarVinculada(profesionalId);
    }

    /**
     * Local: vincula sin hablar con Mercado Pago, con tokens de prueba (los cobros se simulan). El controller
     * la corta con {@code mercadopago.enabled=true}.
     */
    @Transactional
    public void simularConexion(Long profesionalId) {
        cuentas.guardar(new CuentasMp.Cuenta(profesionalId,
                cifrado.cifrar("TEST-dev-mock-access-token-" + profesionalId),
                cifrado.cifrar("TEST-dev-mock-refresh-token-" + profesionalId),
                LocalDateTime.now().plusMonths(6), "999999999"));
        publicarVinculada(profesionalId);
    }

    @Transactional
    public void desvincular(Long profesionalId) {
        cuentas.borrar(profesionalId);
        outbox.publicar(Topicos.PAGOS_CUENTA_MP_DESVINCULADA_V1, "profesional", String.valueOf(profesionalId),
                version(), new CuentaMpDesvinculadaData(profesionalId));
    }

    /**
     * El access token vigente (descifrado) del profesional, renovándolo antes si está por vencer. Vacío si
     * no vinculó su cuenta.
     */
    public Optional<String> accessTokenValido(Long profesionalId) throws Exception {
        Optional<CuentasMp.Cuenta> cuenta = cuentas.buscar(profesionalId);
        if (cuenta.isEmpty()) {
            return Optional.empty();
        }
        CuentasMp.Cuenta c = cuenta.get();
        boolean porVencer = c.tokenVenceEn() != null
                && LocalDateTime.now().plusSeconds(MARGEN_SEGUNDOS).isAfter(c.tokenVenceEn());
        if (porVencer && c.refreshTokenCifrado() != null) {
            try {
                MercadoPagoApi.TokensOAuth tokens = mercadoPago.pedirTokens("grant_type=refresh_token"
                        + "&client_id=" + codificar(clientId)
                        + "&client_secret=" + codificar(clientSecret)
                        + "&refresh_token=" + codificar(cifrado.descifrar(c.refreshTokenCifrado())));
                guardar(profesionalId, tokens);
                return Optional.of(tokens.accessToken());
            } catch (Exception e) {
                // Se usa el que había: el pedido a Mercado Pago va a mostrar el error real.
                log.error("No se pudo renovar el token de Mercado Pago del profesional {}", profesionalId, e);
            }
        }
        return Optional.of(cifrado.descifrar(c.accessTokenCifrado()));
    }

    /** Los tokens de la conexión simulada o de prueba: con ellos no se le pide nada a Mercado Pago. */
    public static boolean esDePrueba(String accessToken) {
        return accessToken.startsWith("dummy") || accessToken.startsWith("TEST-dev-mock") || accessToken.equals("test-token");
    }

    private void guardar(Long profesionalId, MercadoPagoApi.TokensOAuth t) {
        cuentas.guardar(new CuentasMp.Cuenta(profesionalId, cifrado.cifrar(t.accessToken()),
                t.refreshToken() != null ? cifrado.cifrar(t.refreshToken()) : null,
                t.expiraEnSegundos() > 0 ? LocalDateTime.now().plusSeconds(t.expiraEnSegundos()) : null, t.userId()));
    }

    private void publicarVinculada(Long profesionalId) {
        String mpUserId = cuentas.buscar(profesionalId).map(CuentasMp.Cuenta::mpUserId).orElse(null);
        outbox.publicar(Topicos.PAGOS_CUENTA_MP_VINCULADA_V1, "profesional", String.valueOf(profesionalId), version(),
                new CuentaMpVinculadaData(profesionalId, mpUserId));
    }

    /**
     * Versión del agregado: el momento, así un vincular/desvincular posterior siempre tiene una mayor (el
     * monolito publicaba la versión del usuario, que este servicio no tiene).
     */
    private static int version() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
