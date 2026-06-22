---
name: spring-auth-security
description: "Directrices para la implementación de autenticación OAuth2 con Google, tokens JWT y autorización basada en roles (RBAC)."
---

# Skill: Spring Boot Auth & Security

Esta habilidad proporciona instrucciones para implementar esquemas de autenticación y autorización robustos y seguros en **Tranqui App**.

## 1. Responsabilidades del Agente
*   Configurar los filtros de seguridad de Spring Security para validar la sesión de los usuarios.
*   Implementar la integración nativa con Google API Client para decodificar y validar tokens de identidad (ID Tokens).
*   Configurar la generación de JWT con firmas firmes (claves de 256 bits mínimo) y vencimiento controlado (ej. 7 días).
*   Garantizar el envío de JWT mediante Cookies seguras (`HttpOnly`, `Secure`, `SameSite=Strict`).

## 2. Pautas de Código
*   Usar `@PreAuthorize` en las firmas de los controladores de la API REST para verificar los roles (`ADMIN`, `PSIQUIATRA`, `PACIENTE`).
*   Verificar que ningún endpoint exponga información confidencial sin autenticación, excepto la ruta de login (`/api/auth/google`) y salud (`/api/health`).
*   Mantener el cifrado y descifrado de los JWT centralizado en un servicio (`JwtService`) reusable.
