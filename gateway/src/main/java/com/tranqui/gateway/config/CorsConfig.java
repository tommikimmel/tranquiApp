package com.tranqui.gateway.config;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * La politica CORS del gateway se define una sola vez en globalcors (application.yml), que solo
 * cubre las rutas derivadas al monolito. Esta configuracion la aplica tambien a los controladores
 * propios del gateway (panel de administrador: /api/admin/salud y /api/admin/observabilidad).
 */
@Configuration
public class CorsConfig implements WebFluxConfigurer {

    private final GlobalCorsProperties globalCorsProperties;

    public CorsConfig(GlobalCorsProperties globalCorsProperties) {
        this.globalCorsProperties = globalCorsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        globalCorsProperties.getCorsConfigurations().forEach((patron, configuracion) -> {
            CorsRegistration registro = registry.addMapping(patron);
            if (configuracion.getAllowedOriginPatterns() != null) {
                registro.allowedOriginPatterns(configuracion.getAllowedOriginPatterns().toArray(String[]::new));
            }
            if (configuracion.getAllowedMethods() != null) {
                registro.allowedMethods(configuracion.getAllowedMethods().toArray(String[]::new));
            }
            if (configuracion.getAllowedHeaders() != null) {
                registro.allowedHeaders(configuracion.getAllowedHeaders().toArray(String[]::new));
            }
            if (configuracion.getAllowCredentials() != null) {
                registro.allowCredentials(configuracion.getAllowCredentials());
            }
        });
    }
}
