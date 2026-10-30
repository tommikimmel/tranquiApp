package com.tranqui.notificaciones.seguridad;

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
 * Sin token o con uno inválido: 401, como el monolito.
 */
@Component
public class FiltroAutenticacionUsuario extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "tranqui.usuario";

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
    public FiltroAutenticacionUsuario(UsuarioJwt usuarioJwt, com.tranqui.notificaciones.contacto.ContactoRepository copia,
                                      @org.springframework.beans.factory.annotation.Value("${tranqui.seguridad.cruzar-uid:true}") boolean cruzarUid) {
        this.usuarioJwt = usuarioJwt;
        this.idEnCopia = cruzarUid ? email -> copia.buscarPorEmail(email).map(c -> c.getId()) : null;
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
        return !request.getRequestURI().startsWith("/api/");
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
