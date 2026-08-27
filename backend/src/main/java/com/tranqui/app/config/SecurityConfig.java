package com.tranqui.app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Autowired
    private SiteAccessFilter siteAccessFilter;

    @Autowired
    private SubscriptionAccessFilter subscriptionAccessFilter;

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // Session is secured using HttpOnly and SameSite cookies
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/site-access/**", "/api/auth/google", "/api/auth/register", "/api/auth/login", "/api/auth/verify-email", "/api/auth/resend-code", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/health", "/api/payments/webhook", "/api/payments/verificar", "/ws-tranqui/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/medicos", "/api/medicos/*/turnos-disponibles", "/api/medicos/turnos-disponibles-conteo", "/api/medicos/mercadopago/callback", "/api/medicos/google-calendar/callback", "/api/medicos/google-calendar/webhook", "/api/turnos/reservar", "/api/turnos/*/abandonar-pago", "/api/turnos/*/confirmar-asistencia", "/api/turnos/*/no-asistira", "/api/recetas/financiadores", "/api/subscriptions/plans", "/api/subscriptions/invoices/*/pdf", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(siteAccessFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Necesita correr DESPUÉS de jwtAuthFilter (para tener el usuario autenticado
            // disponible en el SecurityContext), así que se ancla a ese filtro en vez de a
            // UsernamePasswordAuthenticationFilter como los otros dos.
            .addFilterAfter(subscriptionAccessFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
