# Etapa 2: Autenticación, Usuarios y Seguridad (OAuth2 + JWT + RBAC)

Esta etapa implementa el sistema de registro e inicio de sesión de usuarios. El Paciente ingresará con Google OAuth2, mientras que el Administrador "fantasma" creará previamente a los Psiquiatras en el sistema.

---

## 1. Objetivos de la Etapa
1.  Definir la estructura de la tabla de `Usuario` y `Rol` en la base de datos PostgreSQL.
2.  Implementar la autenticación basada en Google OAuth2 en el Backend.
3.  Generar tokens JWT firmados en el backend tras validar las credenciales de Google.
4.  Configurar Spring Security con filtros de autorización basados en roles (RBAC).
5.  Establecer cookies seguras (`HttpOnly`, `Secure`, `SameSite=Strict`) para persistir la sesión.

---

## 2. Definición Técnica y Código de Soporte

### A. Estructura de Clases para Seguridad (Spring Boot)

#### Filtro JWT (`JwtAuthenticationFilter.java`)
Este filtro intercepta las peticiones, extrae la cookie de sesión o el header Authorization, valida el JWT y establece el contexto de seguridad de Spring.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = jwtService.extractTokenFromCookies(request);
        if (token == null) {
            token = jwtService.extractTokenFromHeader(request);
        }

        if (token != null && jwtService.validateToken(token)) {
            String email = jwtService.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

#### Configuración de Seguridad (`SecurityConfig.java`)
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Protegido mediante HttpOnly y SameSite en cookies
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/google", "/api/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### B. Ejemplo del Endpoint de Autenticación de Google (`AuthController.java`)
El frontend envía el token ID obtenido desde el botón de Google Sign-In. El backend lo valida usando la librería cliente de Google.

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleAuthService googleAuthService;
    @Autowired
    private JwtService jwtService;

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody GoogleLoginDto googleLoginDto, HttpServletResponse response) {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken(googleLoginDto.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
        }

        Usuario usuario = googleAuthService.getOrCreateUsuario(payload);
        String jwtToken = jwtService.generateToken(usuario);

        // Configurar cookie de sesión HttpOnly
        ResponseCookie cookie = ResponseCookie.from("SESSION-TOKEN", jwtToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 días
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new UserResponseDto(usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }
}
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba de Verificación de Token (Mocking)
En la suite de pruebas unitarias, mockear el servicio de Google Auth para evitar llamadas de red reales.
*   **Prueba Unitaria (`GoogleAuthServiceTest.java`):**
```java
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Test
    void shouldReturnPayloadWhenTokenIsValid() throws Exception {
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmail("paciente@gmail.com");
        mockPayload.set("name", "Juan Pérez");

        when(verifier.verify("valido-google-token")).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);

        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken("valido-google-token");
        assertNotNull(payload);
        assertEquals("paciente@gmail.com", payload.getEmail());
    }
}
```

### B. Pruebas de Acceso RBAC
*   Intentar acceder a `/api/admin/medicos` con una sesión de paciente. El servidor debe retornar HTTP status `403 Forbidden`.
*   Intentar acceder a `/api/medico/agenda` con una sesión válida de psiquiatra. Debe retornar HTTP status `200 OK`.

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Usuarios "Fantasmas":** El Administrador debe precargar los registros de los psiquiatras (email, matrícula y nombre) en la BD. Si un usuario intenta loguearse como psiquiatra mediante Google y su email no está registrado previamente, el sistema debe denegar el acceso o asignarle el rol predeterminado de paciente.
*   **Uso de HTTPS/WSS:** Es fundamental que las cookies tengan la bandera `Secure` activa. En desarrollo local, bajo HTTP simple, es posible que los navegadores bloqueen la cookie a menos que se configure un bypass en la configuración local (`localhost` suele ser tratado como seguro).
