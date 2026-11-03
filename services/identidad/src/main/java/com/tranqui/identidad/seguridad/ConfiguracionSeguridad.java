package com.tranqui.identidad.seguridad;

import com.tranqui.eventos.jwt.ClavesRsa;
import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;

@Configuration
public class ConfiguracionSeguridad {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public EmisorJwtUsuario emisorJwtUsuario(
            @Value("${jwt.private-key:}") String clavePrivadaTexto,
            @Value("${jwt.expiration:7d}") Duration vigencia) {
        if (clavePrivadaTexto == null || clavePrivadaTexto.trim().isEmpty()) {
            throw new IllegalStateException("Falta la clave privada RSA de identidad (variable JWT_PRIVATE_KEY o propiedad jwt.private-key). El servicio no puede arrancar.");
        }
        try {
            RSAPrivateKey clavePrivada = ClavesRsa.leerClavePrivada(clavePrivadaTexto);
            return new EmisorJwtUsuario(clavePrivada, vigencia);
        } catch (Exception e) {
            throw new IllegalStateException("La clave privada RSA de identidad (JWT_PRIVATE_KEY) es inválida: " + e.getMessage(), e);
        }
    }

    @Bean
    public VerificadorJwtUsuario verificadorJwtUsuario(
            @Value("${jwt.private-key:}") String clavePrivadaTexto,
            @Value("${jwt.public-key:}") String clavePublicaTexto,
            @Value("${jwt.secret:}") String secretoHs256,
            @Value("${jwt.hs256-aceptado:true}") boolean aceptarHs256) {
        RSAPublicKey clavePublica = null;

        if (clavePublicaTexto != null && !clavePublicaTexto.trim().isEmpty()) {
            try {
                clavePublica = ClavesRsa.leerClavePublica(clavePublicaTexto);
            } catch (Exception e) {
                throw new IllegalStateException("La clave pública RSA de identidad (JWT_PUBLIC_KEY) es inválida: " + e.getMessage(), e);
            }
        } else if (clavePrivadaTexto != null && !clavePrivadaTexto.trim().isEmpty()) {
            try {
                RSAPrivateKey priv = ClavesRsa.leerClavePrivada(clavePrivadaTexto);
                if (priv instanceof RSAPrivateCrtKey crt) {
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    clavePublica = (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                }
            } catch (Exception ignored) {
                // Si no se puede derivar, VerificadorJwtUsuario fallará si se intenta verificar sin clave
            }
        }

        return new VerificadorJwtUsuario(clavePublica, secretoHs256, aceptarHs256);
    }
}
