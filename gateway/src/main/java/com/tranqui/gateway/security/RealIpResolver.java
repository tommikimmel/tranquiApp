package com.tranqui.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Resuelve la dirección IP real del cliente detrás de proxies reversos (Traefik, balanceadores).
 * Utiliza la propiedad tranqui.gateway.trusted-proxies (redes privadas por defecto).
 * Si la IP remota es confiable y se incluye X-Forwarded-For, selecciona la IP más a la derecha
 * que NO sea un proxy confiable. Si la IP remota no es confiable, descarta X-Forwarded-For.
 */
@Component
public class RealIpResolver {

    private static final Logger log = LoggerFactory.getLogger(RealIpResolver.class);
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    public static final List<String> DEFAULT_TRUSTED_PROXIES = List.of(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "127.0.0.1/32"
    );

    private final List<CidrMatcher> matchers;

    public RealIpResolver(
            @Value("${tranqui.gateway.trusted-proxies:#{T(java.util.Collections).emptyList()}}") List<String> configuredProxies) {
        List<String> proxiesToUse;
        if (configuredProxies == null || configuredProxies.isEmpty()) {
            proxiesToUse = DEFAULT_TRUSTED_PROXIES;
        } else {
            proxiesToUse = configuredProxies;
        }

        List<CidrMatcher> list = new ArrayList<>();
        for (String cidr : proxiesToUse) {
            if (cidr != null && !cidr.trim().isEmpty()) {
                try {
                    list.add(new CidrMatcher(cidr.trim()));
                } catch (Exception e) {
                    log.error("CIDR inválido en configuración de trusted-proxies: {}", cidr, e);
                }
            }
        }
        this.matchers = Collections.unmodifiableList(list);
    }

    /**
     * Constructor para uso directo o en tests.
     */
    public RealIpResolver(List<String> proxies, boolean direct) {
        List<String> proxiesToUse = (proxies == null || proxies.isEmpty()) ? DEFAULT_TRUSTED_PROXIES : proxies;
        List<CidrMatcher> list = new ArrayList<>();
        for (String cidr : proxiesToUse) {
            if (cidr != null && !cidr.trim().isEmpty()) {
                list.add(new CidrMatcher(cidr.trim()));
            }
        }
        this.matchers = Collections.unmodifiableList(list);
    }

    public RealIpResolver() {
        this(DEFAULT_TRUSTED_PROXIES, true);
    }

    /**
     * Extrae la IP del cliente del ServerWebExchange respetando proxies de confianza.
     */
    public String extractClientIp(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "127.0.0.1";
        }

        InetSocketAddress remoteSocketAddress = exchange.getRequest().getRemoteAddress();
        String remoteIp = null;
        InetAddress remoteInetAddress = null;

        if (remoteSocketAddress != null) {
            remoteInetAddress = remoteSocketAddress.getAddress();
            if (remoteInetAddress != null) {
                remoteIp = remoteInetAddress.getHostAddress();
            } else if (remoteSocketAddress.getHostString() != null) {
                remoteIp = remoteSocketAddress.getHostString();
            }
        }

        if (remoteIp == null || remoteIp.trim().isEmpty()) {
            remoteIp = "127.0.0.1";
        }
        remoteIp = sanitizeIp(remoteIp);

        // Si la conexión remota no proviene de un proxy confiable, no podemos confiar en X-Forwarded-For
        if (!isTrusted(remoteInetAddress, remoteIp)) {
            return remoteIp;
        }

        // Si es un proxy confiable, evaluamos X-Forwarded-For
        List<String> xffHeaders = exchange.getRequest().getHeaders().get(X_FORWARDED_FOR);
        if (xffHeaders == null || xffHeaders.isEmpty()) {
            return remoteIp;
        }

        List<String> ipChain = new ArrayList<>();
        for (String headerVal : xffHeaders) {
            if (headerVal != null) {
                String[] tokens = headerVal.split(",");
                for (String token : tokens) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        ipChain.add(sanitizeIp(trimmed));
                    }
                }
            }
        }

        if (ipChain.isEmpty()) {
            return remoteIp;
        }

        // Buscar de derecha a izquierda la primera IP que NO sea un proxy confiable
        for (int i = ipChain.size() - 1; i >= 0; i--) {
            String candidateIp = ipChain.get(i);
            if (!isTrusted(null, candidateIp)) {
                return candidateIp;
            }
        }

        // Si todas son de proxies confiables, se recurre a la IP remota
        return remoteIp;
    }

    /**
     * Determina si una IP (o InetAddress) pertenece a los rangos CIDR de proxies de confianza.
     */
    public boolean isTrusted(InetAddress inetAddress, String ipStr) {
        try {
            InetAddress addr = inetAddress;
            if (addr == null && ipStr != null && !ipStr.isEmpty()) {
                addr = InetAddress.getByName(ipStr);
            }
            if (addr == null) {
                return false;
            }
            for (CidrMatcher matcher : matchers) {
                if (matcher.matches(addr)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isTrusted(String ipStr) {
        return isTrusted(null, ipStr);
    }

    /**
     * Limpia la IP removiendo posibles puertos (ej. "203.0.113.195:8080" o "[2001:db8::1]:8080").
     */
    public static String sanitizeIp(String ip) {
        if (ip == null) {
            return "127.0.0.1";
        }
        String clean = ip.trim();
        if (clean.startsWith("[") && clean.contains("]")) {
            clean = clean.substring(1, clean.indexOf(']'));
        } else if (clean.indexOf(':') != -1 && clean.indexOf(':') == clean.lastIndexOf(':')) {
            // IPv4 con puerto: 1.2.3.4:5678
            clean = clean.substring(0, clean.indexOf(':'));
        }
        return clean;
    }

    /**
     * Clase utilitaria para evaluar pertenencia a un rango CIDR IPv4 / IPv6.
     */
    public static class CidrMatcher {
        private final byte[] networkBytes;
        private final int prefixLength;

        public CidrMatcher(String cidr) {
            String clean = cidr.trim();
            String[] parts = clean.split("/");
            try {
                InetAddress addr = InetAddress.getByName(parts[0].trim());
                this.networkBytes = addr.getAddress();
                int maxBits = this.networkBytes.length * 8;
                if (parts.length > 1) {
                    this.prefixLength = Math.min(Math.max(0, Integer.parseInt(parts[1].trim())), maxBits);
                } else {
                    this.prefixLength = maxBits;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("CIDR inválido: " + cidr, e);
            }
        }

        public boolean matches(InetAddress address) {
            if (address == null) {
                return false;
            }
            byte[] targetBytes = address.getAddress();
            if (targetBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainderBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainderBits > 0) {
                int mask = (0xFF << (8 - remainderBits)) & 0xFF;
                if ((targetBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        }

        public boolean matches(String ipStr) {
            if (ipStr == null || ipStr.trim().isEmpty()) {
                return false;
            }
            try {
                InetAddress addr = InetAddress.getByName(ipStr.trim());
                return matches(addr);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
