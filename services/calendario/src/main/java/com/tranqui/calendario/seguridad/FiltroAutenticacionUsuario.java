package com.tranqui.calendario.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Exige un JWT de usuario válido en {@code /api/**} y deja el usuario como atributo del pedido.
 * Sin token o con uno inválido: 401, como el monolito. El callback del OAuth y el webhook de Google no
 * llevan sesión (los llama Google o el navegador volviendo de Google): se validan con su firma HMAC.
 */
@Component
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";

    private static final java.util.Set<String> SIN_SESION = java.util.Set.of(
            "/api/medicos/google-calendar/callback",
            "/api/medicos/google-calendar/webhook");

    private final UsuarioJwt usuarioJwt;

    private static final org.slf4j.Logger logCruce = org.slf4j.LoggerFactory.getLogger(FiltroAutenticacionUsuario.class);

    // El id que la copia de este servicio tiene para un mail (vacío si todavía no llegó el usuario).
    private final java.util.function.Function<String, java.util.Optional<Long>> idEnCopia;

    /** Sin cruce del uid con la copia (tests que arman el filtro a mano). */
    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt) {
        this.usuarioJwt = usuarioJwt;
        this.idEnCopia = null;
    }

    /**
     * Fase 10: nunca se confía solo en el token. Si trae {@code uid} y la copia tiene a ese mail con otro id, el pedido
     * se rechaza con 401 (un token de otra cuenta, o una copia desfasada que hay que mirar). Si la copia todavía no
     * tiene al usuario, se deja pasar: la copia se llena por eventos y puede llegar después.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt, com.tranqui.calendario.datos.Participantes copia,
                                      @org.springframework.beans.factory.annotation.Value("${tranqui.seguridad.cruzar-uid:true}") boolean cruzarUid) {
        this.usuarioJwt = usuarioJwt;
        this.idEnCopia = cruzarUid ? email -> copia.porEmail(email).map(p -> p.id()) : null;
    }

    private boolean uidNoCoincide(UsuarioJwt.Usuario usuario) {
        if (idEnCopia == null || usuario.uid() == null) {
            return false;
        }
        java.util.Optional<Long> enCopia = idEnCopia.apply(usuario.email());
        if (enCopia.isPresent() && !enCopia.get().equals(usuario.uid())) {
            logCruce.warn("Token rechazado: el uid {} no coincide con el id {} de la copia para ese mail", usuario.uid(), enCopia.get());
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || SIN_SESION.contains(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<UsuarioJwt.Usuario> usuario = usuarioJwt.extraerToken(request).flatMap(usuarioJwt::validar);
        if (usuario.isEmpty() || uidNoCoincide(usuario.get())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(ATRIBUTO_USUARIO, usuario.get());
        chain.doFilter(request, response);
    }
}
