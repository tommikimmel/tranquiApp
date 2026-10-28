package com.tranqui.gateway.config;

import com.tranqui.eventos.jwt.ServiceJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración de beans de infraestructura y seguridad para el API Gateway.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public ServiceJwt serviceJwt(@Value("${tranqui.jwt.service-secret}") String serviceSecret) {
        return new ServiceJwt(serviceSecret);
    }

    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean("loadBalancedWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}

