# ADR 002: Autenticación, Usuarios y Seguridad (OAuth2 + JWT + RBAC)

## Estado
Aceptado

## Contexto
Necesitamos implementar el sistema de login de usuarios (Pacientes, Psiquiatras, Administradores) utilizando Google OAuth2 como proveedor de identidad, garantizando sesiones seguras mediante JWT inyectado en cookies HttpOnly y autorización basada en roles (RBAC) en el backend. Además, debemos cumplir con el requerimiento de calidad de al menos el 90% de cobertura en testing.

## Decisiones
1. **Entidades de BD**:
   - Se crearon la entidad JPA `Usuario` y el enum `Rol` (`ADMIN`, `PSIQUIATRA`, `PACIENTE`).
   - Se implementó `UsuarioRepository` extendiendo `JpaRepository`.

2. **Integración con Google OAuth2**:
   - Se implementó `GoogleAuthService` utilizando la librería de Google API Client para verificar de forma segura los ID Tokens.
   - El login registra nuevos usuarios de Google con el rol por defecto de `PACIENTE`. Los psiquiatras se precargan de forma administrativa y se asocian a su matrícula al validarse su login con Google.

3. **Manejo de Sesiones con JWT y Cookies**:
   - Se implementó `JwtService` para centralizar la generación, firma y parseo de tokens JWT utilizando la biblioteca JJWT.
   - La sesión del usuario se inyecta tras validación del ID Token en una cookie HttpOnly llamada `SESSION-TOKEN` con atributos `Secure`, `SameSite=Strict`, `Path=/` y tiempo de vida de 7 días.

4. **Spring Security y RBAC**:
   - Se habilitó `@EnableMethodSecurity` en `SecurityConfig` para soportar control de acceso mediante `@PreAuthorize("hasRole('...')")`.
   - Se implementó `JwtAuthenticationFilter` interceptando peticiones para validar la cookie de sesión, resolviendo al usuario mediante `CustomUserDetailsService` y cargando las authorities correspondientes (`ROLE_PACIENTE`, `ROLE_PSIQUIATRA`, `ROLE_ADMIN`) en el contexto de seguridad.

5. **Pruebas y Cobertura (Min. 90%)**:
   - Implementado `GoogleAuthServiceTest` mockeando el verificador de Google ID Token.
   - Implementado `CustomUserDetailsServiceTest` cubriendo obtención de usuarios exitosa y excepciones.
   - Implementado `JwtServiceTest` validando cifrado, expiración, extracción y cookies.
   - Implementado `SecurityRbacTest` con MockMvc y un controlador de prueba con anotaciones `@PreAuthorize` para simular denegación HTTP 403 y aceptación HTTP 200 de roles.
   - Implementado `AuthControllerTest` testeando las respuestas HTTP de login.
   - Modificado `00_contexto_arquitectura.md` y `00_workflow_desarrollo.md` para plasmar la cobertura mínima del 90% obligatoria por etapa.

## Consecuencias
- El backend autentica de forma segura contra Google y emite tokens firmados.
- Las APIs quedan protegidas mediante roles configurables a nivel de método.
- Los tests demuestran cumplimiento de cobertura > 90% en la lógica de autenticación y seguridad, compilando exitosamente con `BUILD SUCCESS`.
