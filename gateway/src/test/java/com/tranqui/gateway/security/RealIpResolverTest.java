package com.tranqui.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealIpResolverTest {

    private RealIpResolver realIpResolver;

    @BeforeEach
    void setUp() {
        // Usa los proxies confiables por defecto: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.1/32
        realIpResolver = new RealIpResolver();
    }

    @Test
    void testRemotoConfiableConXff_retornaIpCliente() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .header("X-Forwarded-For", "203.0.113.195")
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    void testRemotoConfiableConMultiplesProxiesEnXff_retornaIpMasADerechaNoConfiable() {
        // Cadena: Cliente real (203.0.113.195) -> Proxy intermedio confiable (10.0.0.1) -> Proxy frontal (172.18.0.2)
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .header("X-Forwarded-For", "203.0.113.195, 10.0.0.1")
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    void testRemotoNoConfiableConXffFalso_ignoraXffYRetornaIpRemota() {
        // Cliente remoto público (198.51.100.20) intenta suplantar IP enviando XFF falso
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .remoteAddress(new InetSocketAddress("198.51.100.20", 54321))
                        .header("X-Forwarded-For", "1.2.3.4")
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("198.51.100.20");
    }

    @Test
    void testRemotoConfiableSinXff_retornaIpRemota() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("172.18.0.2", 54321))
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("172.18.0.2");
    }

    @Test
    void testRemotoNoConfiableSinXff_retornaIpRemota() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("198.51.100.20", 54321))
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("198.51.100.20");
    }

    @Test
    void testRemotoConfiableConXffSoloConProxiesConfiables_retornaIpRemota() {
        // Si todas las IPs en la cadena XFF pertenecen a proxies confiables
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 54321))
                        .header("X-Forwarded-For", "10.0.0.1, 192.168.1.100")
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void testRemotoConfiableConPuertoEnXff_limpiaPuerto() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/turnos")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 54321))
                        .header("X-Forwarded-For", "203.0.113.195:4321")
                        .build()
        );

        String ip = realIpResolver.extractClientIp(exchange);
        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    void testCidrMatcherRangosPrivados() {
        RealIpResolver.CidrMatcher net10 = new RealIpResolver.CidrMatcher("10.0.0.0/8");
        assertThat(net10.matches((String) null)).isFalse();
        assertThat(net10.matches((java.net.InetAddress) null)).isFalse();
        assertThat(net10.matches("10.0.0.1")).isTrue();
        assertThat(net10.matches("10.255.255.255")).isTrue();
        assertThat(net10.matches("11.0.0.1")).isFalse();

        RealIpResolver.CidrMatcher net172 = new RealIpResolver.CidrMatcher("172.16.0.0/12");
        assertThat(net172.matches("172.16.0.1")).isTrue();
        assertThat(net172.matches("172.18.0.2")).isTrue();
        assertThat(net172.matches("172.31.255.255")).isTrue();
        assertThat(net172.matches("172.32.0.1")).isFalse();

        RealIpResolver.CidrMatcher net192 = new RealIpResolver.CidrMatcher("192.168.0.0/16");
        assertThat(net192.matches("192.168.1.1")).isTrue();
        assertThat(net192.matches("192.169.1.1")).isFalse();

        RealIpResolver.CidrMatcher net127 = new RealIpResolver.CidrMatcher("127.0.0.1/32");
        assertThat(net127.matches("127.0.0.1")).isTrue();
        assertThat(net127.matches("127.0.0.2")).isFalse();
    }

    @Test
    void configuracion_vaciaUsaLosRangosPorDefectoEIgnoraLosInvalidos() {
        RealIpResolver porDefecto = new RealIpResolver(List.<String>of());
        assertThat(porDefecto.isTrusted("10.1.2.3")).isTrue();
        assertThat(new RealIpResolver((List<String>) null).isTrusted("192.168.1.1")).isTrue();

        RealIpResolver propia = new RealIpResolver(java.util.Arrays.asList("203.0.113.0/24", "no-es-cidr/99x", " ", null, "2001:db8::/32"));
        assertThat(propia.isTrusted("203.0.113.7")).isTrue();
        assertThat(propia.isTrusted("10.1.2.3")).isFalse();
        assertThat(propia.isTrusted("2001:db8::1")).isTrue();
        assertThat(propia.isTrusted((String) null)).isFalse();
        assertThat(propia.isTrusted("host-que-no-existe.invalid")).isFalse();
        assertThat(new RealIpResolver(null, true).isTrusted("127.0.0.1")).isTrue();
    }

    @Test
    void sinRequestOSinDireccion_usaLocalhost() {
        assertThat(realIpResolver.extractClientIp(null)).isEqualTo("127.0.0.1");
        ServerWebExchange sinRemota = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        assertThat(realIpResolver.extractClientIp(sinRemota)).isEqualTo("127.0.0.1");
        ServerWebExchange sinResolver = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(InetSocketAddress.createUnresolved("proxy.local", 80)).build());
        assertThat(realIpResolver.extractClientIp(sinResolver)).isEqualTo("proxy.local");
    }

    @Test
    void xffVacio_usaLaIpRemota() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 1)).header("X-Forwarded-For", " , ").build());
        assertThat(realIpResolver.extractClientIp(exchange)).isEqualTo("10.0.0.2");
    }

    @Test
    void sanitizeIp_ipv6ConPuertoYNull() {
        assertThat(RealIpResolver.sanitizeIp("[2001:db8::1]:8080")).isEqualTo("2001:db8::1");
        assertThat(RealIpResolver.sanitizeIp("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(RealIpResolver.sanitizeIp(null)).isEqualTo("127.0.0.1");
    }

    @Test
    void cidrMatcher_bordes() {
        RealIpResolver.CidrMatcher host = new RealIpResolver.CidrMatcher("10.0.0.1");
        assertThat(host.matches("10.0.0.1")).isTrue();
        assertThat(host.matches("10.0.0.2")).isFalse();
        assertThat(host.matches("2001:db8::1")).isFalse();
        assertThat(host.matches((java.net.InetAddress) null)).isFalse();
        assertThat(host.matches(" ")).isFalse();
        assertThat(host.matches("no.existe.invalid")).isFalse();
        assertThat(new RealIpResolver.CidrMatcher("172.16.0.0/12").matches("172.31.255.255")).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RealIpResolver.CidrMatcher("10.0.0.0/abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
